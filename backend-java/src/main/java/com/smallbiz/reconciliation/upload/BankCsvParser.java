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
public class BankCsvParser {

	static final int MAX_COUNTERPARTY_LENGTH = 200;

	private static final List<String> HEADERS = List.of(
			"booked_date",
			"direction",
			"amount",
			"counterparty_name",
			"description",
			"source_line_id"
	);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	public List<ParsedBankRow> parse(byte[] content) {
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

	private List<ParsedBankRow> parseRecords(CSVParser parser) {
		List<String> headerNames = parser.getHeaderNames();
		if (headerNames == null || headerNames.isEmpty()) {
			throw fileError("file", "CSV 헤더가 없습니다.");
		}
		ErrorBag errors = new ErrorBag();
		validateHeaders(headerNames, errors);
		if (errors.hasIssues()) {
			throw errors.toValidationException();
		}

		List<ParsedBankRow> rows = new ArrayList<>();
		Map<String, Integer> sourceLineFirstRow = new HashMap<>();
		ErrorBag duplicateIds = new ErrorBag();
		int dataIndex = 0;
		for (CSVRecord record : parser) {
			dataIndex++;
			if (dataIndex > BusinessCsvParser.MAX_DATA_ROWS) {
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
			ParsedBankRow row = parseRow(record, sourceRowNumber, errors);
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

	private ParsedBankRow parseRow(CSVRecord record, int sourceRowNumber, ErrorBag errors) {
		int before = errors.total();
		String bookedRaw = requiredText(record, "booked_date", sourceRowNumber, errors, 32);
		String directionRaw = requiredText(record, "direction", sourceRowNumber, errors, 8);
		String amountRaw = requiredText(record, "amount", sourceRowNumber, errors, 20);
		String counterparty = optionalText(record, "counterparty_name");
		String description = optionalText(record, "description");
		String sourceLineId = requiredText(
				record,
				"source_line_id",
				sourceRowNumber,
				errors,
				BusinessCsvParser.MAX_SOURCE_LINE_ID_LENGTH
		);

		if (counterparty != null && counterparty.length() > MAX_COUNTERPARTY_LENGTH) {
			errors.add(sourceRowNumber, "counterparty_name", "입금자명은 200자 이하여야 합니다.");
		}
		if (description != null && description.length() > BusinessCsvParser.MAX_NOTE_LENGTH) {
			errors.add(sourceRowNumber, "description", "적요는 1,000자 이하여야 합니다.");
		}

		BankDirection direction = null;
		if (directionRaw != null) {
			try {
				direction = BankDirection.valueOf(directionRaw);
			}
			catch (IllegalArgumentException ex) {
				errors.add(sourceRowNumber, "direction", "direction은 IN 또는 OUT 이어야 합니다.");
			}
		}

		LocalDate bookedDate = parseDate(bookedRaw, sourceRowNumber, errors);
		Long amount = parseAmount(amountRaw, sourceRowNumber, errors);

		if (errors.total() > before || bookedDate == null || direction == null
				|| amount == null || sourceLineId == null) {
			return null;
		}
		return new ParsedBankRow(
				sourceRowNumber,
				bookedDate,
				direction,
				amount,
				counterparty,
				description,
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

	private LocalDate parseDate(String raw, int sourceRowNumber, ErrorBag errors) {
		if (raw == null) {
			return null;
		}
		try {
			return LocalDate.parse(raw, DATE_FORMAT);
		}
		catch (DateTimeParseException ex) {
			errors.add(sourceRowNumber, "booked_date", "날짜는 YYYY-MM-DD 형식의 실재하는 날짜여야 합니다.");
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
			if (value < 1 || value > BusinessCsvParser.MAX_AMOUNT) {
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
			if (items.size() < BusinessCsvParser.MAX_ERRORS) {
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
			return new UploadValidationException(List.copyOf(items), total > BusinessCsvParser.MAX_ERRORS);
		}

		DuplicateSourceLineIdException toDuplicateSourceLineIdException() {
			return new DuplicateSourceLineIdException(List.copyOf(items), total > BusinessCsvParser.MAX_ERRORS);
		}
	}
}
