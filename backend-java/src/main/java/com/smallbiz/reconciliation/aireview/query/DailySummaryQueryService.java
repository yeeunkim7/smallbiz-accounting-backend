package com.smallbiz.reconciliation.aireview.query;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.smallbiz.reconciliation.reconciliation.DailyReconciliationDayResponse;
import com.smallbiz.reconciliation.reconciliation.DailyReviewService;
import com.smallbiz.reconciliation.reconciliation.ReconciliationQueryService;

@Service
public class DailySummaryQueryService {
    private final ReconciliationQueryService reconciliation;
    private final DailyReviewService reviews;
    private final JdbcTemplate jdbc;

    public DailySummaryQueryService(ReconciliationQueryService reconciliation, DailyReviewService reviews, JdbcTemplate jdbc) {
        this.reconciliation = reconciliation;
        this.reviews = reviews;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailySummaryResponse summary(String rawDate) {
        LocalDate date = ReviewQueryValidation.date(rawDate, "date");
        var days = reconciliation.daily(rawDate, rawDate).days();
        DailyReconciliationDayResponse totals = days.isEmpty() ? null : days.getFirst();
        long[] businessCounts = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE event_type IN ('CHARGE_EXPECTED','SETTLEMENT_EXPECTED')) AS incoming,
                       COUNT(*) FILTER (WHERE event_type = 'REFUND_EXPECTED') AS outgoing
                FROM business_event WHERE expected_cash_date = ?
                """, (rs, n) -> new long[] {rs.getLong("incoming"), rs.getLong("outgoing")}, date);
        long[] bankCounts = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE direction = 'IN') AS incoming,
                       COUNT(*) FILTER (WHERE direction = 'OUT') AS outgoing
                FROM bank_transaction WHERE booked_date = ?
                """, (rs, n) -> new long[] {rs.getLong("incoming"), rs.getLong("outgoing")}, date);
        return new DailySummaryResponse(date,
                totals == null ? reviews.get(date).status() : totals.reviewStatus(), "NOT_EVALUATED",
                direction(totals == null ? 0 : totals.expectedInAmount(), totals == null ? 0 : totals.actualInAmount(),
                        totals == null ? 0 : totals.inDifference(), businessCounts[0], bankCounts[0]),
                direction(totals == null ? 0 : totals.expectedOutAmount(), totals == null ? 0 : totals.actualOutAmount(),
                        totals == null ? 0 : totals.outDifference(), businessCounts[1], bankCounts[1]));
    }

    private DailySummaryResponse.DirectionSummary direction(long expected, long actual, long difference, long business, long bank) {
        String presence = business == 0 && bank == 0 ? "NONE" : business == 0 ? "BANK_ONLY" : bank == 0 ? "BUSINESS_ONLY" : "BOTH";
        return new DailySummaryResponse.DirectionSummary(Long.toString(expected), Long.toString(actual), Long.toString(difference),
                business, bank, presence, "NONE".equals(presence) ? "NO_DATA" : difference == 0 ? "TOTAL_EQUAL" : "TOTAL_DIFF");
    }
}
