package com.example.accounting.journal.repository;

import com.example.accounting.journal.domain.Journal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    List<Journal> findByJournalDateBetween(LocalDate start, LocalDate end);

    List<Journal> findByVendorId(Long vendorId);
}
