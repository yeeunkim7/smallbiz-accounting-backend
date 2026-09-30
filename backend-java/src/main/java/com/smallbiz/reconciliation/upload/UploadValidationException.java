package com.smallbiz.reconciliation.upload;

import java.util.List;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

public class UploadValidationException extends RuntimeException {

	private final List<FieldErrorResponse> fieldErrors;
	private final boolean truncated;

	public UploadValidationException(List<FieldErrorResponse> fieldErrors, boolean truncated) {
		super("요청 값이 올바르지 않습니다.");
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
