package com.smallbiz.reconciliation.upload;

import java.time.Instant;
import java.time.LocalDate;

public record ParsedBankRow(
		int sourceRowNumber,
		LocalDate bookedDate,
		Instant bookedAt,
		BankDirection direction,
		long amount,
		Long balanceAfter,
		String counterpartyName,
		String description,
		String txnType,
		String branchName,
		String sourceLineId
) {
}
