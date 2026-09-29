package com.smallbiz.reconciliation.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VendorCreateRequest(
		@NotBlank(message = "거래처 코드는 필수입니다.")
		@Size(max = 32, message = "거래처 코드는 32자 이하여야 합니다.")
		@Pattern(regexp = "^[A-Z0-9_-]+$", message = "거래처 코드는 영문 대문자, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.")
		String vendorCode,

		@NotBlank(message = "거래처명은 필수입니다.")
		@Size(max = 100, message = "거래처명은 100자 이하여야 합니다.")
		String vendorName,

		@NotNull(message = "정산 방식은 필수입니다.")
		SettlementType settlementType
) {

	public VendorCreateRequest {
		vendorCode = trim(vendorCode);
		vendorName = trim(vendorName);
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}
}
