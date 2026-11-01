package com.smallbiz.reconciliation.aireview.ai;

public final class AiReviewException extends RuntimeException {
    private final String code;
    private final int status;
    public AiReviewException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
