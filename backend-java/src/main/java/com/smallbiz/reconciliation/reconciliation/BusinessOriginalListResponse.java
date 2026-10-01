package com.smallbiz.reconciliation.reconciliation;

import java.util.List;

public record BusinessOriginalListResponse(
		List<BusinessOriginalResponse> content,
		int page,
		int size,
		long totalElements
) {
}
