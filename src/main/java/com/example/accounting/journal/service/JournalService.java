package com.example.accounting.journal.service;

import com.example.accounting.accountsubject.domain.AccountSubject;
import com.example.accounting.accountsubject.repository.AccountSubjectRepository;
import com.example.accounting.journal.domain.Journal;
import com.example.accounting.journal.repository.JournalRepository;
import com.example.accounting.vendor.domain.Vendor;
import com.example.accounting.vendor.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class JournalService {

    private final JournalRepository journalRepository;
    private final VendorRepository vendorRepository;
    private final AccountSubjectRepository accountSubjectRepository;
    private final JournalValidator journalValidator;

    public JournalService(
            JournalRepository journalRepository,
            VendorRepository vendorRepository,
            AccountSubjectRepository accountSubjectRepository,
            JournalValidator journalValidator
    ) {
        this.journalRepository = journalRepository;
        this.vendorRepository = vendorRepository;
        this.accountSubjectRepository = accountSubjectRepository;
        this.journalValidator = journalValidator;
    }

    /**
     * 분개 등록
     */
    public Journal registerJournal(
            LocalDate journalDate,
            String description,
            Long debitAmount,
            Long creditAmount,
            Long vendorId,
            Long accountSubjectId
    ) {
        // 1. 금액 검증
        journalValidator.validateAmount(debitAmount, creditAmount);

        // 2. 계정과목 조회 (필수)
        AccountSubject accountSubject = accountSubjectRepository
                .findById(accountSubjectId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 계정과목입니다.")
                );

        // 3. 거래처 조회 (선택)
        Vendor vendor = null;
        if (vendorId != null) {
            vendor = vendorRepository
                    .findById(vendorId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("존재하지 않는 거래처입니다.")
                    );
        }

        // 4. Journal 생성
        Journal journal = new Journal(
                journalDate,
                description,
                debitAmount,
                creditAmount,
                vendor,
                accountSubject
        );

        return journalRepository.save(journal);
    }

    /**
     * 분개 전체 조회
     */
    @Transactional(readOnly = true)
    public List<Journal> findAllJournals() {
        return journalRepository.findAll();
    }

    /**
     * 기간별 분개 조회
     */
    @Transactional(readOnly = true)
    public List<Journal> findJournalsByDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return journalRepository.findByJournalDateBetween(startDate, endDate);
    }

    /**
     * 거래처별 분개 조회
     */
    @Transactional(readOnly = true)
    public List<Journal> findJournalsByVendor(Long vendorId) {
        return journalRepository.findByVendorId(vendorId);
    }

    /**
     * 분개 단건 조회
     */
    @Transactional(readOnly = true)
    public Journal findJournal(Long journalId) {
        return journalRepository
                .findById(journalId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 분개입니다.")
                );
    }
}
