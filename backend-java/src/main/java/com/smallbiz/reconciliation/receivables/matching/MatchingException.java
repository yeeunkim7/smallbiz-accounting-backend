package com.smallbiz.reconciliation.receivables.matching;

final class MatchingException extends RuntimeException {
    final String code;
    final int status;
    private MatchingException(String code, int status, String message) { super(message); this.code = code; this.status = status; }
    static MatchingException invalid() { return new MatchingException("INVALID_INPUT", 400, "Invalid matching range."); }
    static MatchingException missing() { return new MatchingException("MATCHING_RUN_NOT_FOUND", 404, "The requested run was not found."); }
    static MatchingException conflict() { return new MatchingException("IDEMPOTENCY_CONFLICT", 409, "The idempotency key was used for another request."); }
    static MatchingException monthChanged() { return new MatchingException("MATCHING_MONTH_CHANGED", 409, "The usage month changed while waiting. Retry with the same idempotency key."); }
    static MatchingException limit() { return new MatchingException("MATCHING_SOURCE_LIMIT", 422, "The range exceeds the matching source limits."); }
    static MatchingException storage() { return new MatchingException("MATCHING_STORAGE_UNAVAILABLE", 503, "Matching storage is unavailable."); }
}
