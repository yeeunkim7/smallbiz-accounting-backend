package com.smallbiz.reconciliation.common;

import java.util.List;

public record ErrorResponse(
		ErrorCode code,
		String message,
		List<FieldErrorResponse> fieldErrors
) {

	public static ErrorResponse of(ErrorCode code, String message) {
		return new ErrorResponse(code, message, List.of());
	}

	public static ErrorResponse of(ErrorCode code, String message, List<FieldErrorResponse> fieldErrors) {
		return new ErrorResponse(code, message, fieldErrors);
	}
}
