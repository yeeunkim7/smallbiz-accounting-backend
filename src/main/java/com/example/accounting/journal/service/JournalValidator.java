package com.example.accounting.journal.service;

import org.springframework.stereotype.Component;

@Component
public class JournalValidator {

    public void validateAmount(Long debitAmount, Long creditAmount) {
        if (debitAmount == null || creditAmount == null) {
            throw new IllegalArgumentException("차변/대변 금액은 null일 수 없습니다.");
        }

        if (debitAmount < 0 || creditAmount < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다.");
        }

        if (debitAmount > 0 && creditAmount > 0) {
            throw new IllegalArgumentException("차변과 대변 중 하나만 금액을 가질 수 있습니다.");
        }

        if (debitAmount == 0 && creditAmount == 0) {
            throw new IllegalArgumentException("차변 또는 대변 중 하나는 금액이 있어야 합니다.");
        }
    }
}
