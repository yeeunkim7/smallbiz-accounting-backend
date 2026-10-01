package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.BankTransaction;

public record BankOriginalResponse(
		Long id,
		LocalDate bookedDate,
		BankDirection direction,
		long amount,
		String counterpartyName,
		String description,
		Long uploadId,
		String sourceLineId,
		int sourceRowNumber
) {

	static BankOriginalResponse from(BankTransaction transaction) {
		return new BankOriginalResponse(
				transaction.getId(),
				transaction.getBookedDate(),
				transaction.getDirection(),
				transaction.getAmount(),
				transaction.getCounterpartyName(),
				transaction.getDescription(),
				transaction.getUploadFile().getId(),
				transaction.getSourceLineId(),
				transaction.getSourceRowNumber()
		);
	}
}
