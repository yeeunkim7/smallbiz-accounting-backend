package com.smallbiz.reconciliation.vendor;

import java.util.List;

public record VendorListResponse(
		List<VendorResponse> content,
		int page,
		int size,
		long totalElements
) {
}
