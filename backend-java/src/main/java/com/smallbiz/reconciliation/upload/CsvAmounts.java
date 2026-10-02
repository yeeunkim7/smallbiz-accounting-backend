package com.smallbiz.reconciliation.upload;

final class CsvAmounts {

	private CsvAmounts() {
	}

	static Long parse(String raw, int sourceRowNumber, CsvFieldErrorBag errors) {
		if (raw == null) {
			return null;
		}
		if (!raw.matches("^[0-9]+$") || raw.startsWith("0")) {
			errors.add(sourceRowNumber, "amount", "금액은 콤마·소수점·부호 없는 원화 정수여야 합니다.");
			return null;
		}
		try {
			long value = Long.parseLong(raw);
			if (value < 1 || value > CsvUploadLimits.MAX_AMOUNT) {
				errors.add(sourceRowNumber, "amount", "금액은 1 이상 1조 이하여야 합니다.");
				return null;
			}
			return value;
		}
		catch (NumberFormatException ex) {
			errors.add(sourceRowNumber, "amount", "금액은 1 이상 1조 이하여야 합니다.");
			return null;
		}
	}
}
