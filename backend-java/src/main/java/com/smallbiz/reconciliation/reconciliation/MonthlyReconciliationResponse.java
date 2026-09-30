package com.smallbiz.reconciliation.reconciliation;

public record MonthlyReconciliationResponse(
		String yearMonth,
		long expectedInAmount,
		long actualInAmount,
		long inDifference,
		long expectedOutAmount,
		long actualOutAmount,
		long outDifference,
		TotalStatus inTotalStatus,
		TotalStatus outTotalStatus,
		int dataDayCount,
		int differenceDayCount
) {
}
