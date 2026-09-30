package com.smallbiz.reconciliation.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		ErrorCode code,
		String message,
		List<FieldErrorResponse> fieldErrors,
		Boolean truncated
) {

	public static ErrorResponse of(ErrorCode code, String message) {
		return new ErrorResponse(code, message, List.of(), null);
	}

	public static ErrorResponse of(ErrorCode code, String message, List<FieldErrorResponse> fieldErrors) {
		return new ErrorResponse(code, message, fieldErrors, null);
	}

	public static ErrorResponse of(
			ErrorCode code,
			String message,
			List<FieldErrorResponse> fieldErrors,
			boolean truncated
	) {
		return new ErrorResponse(code, message, fieldErrors, truncated ? Boolean.TRUE : null);
	}
}
