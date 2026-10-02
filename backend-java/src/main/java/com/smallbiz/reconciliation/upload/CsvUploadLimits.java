package com.smallbiz.reconciliation.upload;

final class CsvUploadLimits {

	static final long MAX_AMOUNT = 1_000_000_000_000L;
	static final int MAX_DATA_ROWS = 10_000;
	static final int MAX_ERRORS = 100;
	static final int MAX_NOTE_LENGTH = 1_000;
	static final int MAX_SOURCE_LINE_ID_LENGTH = 100;
	static final int MAX_FILENAME_LENGTH = 255;

	private CsvUploadLimits() {
	}
}
