package com.smallbiz.reconciliation.upload;

public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException() {
		super("파일 크기가 허용 한도를 초과했습니다.");
	}
}
