package com.smallbiz.reconciliation.vendor;

import java.time.Instant;

public record VendorResponse(
		Long id,
		String vendorCode,
		String vendorName,
		SettlementType settlementType,
		Instant createdAt,
		Instant updatedAt
) {

	public static VendorResponse from(Vendor vendor) {
		return new VendorResponse(
				vendor.getId(),
				vendor.getVendorCode(),
				vendor.getVendorName(),
				vendor.getSettlementType(),
				vendor.getCreatedAt(),
				vendor.getUpdatedAt()
		);
	}
}
