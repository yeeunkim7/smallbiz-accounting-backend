package com.smallbiz.reconciliation.reconciliation;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

public class InvalidReconciliationQueryException extends RuntimeException {

	private final FieldErrorResponse fieldError;

	public InvalidReconciliationQueryException(String field, String message) {
		super("요청 값이 올바르지 않습니다.");
		this.fieldError = new FieldErrorResponse(field, message);
	}

	public FieldErrorResponse getFieldError() {
		return fieldError;
	}
}
