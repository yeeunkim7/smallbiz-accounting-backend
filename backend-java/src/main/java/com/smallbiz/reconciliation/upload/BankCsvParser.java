package com.smallbiz.reconciliation.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
	static final int MAX_TXN_TYPE_LENGTH = 100;
	static final int MAX_BRANCH_NAME_LENGTH = 200;
	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final List<String> GENERIC_HEADERS = List.of(
			"booked_date",
			"direction",
			"amount",
			"counterparty_name",
			"description",
			"source_line_id"
	);

	private static final List<String> WOORI_HEADERS = List.of(
			"거래일시",
			"거래구분",
			"기재내용",
			"출금금액",
			"입금금액",
			"잔액",
			"취급점"
	);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);

	private static final DateTimeFormatter WOORI_DATE_TIME = DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss")
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
		List<String> trimmed = headerNames.stream().map(this::trimToEmpty).toList();
		if (trimmed.stream().distinct().count() != trimmed.size()) {
			throw fileError("file", "헤더에 중복된 컬럼이 있습니다.");
		}
		if (headersMatch(trimmed, GENERIC_HEADERS)) {
			return parseGenericRecords(parser);
		}
		if (headersMatch(trimmed, WOORI_HEADERS)) {
			return parseWooriRecords(parser);
		}
		throw fileError("file", "헤더 이름과 순서가 고정 양식과 다릅니다.");
	}

	private boolean headersMatch(List<String> trimmed, List<String> expected) {
		if (trimmed.size() != expected.size()) {
			return false;
		}
		for (int i = 0; i < expected.size(); i++) {
			if (!expected.get(i).equals(trimmed.get(i))) {
				return false;
			}
		}
		return true;
	}

	private List<ParsedBankRow> parseGenericRecords(CSVParser parser) {
		CsvFieldErrorBag errors = new CsvFieldErrorBag();
		List<ParsedBankRow> rows = new ArrayList<>();
		Map<String, Integer> sourceLineFirstRow = new HashMap<>();
		CsvFieldErrorBag duplicateIds = new CsvFieldErrorBag();
		int dataIndex = 0;
		for (CSVRecord record : parser) {
			dataIndex++;
			if (dataIndex > CsvUploadLimits.MAX_DATA_ROWS) {
				errors.add(null, "file", "데이터 행은 최대 10,000개까지 허용합니다.");
				break;
			}
			int sourceRowNumber = dataIndex + 1;
			if (isCompletelyEmpty(record)) {
				errors.add(sourceRowNumber, "file", "빈 레코드는 허용되지 않습니다.");
				continue;
			}
			if (!record.isConsistent() || record.size() != GENERIC_HEADERS.size()) {
				errors.add(sourceRowNumber, "file", "컬럼 수가 헤더와 일치하지 않습니다.");
				continue;
			}
			ParsedBankRow row = parseGenericRow(record, sourceRowNumber, errors);
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
		return finishRows(dataIndex, errors, duplicateIds, rows);
	}

	private List<ParsedBankRow> parseWooriRecords(CSVParser parser) {
		CsvFieldErrorBag errors = new CsvFieldErrorBag();
		List<ParsedBankRow> rows = new ArrayList<>();
		Map<String, Integer> sourceLineFirstRow = new HashMap<>();
		CsvFieldErrorBag duplicateIds = new CsvFieldErrorBag();
		int dataIndex = 0;
		for (CSVRecord record : parser) {
			dataIndex++;
			if (dataIndex > CsvUploadLimits.MAX_DATA_ROWS) {
				errors.add(null, "file", "데이터 행은 최대 10,000개까지 허용합니다.");
				break;
			}
			int sourceRowNumber = dataIndex + 1;
			if (isCompletelyEmpty(record)) {
				errors.add(sourceRowNumber, "file", "빈 레코드는 허용되지 않습니다.");
				continue;
			}
			if (!record.isConsistent() || record.size() != WOORI_HEADERS.size()) {
				errors.add(sourceRowNumber, "file", "컬럼 수가 헤더와 일치하지 않습니다.");
				continue;
			}
			ParsedBankRow row = parseWooriRow(record, sourceRowNumber, errors);
			if (row != null) {
				Integer firstRow = sourceLineFirstRow.putIfAbsent(row.sourceLineId(), sourceRowNumber);
				if (firstRow != null) {
					duplicateIds.add(sourceRowNumber, "source_line_id", "파일 안에 동일한 거래 지문이 있습니다.");
				}
				else {
					rows.add(row);
				}
			}
		}
		return finishRows(dataIndex, errors, duplicateIds, rows);
	}

	private List<ParsedBankRow> finishRows(
			int dataIndex,
			CsvFieldErrorBag errors,
			CsvFieldErrorBag duplicateIds,
			List<ParsedBankRow> rows
	) {
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

	private ParsedBankRow parseGenericRow(CSVRecord record, int sourceRowNumber, CsvFieldErrorBag errors) {
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
				CsvUploadLimits.MAX_SOURCE_LINE_ID_LENGTH
		);

		if (counterparty != null && counterparty.length() > MAX_COUNTERPARTY_LENGTH) {
			errors.add(sourceRowNumber, "counterparty_name", "입금자명은 200자 이하여야 합니다.");
		}
		if (description != null && description.length() > CsvUploadLimits.MAX_NOTE_LENGTH) {
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
		Long amount = CsvAmounts.parse(amountRaw, sourceRowNumber, errors);
		if (sourceLineId != null && BankTransactionFingerprint.isReserved(sourceLineId)) {
			errors.add(sourceRowNumber, "source_line_id", "이 형식의 식별자는 우리은행 거래 지문에 예약되어 있습니다.");
			sourceLineId = null;
		}

		if (errors.total() > before || bookedDate == null || direction == null
				|| amount == null || sourceLineId == null) {
			return null;
		}
		return new ParsedBankRow(
				sourceRowNumber,
				bookedDate,
				null,
				direction,
				amount,
				null,
				counterparty,
				description,
				null,
				null,
				sourceLineId
		);
	}

	private ParsedBankRow parseWooriRow(CSVRecord record, int sourceRowNumber, CsvFieldErrorBag errors) {
		int before = errors.total();
		String bookedRaw = requiredText(record, "거래일시", sourceRowNumber, errors, 32);
		String txnType = optionalBounded(record, "거래구분", sourceRowNumber, errors, MAX_TXN_TYPE_LENGTH);
		String description = optionalBounded(record, "기재내용", sourceRowNumber, errors, CsvUploadLimits.MAX_NOTE_LENGTH);
		String outRaw = optionalText(record, "출금금액");
		String inRaw = optionalText(record, "입금금액");
		String balanceRaw = optionalText(record, "잔액");
		String branch = optionalBounded(record, "취급점", sourceRowNumber, errors, MAX_BRANCH_NAME_LENGTH);

		LocalDateTime bookedAtSeoul = parseWooriDateTime(bookedRaw, sourceRowNumber, errors);
		Long outAmount = WooriBankAmounts.parseInOut(outRaw, sourceRowNumber, "출금금액", errors);
		Long inAmount = WooriBankAmounts.parseInOut(inRaw, sourceRowNumber, "입금금액", errors);
		Long balance = WooriBankAmounts.parseBalance(balanceRaw, sourceRowNumber, errors);

		BankDirection direction = null;
		long amount = 0;
		if (inAmount != null && outAmount != null) {
			boolean inPositive = inAmount > 0;
			boolean outPositive = outAmount > 0;
			if (inPositive && outPositive) {
				errors.add(sourceRowNumber, "입금금액", "입금금액과 출금금액 중 정확히 한쪽만 양수여야 합니다.");
			}
			else if (!inPositive && !outPositive) {
				errors.add(sourceRowNumber, "입금금액", "입금금액과 출금금액 중 정확히 한쪽만 양수여야 합니다.");
			}
			else if (inPositive) {
				direction = BankDirection.IN;
				amount = inAmount;
			}
			else {
				direction = BankDirection.OUT;
				amount = outAmount;
			}
		}

		if (errors.total() > before || bookedAtSeoul == null || direction == null || balance == null) {
			return null;
		}
		if (amount < 1 || amount > CsvUploadLimits.MAX_AMOUNT) {
			errors.add(sourceRowNumber, direction == BankDirection.IN ? "입금금액" : "출금금액", "금액은 1 이상 1조 이하여야 합니다.");
			return null;
		}
		return new ParsedBankRow(
				sourceRowNumber,
				bookedAtSeoul.toLocalDate(),
				bookedAtSeoul.atZone(SEOUL).toInstant(),
				direction,
				amount,
				balance,
				null,
				description,
				txnType,
				branch,
				BankTransactionFingerprint.from(bookedAtSeoul, direction, amount, balance)
		);
	}

	private String optionalBounded(
			CSVRecord record,
			String field,
			int sourceRowNumber,
			CsvFieldErrorBag errors,
			int maxLength
	) {
		String value = optionalText(record, field);
		if (value != null && value.length() > maxLength) {
			errors.add(sourceRowNumber, field, "길이가 허용 범위를 초과했습니다.");
			return null;
		}
		return value;
	}

	private String requiredText(
			CSVRecord record,
			String field,
			int sourceRowNumber,
			CsvFieldErrorBag errors,
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

	private LocalDate parseDate(String raw, int sourceRowNumber, CsvFieldErrorBag errors) {
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

	private LocalDateTime parseWooriDateTime(String raw, int sourceRowNumber, CsvFieldErrorBag errors) {
		if (raw == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(raw, WOORI_DATE_TIME);
		}
		catch (DateTimeParseException ex) {
			errors.add(sourceRowNumber, "거래일시", "거래일시는 YYYY.MM.DD HH:MM:SS 형식의 실재하는 시각이어야 합니다.");
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
}
