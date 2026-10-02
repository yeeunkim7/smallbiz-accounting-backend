package com.smallbiz.reconciliation.upload;

import java.util.ArrayList;
import java.util.List;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

final class CsvFieldErrorBag {

	private final List<FieldErrorResponse> items = new ArrayList<>();
	private int total;

	void add(Integer rowNumber, String field, String message) {
		total++;
		if (items.size() < CsvUploadLimits.MAX_ERRORS) {
			items.add(new FieldErrorResponse(rowNumber, field, message));
		}
	}

	boolean hasIssues() {
		return total > 0;
	}

	int total() {
		return total;
	}

	UploadValidationException toValidationException() {
		return new UploadValidationException(List.copyOf(items), total > CsvUploadLimits.MAX_ERRORS);
	}

	DuplicateSourceLineIdException toDuplicateSourceLineIdException() {
		return new DuplicateSourceLineIdException(List.copyOf(items), total > CsvUploadLimits.MAX_ERRORS);
	}
}
