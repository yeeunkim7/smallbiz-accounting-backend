package com.smallbiz.reconciliation.vendor;

public class DuplicateVendorCodeException extends RuntimeException {

	private final String vendorCode;

	public DuplicateVendorCodeException(String vendorCode) {
		super("이미 사용 중인 거래처 코드입니다.");
		this.vendorCode = vendorCode;
	}

	public String getVendorCode() {
		return vendorCode;
	}
}
