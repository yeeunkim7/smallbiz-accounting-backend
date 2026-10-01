package com.smallbiz.reconciliation.reconciliation;

import java.util.List;

public record BankOriginalListResponse(
		List<BankOriginalResponse> content,
		int page,
		int size,
		long totalElements
) {
}
