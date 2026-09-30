package com.smallbiz.reconciliation.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.smallbiz.reconciliation.common.FieldErrorResponse;

@Component
public class BusinessCsvParser {

	static final long MAX_AMOUNT = 1_000_000_000_000L;
	static final int MAX_DATA_ROWS = 10_000;
	static final int MAX_ERRORS = 100;
	static final int MAX_NOTE_LENGTH = 1_000;
	static final int MAX_SOURCE_LINE_ID_LENGTH = 100;
	static final int MAX_FILENAME_LENGTH = 255;

	private static final List<String> HEADERS = List.of(
			"vendor_code",
			"event_type",
			"usage_date",
			"expected_cash_date",
			"amount",
			"note",
			"source_line_id"
	);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	public List<ParsedBusinessRow> parse(byte[] content) {
		if (content == null || content.length == 0) {
			throw fileError("file", "빈 파일은 업로드할 수 없습니다.");
		}
		byte[] utf8 = stripUtf8Bom(content);
		try (Reader reader = new InputStreamReader(new ByteArrayInputStream(utf8), StandardCharsets.UTF_8);
				CSVParser parser = CSVFormat.RFC4180.builder()
						.setIgnoreEmptyLines(false)
						.setTrim(false)
						.setHeader()
						.setSkipHeaderRecord(true)
						.get()
						.parse(reader)) {
			return parseRecords(parser);
		}
		catch (UploadValidationException | DuplicateSourceLineIdException ex) {
			throw ex;
		}
		catch (IOException | RuntimeException ex) {
			throw fileError("file", "CSV 형식이 올바르지 않습니다.");
		}
	}

	private byte[] stripUtf8Bom(byte[] content) {
		if (content.length >= 3
				&& content[0] == (byte) 0xEF
				&& content[1] == (byte) 0xBB
				&& content[2] == (byte) 0xBF) {
			byte[] withoutBom = new byte[content.length - 3];
			System.arraycopy(content, 3, withoutBom, 0, withoutBom.length);
			return withoutBom;
		}
		return content;
	}

	private List<ParsedBusinessRow> parseRecords(CSVParser parser) {
		List<String> headerNames = parser.getHeaderNames();
		if (headerNames == null || headerNames.isEmpty()) {
			throw fileError("file", "CSV 헤더가 없습니다.");
		}
		ErrorBag errors = new ErrorBag();
		validateHeaders(headerNames, errors);
		if (errors.hasIssues()) {
			throw errors.toValidationException();
		}

		List<ParsedBusinessRow> rows = new ArrayList<>();
		Map<String, Integer> sourceLineFirstRow = new HashMap<>();
		ErrorBag duplicateIds = new ErrorBag();
		int dataIndex = 0;
		for (CSVRecord record : parser) {
			dataIndex++;
			if (dataIndex > MAX_DATA_ROWS) {
				errors.add(null, "file", "데이터 행은 최대 10,000개까지 허용합니다.");
				break;
			}
			int sourceRowNumber = dataIndex + 1;
			if (isCompletelyEmpty(record)) {
				errors.add(sourceRowNumber, "file", "빈 레코드는 허용되지 않습니다.");
				continue;
			}
			if (!record.isConsistent() || record.size() != HEADERS.size()) {
				errors.add(sourceRowNumber, "file", "컬럼 수가 헤더와 일치하지 않습니다.");
				continue;
			}
			ParsedBusinessRow row = parseRow(record, sourceRowNumber, errors);
			if (row != null) {
				Integer firstRow = sourceLineFirstRow.putIfAbsent(row.sourceLineId(), sourceRowNumber);
				if (firstRow != null) {
					duplicateIds.add(sourceRowNumber, "source_line_id", "파일 안에 동일한 원천 거래 ID가 있습니다.");
				}
				else {
					rows.add(row);
				}
			}
		}
		if (dataIndex == 0) {
			throw fileError("file", "헤더만 있고 데이터 행이 없습니다.");
		}
		if (errors.hasIssues()) {
			throw errors.toValidationException();
		}
		if (duplicateIds.hasIssues()) {
			throw duplicateIds.toDuplicateSourceLineIdException();
		}
		return rows;
	}

	private void validateHeaders(List<String> headerNames, ErrorBag errors) {
		List<String> trimmed = headerNames.stream().map(this::trimToEmpty).toList();
		if (trimmed.size() != HEADERS.size()) {
			errors.add(null, "file", "헤더 이름과 순서가 고정 양식과 다릅니다.");
			return;
		}
		long distinct = trimmed.stream().distinct().count();
		if (distinct != trimmed.size()) {
			errors.add(null, "file", "헤더에 중복된 컬럼이 있습니다.");
			return;
		}
		for (int i = 0; i < HEADERS.size(); i++) {
			if (!HEADERS.get(i).equals(trimmed.get(i))) {
				errors.add(null, "file", "헤더 이름과 순서가 고정 양식과 다릅니다.");
				return;
			}
		}
	}

	private ParsedBusinessRow parseRow(CSVRecord record, int sourceRowNumber, ErrorBag errors) {
		int before = errors.total();
		String vendorCode = requiredText(record, "vendor_code", sourceRowNumber, errors, 32);
		String eventTypeRaw = requiredText(record, "event_type", sourceRowNumber, errors, 32);
		String usageRaw = optionalText(record, "usage_date");
		String expectedRaw = optionalText(record, "expected_cash_date");
		String amountRaw = requiredText(record, "amount", sourceRowNumber, errors, 20);
		String note = optionalText(record, "note");
		String sourceLineId = requiredText(
				record,
				"source_line_id",
				sourceRowNumber,
				errors,
				MAX_SOURCE_LINE_ID_LENGTH
		);

		if (note != null && note.length() > MAX_NOTE_LENGTH) {
			errors.add(sourceRowNumber, "note", "비고는 1,000자 이하여야 합니다.");
		}

		BusinessEventType eventType = null;
		if (eventTypeRaw != null) {
			try {
				eventType = BusinessEventType.valueOf(eventTypeRaw);
			}
			catch (IllegalArgumentException ex) {
				errors.add(sourceRowNumber, "event_type", "허용되지 않는 거래 유형입니다.");
			}
		}

		LocalDate usageDate = parseDate(usageRaw, sourceRowNumber, "usage_date", errors);
		LocalDate expectedCashDate = parseDate(expectedRaw, sourceRowNumber, "expected_cash_date", errors);
		Long amount = parseAmount(amountRaw, sourceRowNumber, errors);

		if (eventType == BusinessEventType.USAGE) {
			if (usageRaw == null) {
				errors.add(sourceRowNumber, "usage_date", "USAGE는 이용일이 필요합니다.");
			}
			if (expectedRaw != null) {
				errors.add(sourceRowNumber, "expected_cash_date", "USAGE는 입출금 예정일을 비워야 합니다.");
			}
		}
		else if (eventType != null && expectedRaw == null) {
			errors.add(sourceRowNumber, "expected_cash_date", "입출금 예정일이 필요합니다.");
		}

		if (errors.total() > before || vendorCode == null || eventType == null
				|| amount == null || sourceLineId == null) {
			return null;
		}
		if (eventType == BusinessEventType.USAGE && usageDate == null) {
			return null;
		}
		if (eventType != BusinessEventType.USAGE && expectedCashDate == null) {
			return null;
		}
		return new ParsedBusinessRow(
				sourceRowNumber,
				vendorCode,
				eventType,
				usageDate,
				expectedCashDate,
				amount,
				note,
				sourceLineId
		);
	}

	private String requiredText(
			CSVRecord record,
			String field,
			int sourceRowNumber,
			ErrorBag errors,
			int maxLength
	) {
		String value = optionalText(record, field);
		if (value == null) {
			errors.add(sourceRowNumber, field, "필수 값입니다.");
			return null;
		}
		if (value.length() > maxLength) {
			errors.add(sourceRowNumber, field, "길이가 허용 범위를 초과했습니다.");
			return null;
		}
		return value;
	}

	private String optionalText(CSVRecord record, String field) {
		if (!record.isMapped(field)) {
			return null;
		}
		return trimToNull(record.get(field));
	}

	private LocalDate parseDate(String raw, int sourceRowNumber, String field, ErrorBag errors) {
		if (raw == null) {
			return null;
		}
		try {
			return LocalDate.parse(raw, DATE_FORMAT);
		}
		catch (DateTimeParseException ex) {
			errors.add(sourceRowNumber, field, "날짜는 YYYY-MM-DD 형식의 실재하는 날짜여야 합니다.");
			return null;
		}
	}

	private Long parseAmount(String raw, int sourceRowNumber, ErrorBag errors) {
		if (raw == null) {
			return null;
		}
		if (!raw.matches("^[0-9]+$") || raw.startsWith("0")) {
			errors.add(sourceRowNumber, "amount", "금액은 콤마·소수점·부호 없는 원화 정수여야 합니다.");
			return null;
		}
		try {
			long value = Long.parseLong(raw);
			if (value < 1 || value > MAX_AMOUNT) {
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

	private boolean isCompletelyEmpty(CSVRecord record) {
		for (int i = 0; i < record.size(); i++) {
			if (trimToNull(record.get(i)) != null) {
				return false;
			}
		}
		return true;
	}

	private UploadValidationException fileError(String field, String message) {
		return new UploadValidationException(List.of(new FieldErrorResponse(field, message)), false);
	}

	private String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static final class ErrorBag {
		private final List<FieldErrorResponse> items = new ArrayList<>();
		private int total;

		void add(Integer rowNumber, String field, String message) {
			total++;
			if (items.size() < MAX_ERRORS) {
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
			return new UploadValidationException(List.copyOf(items), total > MAX_ERRORS);
		}

		DuplicateSourceLineIdException toDuplicateSourceLineIdException() {
			return new DuplicateSourceLineIdException(List.copyOf(items), total > MAX_ERRORS);
		}
	}
}
