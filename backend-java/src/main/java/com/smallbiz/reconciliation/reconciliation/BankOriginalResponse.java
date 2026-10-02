package com.smallbiz.reconciliation.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.BankTransaction;

public record BankOriginalResponse(
		Long id,
		LocalDate bookedDate,
		Instant bookedAt,
		BankDirection direction,
		long amount,
		Long balanceAfter,
		String counterpartyName,
		String description,
		String txnType,
		String branchName,
		Long uploadId,
		String sourceLineId,
		int sourceRowNumber
) {

	static BankOriginalResponse from(BankTransaction transaction) {
		return new BankOriginalResponse(
				transaction.getId(),
				transaction.getBookedDate(),
				transaction.getBookedAt(),
				transaction.getDirection(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				transaction.getCounterpartyName(),
				transaction.getDescription(),
				transaction.getTxnType(),
				transaction.getBranchName(),
				transaction.getUploadFile().getId(),
				transaction.getSourceLineId(),
				transaction.getSourceRowNumber()
		);
	}
}
