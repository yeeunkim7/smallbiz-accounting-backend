package com.smallbiz.reconciliation.account;

public class AccountException extends RuntimeException {
    private final String code;
    private final int status;
    public AccountException(String code, int status, String message) {
        super(message); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
    public static AccountException unauthenticated() {
        return new AccountException("AUTH_REQUIRED", 401, "Authentication is required.");
    }
    public static AccountException forbidden() {
        return new AccountException("ACCESS_DENIED", 403, "Access is denied.");
    }
}
