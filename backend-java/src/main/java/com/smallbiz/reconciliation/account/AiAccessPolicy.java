package com.smallbiz.reconciliation.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.smallbiz.reconciliation.aireview.ai.AiReviewException;

@Service
public class AiAccessPolicy {
    private final AuthService auth;
    private final TenantAccess tenants;
    private final boolean enabled;
    public AiAccessPolicy(AuthService auth, TenantAccess tenants, @Value("${app.ai.enabled:false}") boolean enabled) {
        this.auth = auth; this.tenants = tenants; this.enabled = enabled;
    }
    public void requireReviewAccess() {
        AccountPrincipal.require();
        auth.requireActiveSession();
        var memberships = auth.me().tenants();
        if (memberships.size() != 1) throw AccountException.forbidden();
        // Re-read membership/plan from the server, never request fields or token claims.
        var tenant = tenants.require(memberships.getFirst().id());
        if (!"PRO".equals(tenant.plan())) throw new AiReviewException("AI_PLAN_REQUIRED",403,"AI access requires PRO entitlement.");
        if (!enabled) throw new AiReviewException("AI_DISABLED",503,"AI is disabled.");
        // This final fail-closed gate has no configuration override. Legacy tools query global financial data.
        throw new AiReviewException("AI_TENANT_TOOLS_UNAVAILABLE",503,"Tenant-owned AI tools are not available.");
    }
}
