package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;

public record ParsedBankRow(
		int sourceRowNumber,
		LocalDate bookedDate,
		BankDirection direction,
		long amount,
		String counterpartyName,
		String description,
		String sourceLineId
) {
}
