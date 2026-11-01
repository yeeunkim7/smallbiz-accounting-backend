package com.smallbiz.reconciliation.receivables.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-only policy hypotheses. Paid plans have no implicit entitlement. */
@Component
public record UsagePolicy(int freeLimit, int standardLimit, int proLimit, String version) {
    public UsagePolicy(
            @Value("${app.receivables.usage.free-limit:10}") int freeLimit,
            @Value("${app.receivables.usage.standard-limit:0}") int standardLimit,
            @Value("${app.receivables.usage.pro-limit:0}") int proLimit,
            @Value("${app.receivables.usage.policy-version:matching-usage-v1}") String version) {
        if (freeLimit < 0 || standardLimit < 0 || proLimit < 0 || version == null || !version.matches("[A-Za-z0-9._:-]{1,80}"))
            throw new IllegalArgumentException("Invalid monthly matching usage policy");
        this.freeLimit = freeLimit; this.standardLimit = standardLimit; this.proLimit = proLimit; this.version = version;
    }
    public int limit(String plan) {
        return switch (plan) { case "FREE" -> freeLimit; case "STANDARD" -> standardLimit; case "PRO" -> proLimit;
            default -> throw new IllegalStateException("Unsupported server plan"); };
    }
}
