package com.smallbiz.reconciliation.account;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantAccess {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    public TenantAccess(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }
    public AuthService.TenantResponse require(UUID tenantId) {
        var account = auth.requireActiveSession();
        var result = jdbc.query("""
                SELECT t.id,t.name,t.plan FROM app_tenant t JOIN app_membership m ON m.tenant_id=t.id
                WHERE t.id=? AND m.account_id=?
                """, (rs,row) -> new AuthService.TenantResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("plan")),
                tenantId, account.accountId());
        // Same response for unknown IDs and another account's tenant.
        if (result.isEmpty()) throw AccountException.forbidden();
        return result.getFirst();
    }
}
