package com.smallbiz.reconciliation.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorResponse(Integer rowNumber, String field, String message) {

	public FieldErrorResponse(String field, String message) {
		this(null, field, message);
	}
}
