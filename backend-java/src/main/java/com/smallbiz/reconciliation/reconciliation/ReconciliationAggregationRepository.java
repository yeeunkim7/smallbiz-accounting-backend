package com.smallbiz.reconciliation.reconciliation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationAggregationRepository {

	/**
	 * 업무·은행을 각각 날짜별로 먼저 합친 뒤 날짜로 붙인다.
	 * 같은 날짜의 원본 행을 JOIN하면 행 수끼리 곱해져 금액이 커진다.
	 */
	private static final String DAILY_SQL = """
			WITH business_daily AS (
			    SELECT expected_cash_date AS day,
			           COALESCE(SUM(amount) FILTER (
			               WHERE event_type IN ('CHARGE_EXPECTED', 'SETTLEMENT_EXPECTED')
			           ), 0)::bigint AS expected_in,
			           COALESCE(SUM(amount) FILTER (
			               WHERE event_type = 'REFUND_EXPECTED'
			           ), 0)::bigint AS expected_out
			    FROM business_event
			    WHERE expected_cash_date BETWEEN ? AND ?
			      AND event_type IN ('CHARGE_EXPECTED', 'SETTLEMENT_EXPECTED', 'REFUND_EXPECTED')
			    GROUP BY expected_cash_date
			),
			bank_daily AS (
			    SELECT booked_date AS day,
			           COALESCE(SUM(amount) FILTER (WHERE direction = 'IN'), 0)::bigint AS actual_in,
			           COALESCE(SUM(amount) FILTER (WHERE direction = 'OUT'), 0)::bigint AS actual_out
			    FROM bank_transaction
			    WHERE booked_date BETWEEN ? AND ?
			    GROUP BY booked_date
			)
			SELECT d.day,
			       COALESCE(b.expected_in, 0) AS expected_in,
			       COALESCE(k.actual_in, 0) AS actual_in,
			       COALESCE(b.expected_out, 0) AS expected_out,
			       COALESCE(k.actual_out, 0) AS actual_out,
			       (b.day IS NOT NULL) AS has_business,
			       (k.day IS NOT NULL) AS has_bank
			FROM (
			    SELECT day FROM business_daily
			    UNION
			    SELECT day FROM bank_daily
			) d
			LEFT JOIN business_daily b ON b.day = d.day
			LEFT JOIN bank_daily k ON k.day = d.day
			ORDER BY d.day
			""";

	private final JdbcTemplate jdbcTemplate;

	public ReconciliationAggregationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<DailyAmountTotals> findDailyTotals(LocalDate from, LocalDate to) {
		return jdbcTemplate.query(
				DAILY_SQL,
				(rs, rowNum) -> new DailyAmountTotals(
						rs.getObject("day", LocalDate.class),
						rs.getLong("expected_in"),
						rs.getLong("actual_in"),
						rs.getLong("expected_out"),
						rs.getLong("actual_out"),
						rs.getBoolean("has_business"),
						rs.getBoolean("has_bank")
				),
				from,
				to,
				from,
				to
		);
	}
}
