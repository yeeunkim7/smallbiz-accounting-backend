package com.smallbiz.reconciliation.upload;

import java.time.LocalDate;

public record ParsedBusinessRow(
		int sourceRowNumber,
		String vendorCode,
		BusinessEventType eventType,
		LocalDate usageDate,
		LocalDate expectedCashDate,
		long amount,
		String note,
		String sourceLineId
) {
}
