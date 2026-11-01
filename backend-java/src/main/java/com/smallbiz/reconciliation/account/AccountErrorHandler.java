package com.smallbiz.reconciliation.account;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-1)
@RestControllerAdvice
public class AccountErrorHandler {
    @ExceptionHandler(AccountException.class)
    public ResponseEntity<Map<String,String>> error(AccountException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }
}
