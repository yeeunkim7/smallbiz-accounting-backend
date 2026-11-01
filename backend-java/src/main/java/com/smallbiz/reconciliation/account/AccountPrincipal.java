package com.smallbiz.reconciliation.account;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

// No password, raw bearer token or plan supplied by the client belongs in the principal.
public record AccountPrincipal(UUID accountId, String login, String sessionHash) {
    public static AccountPrincipal require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal))
            throw AccountException.unauthenticated();
        return principal;
    }
}
