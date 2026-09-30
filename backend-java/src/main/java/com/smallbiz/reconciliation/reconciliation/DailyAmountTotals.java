package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

record DailyAmountTotals(
		LocalDate date,
		long expectedInAmount,
		long actualInAmount,
		long expectedOutAmount,
		long actualOutAmount,
		boolean hasBusiness,
		boolean hasBank
) {
}
