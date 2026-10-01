package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

	List<BankTransaction> findBySourceLineIdIn(Collection<String> sourceLineIds);

	@EntityGraph(attributePaths = {"uploadFile"})
	Page<BankTransaction> findByBookedDate(LocalDate bookedDate, Pageable pageable);
}
