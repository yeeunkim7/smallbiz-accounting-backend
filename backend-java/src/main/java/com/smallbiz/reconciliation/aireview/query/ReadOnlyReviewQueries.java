package com.smallbiz.reconciliation.aireview.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.smallbiz.reconciliation.reconciliation.BankOriginalListResponse;
import com.smallbiz.reconciliation.reconciliation.BankOriginalResponse;
import com.smallbiz.reconciliation.reconciliation.BusinessOriginalListResponse;
import com.smallbiz.reconciliation.reconciliation.BusinessOriginalResponse;
import com.smallbiz.reconciliation.upload.BankDirection;
import com.smallbiz.reconciliation.upload.BusinessEventType;
import com.smallbiz.reconciliation.upload.UploadFileListResponse;
import com.smallbiz.reconciliation.upload.UploadFileResponse;
import com.smallbiz.reconciliation.upload.UploadFileType;

/** Explicit query-only boundary shared by the UI and the AI tool registry. No write service is exposed. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadOnlyReviewQueries {
    private static final String CASH_TYPES = "('CHARGE_EXPECTED','SETTLEMENT_EXPECTED','REFUND_EXPECTED')";
    private static final String RELATED_UPLOADS = """
            SELECT upload_file_id FROM business_event
            WHERE expected_cash_date BETWEEN ? AND ?
              AND event_type IN ('CHARGE_EXPECTED','SETTLEMENT_EXPECTED','REFUND_EXPECTED')
            UNION
            SELECT upload_file_id FROM bank_transaction WHERE booked_date BETWEEN ? AND ?
            """;
    private final DailySummaryQueryService summaries;
    private final JdbcTemplate jdbc;

    public ReadOnlyReviewQueries(DailySummaryQueryService summaries, JdbcTemplate jdbc) {
        this.summaries = summaries;
        this.jdbc = jdbc;
    }

    public DailySummaryResponse summary(String date) { return summaries.summary(date); }

    public BusinessOriginalListResponse business(String from, String to, String direction, int page, int size) {
        var range = ReviewQueryValidation.range(from, to);
        ReviewQueryValidation.page(page, size);
        ReviewQueryValidation.direction(direction);
        String types = direction == null ? CASH_TYPES : "IN".equals(direction)
                ? "('CHARGE_EXPECTED','SETTLEMENT_EXPECTED')" : "('REFUND_EXPECTED')";
        String where = " WHERE e.expected_cash_date BETWEEN ? AND ? AND e.event_type IN " + types;
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM business_event e" + where, Long.class, range.from(), range.to());
        var rows = jdbc.query("""
                SELECT e.*, v.vendor_code, v.vendor_name
                FROM business_event e JOIN vendor v ON v.id=e.vendor_id
                """ + where + " ORDER BY e.expected_cash_date, e.source_row_number, e.id LIMIT ? OFFSET ?",
                (rs, n) -> new BusinessOriginalResponse(rs.getLong("id"), rs.getString("vendor_code"), rs.getString("vendor_name"),
                        BusinessEventType.valueOf(rs.getString("event_type")), rs.getLong("amount"),
                        rs.getObject("usage_date", java.time.LocalDate.class), rs.getObject("expected_cash_date", java.time.LocalDate.class),
                        rs.getString("note"), rs.getLong("upload_file_id"), rs.getString("source_line_id"), rs.getInt("source_row_number")),
                range.from(), range.to(), size, (long) page * size);
        return new BusinessOriginalListResponse(rows, page, size, total);
    }

    public BankOriginalListResponse bank(String from, String to, String direction, int page, int size) {
        var range = ReviewQueryValidation.range(from, to);
        ReviewQueryValidation.page(page, size);
        ReviewQueryValidation.direction(direction);
        String where = " WHERE booked_date BETWEEN ? AND ?" + (direction == null ? "" : " AND direction = ?");
        Object[] countArgs = direction == null ? new Object[] {range.from(), range.to()}
                : new Object[] {range.from(), range.to(), direction};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM bank_transaction" + where, Long.class, countArgs);
        Object[] args = direction == null ? new Object[] {range.from(), range.to(), size, (long) page * size}
                : new Object[] {range.from(), range.to(), direction, size, (long) page * size};
        var rows = jdbc.query("SELECT * FROM bank_transaction" + where + " ORDER BY booked_date, source_row_number, id LIMIT ? OFFSET ?",
                (rs, n) -> new BankOriginalResponse(rs.getLong("id"), rs.getObject("booked_date", java.time.LocalDate.class),
                        instant(rs, "booked_at"), BankDirection.valueOf(rs.getString("direction")), rs.getLong("amount"),
                        rs.getObject("balance_after", Long.class), rs.getString("counterparty_name"), rs.getString("description"),
                        rs.getString("txn_type"), rs.getString("branch_name"), rs.getLong("upload_file_id"),
                        rs.getString("source_line_id"), rs.getInt("source_row_number")), args);
        return new BankOriginalListResponse(rows, page, size, total);
    }

    /** Related by cash transaction dates, never by the unrelated upload timestamp or just one originals page. */
    public UploadFileListResponse uploads(String from, String to, int page, int size) {
        var range = ReviewQueryValidation.range(from, to);
        ReviewQueryValidation.page(page, size);
        Object[] countArgs = {range.from(), range.to(), range.from(), range.to()};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM upload_file WHERE id IN (" + RELATED_UPLOADS + ")", Long.class, countArgs);
        var rows = jdbc.query("SELECT * FROM upload_file WHERE id IN (" + RELATED_UPLOADS + ") ORDER BY uploaded_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new UploadFileResponse(rs.getLong("id"), UploadFileType.valueOf(rs.getString("file_type")),
                        rs.getString("original_filename"), rs.getInt("row_count"), instant(rs, "uploaded_at")),
                range.from(), range.to(), range.from(), range.to(), size, (long) page * size);
        return new UploadFileListResponse(rows, page, size, total);
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
