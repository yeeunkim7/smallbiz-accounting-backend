package com.example.accounting.journal.domain;

import com.example.accounting.accountsubject.domain.AccountSubject;
import com.example.accounting.common.domain.BaseEntity;
import com.example.accounting.vendor.domain.Vendor;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Journal extends BaseEntity {

    @Column(nullable = false)
    private LocalDate journalDate; // 분개일자

    @Column(nullable = false, length = 200)
    private String description; // 적요

    @Column(nullable = false)
    private Long debitAmount; // 차변 금액

    @Column(nullable = false)
    private Long creditAmount; // 대변 금액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor; // 거래처 (선택)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_subject_id", nullable = false)
    private AccountSubject accountSubject; // 계정과목

    protected Journal() {
    }

    public Journal(
            LocalDate journalDate,
            String description,
            Long debitAmount,
            Long creditAmount,
            Vendor vendor,
            AccountSubject accountSubject
    ) {
        this.journalDate = journalDate;
        this.description = description;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.vendor = vendor;
        this.accountSubject = accountSubject;
    }

    public LocalDate getJournalDate() {
        return journalDate;
    }

    public String getDescription() {
        return description;
    }

    public Long getDebitAmount() {
        return debitAmount;
    }

    public Long getCreditAmount() {
        return creditAmount;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public AccountSubject getAccountSubject() {
        return accountSubject;
    }
}
