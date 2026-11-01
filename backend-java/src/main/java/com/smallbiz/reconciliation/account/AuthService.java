package com.smallbiz.reconciliation.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final long ttl;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock,
            @Value("${app.auth.session-ttl-seconds:86400}") long ttl) {
        if (ttl < 60 || ttl > 2_592_000) throw new IllegalArgumentException("Invalid session TTL");
        this.jdbc = jdbc; this.encoder = encoder; this.clock = clock; this.ttl = ttl;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public SessionResponse signup(String login, String password, String tenantName) {
        String normalized = normalize(login);
        validatePassword(password);
        if (tenantName == null || tenantName.isBlank() || tenantName.length() > 100) throw invalid();
        UUID account = UUID.randomUUID(), tenant = UUID.randomUUID();
        long now = clock.instant().getEpochSecond();
        try {
            jdbc.update("INSERT INTO app_account VALUES (?, ?, ?, ?)", account, normalized, encoder.encode(password), now);
        } catch (DuplicateKeyException ex) {
            throw new AccountException("LOGIN_UNAVAILABLE", 409, "Login is unavailable.");
        }
        jdbc.update("INSERT INTO app_tenant (id,name,created_at_epoch) VALUES (?, ?, ?)", tenant, tenantName.trim(), now);
        jdbc.update("INSERT INTO app_membership (account_id,tenant_id) VALUES (?, ?)", account, tenant);
        return createSession(account, normalized);
    }

    @Transactional
    public SessionResponse login(String login, String password) {
        String normalized = normalize(login);
        validatePassword(password);
        var accounts = jdbc.query("SELECT id, password_hash FROM app_account WHERE login = ?",
                (rs, row) -> new StoredAccount(rs.getObject("id", UUID.class), rs.getString("password_hash")), normalized);
        String hash = accounts.isEmpty() ? dummyHash : accounts.getFirst().passwordHash();
        boolean matches = encoder.matches(password, hash);
        if (!matches || accounts.isEmpty()) throw new AccountException("INVALID_CREDENTIALS", 401, "Invalid login or password.");
        return createSession(accounts.getFirst().id(), normalized);
    }

    public AccountPrincipal authenticate(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw AccountException.unauthenticated();
        String hash = hashToken(rawToken);
        var principals = jdbc.query("""
                SELECT a.id, a.login FROM app_session s JOIN app_account a ON a.id = s.account_id
                WHERE s.token_hash = ? AND s.revoked_at_epoch IS NULL AND s.expires_at_epoch > ?
                """, (rs, row) -> new AccountPrincipal(rs.getObject("id", UUID.class), rs.getString("login"), hash),
                hash, clock.instant().getEpochSecond());
        if (principals.isEmpty()) throw AccountException.unauthenticated();
        return principals.getFirst();
    }

    public AccountPrincipal requireActiveSession() {
        var principal = AccountPrincipal.require();
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM app_session WHERE token_hash=? AND account_id=?
                AND revoked_at_epoch IS NULL AND expires_at_epoch>?
                """, Integer.class, principal.sessionHash(), principal.accountId(), clock.instant().getEpochSecond());
        if (active == null || active != 1) throw AccountException.unauthenticated();
        return principal;
    }

    public MeResponse me() {
        var principal = requireActiveSession();
        return new MeResponse(principal.accountId(), principal.login(), jdbc.query("""
                SELECT t.id,t.name,t.plan FROM app_tenant t JOIN app_membership m ON m.tenant_id=t.id
                WHERE m.account_id=? ORDER BY t.created_at_epoch,t.id
                """, (rs,row) -> new TenantResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("plan")), principal.accountId()));
    }

    @Transactional
    public void logout() {
        var principal = AccountPrincipal.require();
        jdbc.update("UPDATE app_session SET revoked_at_epoch=? WHERE token_hash=? AND account_id=? AND revoked_at_epoch IS NULL",
                clock.instant().getEpochSecond(), principal.sessionHash(), principal.accountId());
    }

    private SessionResponse createSession(UUID account, String login) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = clock.instant().getEpochSecond(), expiry = Math.addExact(now, ttl);
        jdbc.update("INSERT INTO app_session (token_hash,account_id,created_at_epoch,expires_at_epoch) VALUES (?,?,?,?)",
                hashToken(token), account, now, expiry);
        return new SessionResponse(token, "Bearer", expiry, account, login);
    }

    private static String normalize(String login) {
        if (login == null) throw invalid();
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9@._+\\-]{2,119}")) throw invalid();
        return normalized;
    }
    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw invalid();
    }
    private static AccountException invalid() { return new AccountException("INVALID_INPUT", 400, "Invalid account input."); }
    static String hashToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private record StoredAccount(UUID id, String passwordHash) {}
    public record SessionResponse(String accessToken, String tokenType, long expiresAtEpoch, UUID accountId, String login) {}
    public record MeResponse(UUID accountId, String login, List<TenantResponse> tenants) {}
    public record TenantResponse(UUID id, String name, String plan) {}
}
