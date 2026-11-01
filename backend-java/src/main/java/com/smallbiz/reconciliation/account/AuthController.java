package com.smallbiz.reconciliation.account;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final TenantAccess tenants;
    public AuthController(AuthService auth, TenantAccess tenants) { this.auth = auth; this.tenants = tenants; }
    // Explicit allowlists reject privilege fields independently of Jackson's unknown-field default.
    @PostMapping("/auth/signup")
    public ResponseEntity<AuthService.SessionResponse> signup(@RequestBody Map<String,Object> body) {
        if (!body.keySet().equals(java.util.Set.of("login","password","tenantName"))) throw invalid();
        return ResponseEntity.status(201).body(auth.signup(string(body,"login"), string(body,"password"), string(body,"tenantName")));
    }
    @PostMapping("/auth/login")
    public AuthService.SessionResponse login(@RequestBody Map<String,Object> body) {
        if (!body.keySet().equals(java.util.Set.of("login","password"))) throw invalid();
        return auth.login(string(body,"login"), string(body,"password"));
    }
    @GetMapping("/auth/me") public AuthService.MeResponse me() { return auth.me(); }
    @PostMapping("/auth/logout") public ResponseEntity<Void> logout() { auth.logout(); return ResponseEntity.noContent().build(); }
    @GetMapping("/tenants/{id}") public AuthService.TenantResponse tenant(@PathVariable UUID id) { return tenants.require(id); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Map<String,String>> databaseError() {
        // Do not log SQL exceptions: their detail can contain normalized logins, hashes or token hashes.
        return ResponseEntity.status(503).body(Map.of("code","ACCOUNT_STORAGE_UNAVAILABLE","message","Account storage is unavailable."));
    }
    private static String string(Map<String,Object> body, String key) {
        if (!(body.get(key) instanceof String value)) throw invalid();
        return value;
    }
    private static AccountException invalid() { return new AccountException("INVALID_INPUT",400,"Invalid account input."); }
}
