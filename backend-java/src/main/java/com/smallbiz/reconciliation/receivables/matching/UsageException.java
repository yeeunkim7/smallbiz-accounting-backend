package com.smallbiz.reconciliation.receivables.matching;

final class UsageException extends RuntimeException {
    final UsageService.Summary usage;
    final long retryAfter;
    UsageException(UsageService.Summary usage, long retryAfter) {
        super("The monthly successful matching-run limit has been reached.");
        this.usage = usage; this.retryAfter = retryAfter;
    }
}
