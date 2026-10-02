package com.smallbiz.reconciliation.upload;

final class WooriBankAmounts {

	private WooriBankAmounts() {
	}

	static Long parseInOut(String raw, int sourceRowNumber, String field, CsvFieldErrorBag errors) {
		if (raw == null) {
			return 0L;
		}
		Long value = parseBody(raw, sourceRowNumber, field, errors);
		if (value == null) {
			return null;
		}
		if (value > CsvUploadLimits.MAX_AMOUNT) {
			errors.add(sourceRowNumber, field, "금액은 1 이상 1조 이하여야 합니다.");
			return null;
		}
		return value;
	}

	static Long parseBalance(String raw, int sourceRowNumber, CsvFieldErrorBag errors) {
		if (raw == null) {
			errors.add(sourceRowNumber, "잔액", "필수 값입니다.");
			return null;
		}
		Long value = parseBody(raw, sourceRowNumber, "잔액", errors);
		if (value == null) {
			return null;
		}
		if (value < 0) {
			errors.add(sourceRowNumber, "잔액", "잔액은 0 이상이어야 합니다.");
			return null;
		}
		return value;
	}

	private static Long parseBody(String raw, int sourceRowNumber, String field, CsvFieldErrorBag errors) {
		if (raw.endsWith("원")) {
			raw = raw.substring(0, raw.length() - 1);
		}
		if ("0".equals(raw)) {
			return 0L;
		}
		if (raw.matches("^[1-9][0-9]*$") || raw.matches("^[1-9][0-9]{0,2}(,[0-9]{3})+$")) {
			String digits = raw.replace(",", "");
			try {
				return Long.parseLong(digits);
			}
			catch (NumberFormatException ex) {
				errors.add(sourceRowNumber, field, rangeMessage(field));
				return null;
			}
		}
		errors.add(sourceRowNumber, field, formatMessage(field));
		return null;
	}

	private static String formatMessage(String field) {
		if ("잔액".equals(field)) {
			return "잔액은 쉼표 없는 정수 또는 올바른 천 단위 쉼표와 선택적 원 표시여야 합니다.";
		}
		return "금액은 쉼표 없는 정수 또는 올바른 천 단위 쉼표와 선택적 원 표시여야 합니다.";
	}

	private static String rangeMessage(String field) {
		if ("잔액".equals(field)) {
			return "잔액이 허용 범위를 벗어났습니다.";
		}
		return "금액은 1 이상 1조 이하여야 합니다.";
	}
}
