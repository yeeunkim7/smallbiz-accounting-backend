package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationQueryService {

	static final int MAX_INCLUSIVE_DAYS = 366;

	private final ReconciliationAggregationRepository aggregationRepository;

	public ReconciliationQueryService(ReconciliationAggregationRepository aggregationRepository) {
		this.aggregationRepository = aggregationRepository;
	}

	@Transactional(readOnly = true)
	public DailyReconciliationListResponse daily(String fromRaw, String toRaw) {
		LocalDate from = parseDate(fromRaw, "from");
		LocalDate to = parseDate(toRaw, "to");
		if (from.isAfter(to)) {
			throw new InvalidReconciliationQueryException("from", "from은 to보다 이후일 수 없습니다.");
		}
		long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
		if (inclusiveDays > MAX_INCLUSIVE_DAYS) {
			throw new InvalidReconciliationQueryException("to", "조회 기간은 양끝 포함 366일을 넘을 수 없습니다.");
		}
		return new DailyReconciliationListResponse(toDayResponses(aggregationRepository.findDailyTotals(from, to)));
	}

	@Transactional(readOnly = true)
	public MonthlyReconciliationResponse monthly(String yearMonthRaw) {
		YearMonth yearMonth = parseYearMonth(yearMonthRaw);
		LocalDate from = yearMonth.atDay(1);
		LocalDate to = yearMonth.atEndOfMonth();
		List<DailyReconciliationDayResponse> days = toDayResponses(aggregationRepository.findDailyTotals(from, to));
		return toMonthly(yearMonth.toString(), days);
	}

	private List<DailyReconciliationDayResponse> toDayResponses(List<DailyAmountTotals> totals) {
		return totals.stream().map(this::toDayResponse).toList();
	}

	DailyReconciliationDayResponse toDayResponse(DailyAmountTotals totals) {
		long inDifference = subtractExact(totals.actualInAmount(), totals.expectedInAmount());
		long outDifference = subtractExact(totals.actualOutAmount(), totals.expectedOutAmount());
		SourcePresence presence;
		if (totals.hasBusiness() && totals.hasBank()) {
			presence = SourcePresence.BOTH;
		}
		else if (totals.hasBusiness()) {
			presence = SourcePresence.BUSINESS_ONLY;
		}
		else {
			presence = SourcePresence.BANK_ONLY;
		}
		return new DailyReconciliationDayResponse(
				totals.date(),
				totals.expectedInAmount(),
				totals.actualInAmount(),
				inDifference,
				totals.expectedOutAmount(),
				totals.actualOutAmount(),
				outDifference,
				inDifference == 0 ? TotalStatus.TOTAL_EQUAL : TotalStatus.TOTAL_DIFF,
				outDifference == 0 ? TotalStatus.TOTAL_EQUAL : TotalStatus.TOTAL_DIFF,
				presence
		);
	}

	private MonthlyReconciliationResponse toMonthly(String yearMonth, List<DailyReconciliationDayResponse> days) {
		long expectedIn = 0;
		long actualIn = 0;
		long expectedOut = 0;
		long actualOut = 0;
		int differenceDayCount = 0;
		for (DailyReconciliationDayResponse day : days) {
			expectedIn = addExact(expectedIn, day.expectedInAmount());
			actualIn = addExact(actualIn, day.actualInAmount());
			expectedOut = addExact(expectedOut, day.expectedOutAmount());
			actualOut = addExact(actualOut, day.actualOutAmount());
			if (day.inDifference() != 0 || day.outDifference() != 0) {
				differenceDayCount++;
			}
		}
		long inDifference = subtractExact(actualIn, expectedIn);
		long outDifference = subtractExact(actualOut, expectedOut);
		return new MonthlyReconciliationResponse(
				yearMonth,
				expectedIn,
				actualIn,
				inDifference,
				expectedOut,
				actualOut,
				outDifference,
				inDifference == 0 ? TotalStatus.TOTAL_EQUAL : TotalStatus.TOTAL_DIFF,
				outDifference == 0 ? TotalStatus.TOTAL_EQUAL : TotalStatus.TOTAL_DIFF,
				days.size(),
				differenceDayCount
		);
	}

	private LocalDate parseDate(String raw, String field) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidReconciliationQueryException(field, "필수 값입니다.");
		}
		try {
			return LocalDate.parse(raw.trim());
		}
		catch (DateTimeParseException ex) {
			throw new InvalidReconciliationQueryException(field, "날짜는 YYYY-MM-DD 형식의 실재하는 날짜여야 합니다.");
		}
	}

	private YearMonth parseYearMonth(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new InvalidReconciliationQueryException("yearMonth", "필수 값입니다.");
		}
		try {
			return YearMonth.parse(raw.trim());
		}
		catch (DateTimeParseException ex) {
			throw new InvalidReconciliationQueryException("yearMonth", "연월은 YYYY-MM 형식이어야 합니다.");
		}
	}

	private long addExact(long left, long right) {
		try {
			return Math.addExact(left, right);
		}
		catch (ArithmeticException ex) {
			throw new IllegalStateException("Amount total overflowed long");
		}
	}

	private long subtractExact(long left, long right) {
		try {
			return Math.subtractExact(left, right);
		}
		catch (ArithmeticException ex) {
			throw new IllegalStateException("Amount difference overflowed long");
		}
	}
}
