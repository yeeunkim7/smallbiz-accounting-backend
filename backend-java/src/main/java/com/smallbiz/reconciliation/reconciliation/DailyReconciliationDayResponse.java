package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;

public record DailyReconciliationDayResponse(
		LocalDate date,
		long expectedInAmount,
		long actualInAmount,
		long inDifference,
		long expectedOutAmount,
		long actualOutAmount,
		long outDifference,
		TotalStatus inTotalStatus,
		TotalStatus outTotalStatus,
		SourcePresence sourcePresence,
		ReviewStatus reviewStatus
) {
}
