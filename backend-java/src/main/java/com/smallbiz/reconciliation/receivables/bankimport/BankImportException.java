package com.smallbiz.reconciliation.receivables.bankimport;

public class BankImportException extends RuntimeException {
    private final String code;
    private final int status;
    private final Integer recordNumber;
    private final String field;
    private BankImportException(String code, int status, String message, Integer recordNumber, String field) {
        super(message); this.code=code; this.status=status; this.recordNumber=recordNumber; this.field=field;
    }
    public String code() { return code; }
    public int status() { return status; }
    public Integer recordNumber() { return recordNumber; }
    public String field() { return field; }
    static BankImportException invalid(Integer record, String field) {
        return new BankImportException("BANK_IMPORT_INVALID",400,"Invalid bank import input.",record,field);
    }
    static BankImportException limit(String field) {
        return new BankImportException("BANK_IMPORT_LIMIT",413,"Bank import exceeds the configured limit.",null,field);
    }
    static BankImportException conflict(String code) {
        return new BankImportException(code,409,"The supplied identity was previously used for different content.",null,null);
    }
    static BankImportException missing() {
        return new BankImportException("BANK_IMPORT_NOT_FOUND",404,"The requested import was not found.",null,null);
    }
}
