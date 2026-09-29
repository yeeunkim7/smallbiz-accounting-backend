package com.smallbiz.reconciliation.vendor;

public class VendorNotFoundException extends RuntimeException {

	private final Long vendorId;

	public VendorNotFoundException(Long vendorId) {
		super("거래처를 찾을 수 없습니다.");
		this.vendorId = vendorId;
	}

	public Long getVendorId() {
		return vendorId;
	}
}
