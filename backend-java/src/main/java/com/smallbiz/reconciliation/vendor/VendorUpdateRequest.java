package com.smallbiz.reconciliation.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VendorUpdateRequest(
		@NotBlank(message = "거래처명은 필수입니다.")
		@Size(max = 100, message = "거래처명은 100자 이하여야 합니다.")
		String vendorName,

		@NotNull(message = "정산 방식은 필수입니다.")
		SettlementType settlementType
) {

	public VendorUpdateRequest {
		vendorName = vendorName == null ? null : vendorName.trim();
	}
}
