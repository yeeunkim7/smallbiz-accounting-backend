package com.smallbiz.reconciliation.upload;

import java.util.List;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

public class DuplicateSourceLineIdException extends RuntimeException {

	private final List<FieldErrorResponse> fieldErrors;
	private final boolean truncated;

	public DuplicateSourceLineIdException(List<FieldErrorResponse> fieldErrors, boolean truncated) {
		super("이미 저장된 원천 거래 ID가 포함되어 있습니다.");
		this.fieldErrors = List.copyOf(fieldErrors);
		this.truncated = truncated;
	}

	public List<FieldErrorResponse> getFieldErrors() {
		return fieldErrors;
	}

	public boolean isTruncated() {
		return truncated;
	}
}
