package com.smallbiz.reconciliation.receivables.allocation;

final class AllocationException extends RuntimeException {
    final String code;
    final int status;
    private AllocationException(String code, int status, String message) {
        super(message); this.code = code; this.status = status;
    }
    static AllocationException invalid() { return new AllocationException("INVALID_INPUT", 400, "Invalid allocation input."); }
    static AllocationException missing() { return new AllocationException("RECEIVABLE_NOT_FOUND", 404, "The requested record was not found."); }
    static AllocationException exceeded() { return new AllocationException("ALLOCATION_EXCEEDS_AVAILABLE", 409, "The allocation exceeds the available amount."); }
    static AllocationException conflict() { return new AllocationException("IDEMPOTENCY_CONFLICT", 409, "The idempotency key was used for another request."); }
    static AllocationException cancelled() { return new AllocationException("ALLOCATION_ALREADY_CANCELLED", 409, "The allocation has already been cancelled."); }
    static AllocationException invariant() { return new AllocationException("ALLOCATION_STORAGE_UNAVAILABLE", 503, "Allocation storage is unavailable."); }
}
