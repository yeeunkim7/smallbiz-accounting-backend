package com.smallbiz.reconciliation.upload;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

	List<BankTransaction> findBySourceLineIdIn(Collection<String> sourceLineIds);
}
