package com.smallbiz.reconciliation.aireview.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import com.smallbiz.reconciliation.reconciliation.InvalidReconciliationQueryException;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import com.smallbiz.reconciliation.vendor.TestDatabaseIsolation;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class ReadOnlyReviewQueriesIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ReadOnlyReviewQueries queries;
    @Autowired MockMvc mvc;
    long vendor;
    long businessUpload;
    long bankUpload;
    long serial;

    @BeforeAll static void rejectDevelopmentDatabase() { TestDatabaseIsolation.assertNotDevelopmentDatabase(); }

    @BeforeEach void setUp() {
        jdbc.update("DELETE FROM daily_review");
        jdbc.update("DELETE FROM bank_transaction");
        jdbc.update("DELETE FROM business_event");
        jdbc.update("DELETE FROM upload_file");
        jdbc.update("DELETE FROM vendor");
        serial = 0;
        vendor = jdbc.queryForObject("""
                INSERT INTO vendor(vendor_code,vendor_name,settlement_type,created_at,updated_at)
                VALUES ('AI-TEST','검증용 거래처','PREPAID',now(),now()) RETURNING id
                """, Long.class);
        businessUpload = upload("BUSINESS", "a");
        bankUpload = upload("BANK", "b");
    }

    @Test void summarySeparatesEmptyDirectionFromEqualTotalsAndLeavesMatchingUnevaluated() throws Exception {
        business("2026-09-03", "CHARGE_EXPECTED", 500, businessUpload);
        business("2026-09-03", "SETTLEMENT_EXPECTED", 800, businessUpload);
        bank("2026-09-03", "IN", 400, bankUpload);
        bank("2026-09-03", "IN", 900, bankUpload);
        mvc.perform(get("/api/v1/ai-review/summary").param("date", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming.expectedAmount").value("1300"))
                .andExpect(jsonPath("$.incoming.actualAmount").value("1300"))
                .andExpect(jsonPath("$.incoming.difference").value("0"))
                .andExpect(jsonPath("$.incoming.businessCount").value(2))
                .andExpect(jsonPath("$.incoming.bankCount").value(2))
                .andExpect(jsonPath("$.incoming.sourcePresence").value("BOTH"))
                .andExpect(jsonPath("$.incoming.comparisonStatus").value("TOTAL_EQUAL"))
                .andExpect(jsonPath("$.outgoing.sourcePresence").value("NONE"))
                .andExpect(jsonPath("$.outgoing.comparisonStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.matchingStatus").value("NOT_EVALUATED"));
    }

    @Test void summaryUsesExistingSignedDifferencesAndDirectionSpecificPresence() {
        business("2026-09-03", "CHARGE_EXPECTED", 100, businessUpload);
        bank("2026-09-03", "OUT", 100, bankUpload);
        var summary = queries.summary("2026-09-03");
        assertEquals("-100", summary.incoming().difference());
        assertEquals("BUSINESS_ONLY", summary.incoming().sourcePresence());
        assertEquals("100", summary.outgoing().difference());
        assertEquals("BANK_ONLY", summary.outgoing().sourcePresence());
        assertEquals("TOTAL_DIFF", summary.outgoing().comparisonStatus());
    }

    @Test void missingDateAndUsageOnlyAreNoDataAndDoNotImplyMissingUpload() {
        business("2026-09-03", "USAGE", 100, businessUpload);
        var summary = queries.summary("2026-09-03");
        assertEquals("NO_DATA", summary.incoming().comparisonStatus());
        assertEquals("NO_DATA", summary.outgoing().comparisonStatus());
        assertEquals(0, queries.uploads("2026-09-03", "2026-09-03", 0, 20).totalElements());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM upload_file", Integer.class));
    }

    @Test void filtersRangesDirectionsAndPagesWithStableOrderingAndTrace() throws Exception {
        business("2026-09-04", "CHARGE_EXPECTED", 9, businessUpload);
        business("2026-09-03", "REFUND_EXPECTED", 5, businessUpload);
        business("2026-09-03", "CHARGE_EXPECTED", 7, businessUpload);
        business("2026-09-03", "USAGE", 99, businessUpload);
        business("2026-09-05", "CHARGE_EXPECTED", 999, businessUpload);
        bank("2026-09-03", "OUT", 5, bankUpload);
        bank("2026-09-04", "IN", 9, bankUpload);
        bank("2026-09-05", "IN", 999, bankUpload);
        var first = queries.business("2026-09-03", "2026-09-04", "IN", 0, 1);
        var second = queries.business("2026-09-03", "2026-09-04", "IN", 1, 1);
        assertEquals(2, first.totalElements());
        assertEquals("2026-09-03", first.content().getFirst().expectedCashDate().toString());
        assertEquals("2026-09-04", second.content().getFirst().expectedCashDate().toString());
        assertNotEquals(first.content().getFirst().id(), second.content().getFirst().id());
        assertEquals(businessUpload, first.content().getFirst().uploadId());
        assertNotNull(first.content().getFirst().sourceLineId());
        assertTrue(first.content().getFirst().sourceRowNumber() >= 2);
        assertEquals(1, queries.business("2026-09-03", "2026-09-04", "OUT", 0, 20).totalElements());
        assertEquals(1, queries.bank("2026-09-03", "2026-09-04", "OUT", 0, 20).totalElements());
        assertEquals(3, queries.business("2026-09-03", "2026-09-04", null, 0, 20).totalElements());
        mvc.perform(get("/api/v1/ai-review/originals/bank").param("from", "2026-09-03").param("to", "2026-09-04").param("direction", "IN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("IN"));
    }

    @Test void relatedUploadsAreDistinctAcrossAllPagesAndUseTransactionDatesNotUploadTime() throws Exception {
        long extra = upload("BUSINESS", "c");
        business("2026-09-03", "CHARGE_EXPECTED", 10, businessUpload);
        business("2026-09-03", "CHARGE_EXPECTED", 10, businessUpload);
        business("2026-09-03", "REFUND_EXPECTED", 10, extra);
        bank("2026-09-03", "OUT", 10, bankUpload);
        jdbc.update("UPDATE upload_file SET uploaded_at='2020-01-01T00:00:00Z'");
        assertEquals(1, queries.business("2026-09-03", "2026-09-03", null, 0, 1).content().size());
        var first = queries.uploads("2026-09-03", "2026-09-03", 0, 2);
        var second = queries.uploads("2026-09-03", "2026-09-03", 1, 2);
        assertEquals(3, first.totalElements());
        assertEquals(2, first.content().size());
        assertEquals(1, second.content().size());
        assertEquals(extra, first.content().getFirst().uploadId());
        assertEquals(businessUpload, second.content().getFirst().uploadId());
        mvc.perform(get("/api/v1/ai-review/uploads").param("from", "2026-09-03").param("to", "2026-09-03"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test void rejectsInvalidArgumentsWhenCalledDirectlyAndThroughHttp() throws Exception {
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.summary("2026-02-30"));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.business("2026-09-04", "2026-09-03", null, 0, 20));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.bank("2026-01-01", "2027-01-02", null, 0, 20));
        assertDoesNotThrow(() -> queries.bank("2026-01-01", "2027-01-01", null, 0, 20));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.business("2026-09-03", "2026-09-03", "DROP TABLE", 0, 20));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.bank("2026-09-03", "2026-09-03", "", 0, 20));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.uploads("2026-09-03", "2026-09-03", -1, 20));
        assertThrows(InvalidReconciliationQueryException.class, () -> queries.uploads("2026-09-03", "2026-09-03", 0, 101));
        mvc.perform(get("/api/v1/ai-review/summary").param("date", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test void allQueriesLeaveOriginalsUploadsAndReviewStateUnchanged() {
        business("2026-09-03", "CHARGE_EXPECTED", 100, businessUpload);
        bank("2026-09-03", "IN", 100, bankUpload);
        jdbc.update("""
                INSERT INTO daily_review(review_date,status,memo,last_reviewed_at,updated_at,version)
                VALUES ('2026-09-03','REVIEWED','보존 메모',now(),now(),3)
                """);
        var before = snapshot();
        assertEquals("REVIEWED", queries.summary("2026-09-03").reviewStatus().name());
        queries.summary("2026-09-04");
        queries.business("2026-09-03", "2026-09-04", null, 0, 20);
        queries.bank("2026-09-03", "2026-09-04", null, 0, 20);
        queries.uploads("2026-09-03", "2026-09-04", 0, 20);
        assertEquals(before, snapshot());
    }

    @Test void summarySerializesLargeExistingJavaAmountsAsExactDecimalText() throws Exception {
        jdbc.update("""
                INSERT INTO business_event(vendor_id,upload_file_id,source_line_id,source_row_number,event_type,expected_cash_date,amount)
                SELECT ?,?,'BIG-'||n,n+1,'CHARGE_EXPECTED','2026-09-03',1000000000000 FROM generate_series(1,9100) n
                """, vendor, businessUpload);
        mvc.perform(get("/api/v1/ai-review/summary").param("date", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incoming.expectedAmount").value("9100000000000000"))
                .andExpect(jsonPath("$.incoming.difference").value("-9100000000000000"))
                .andExpect(jsonPath("$.incoming.businessCount").value(9100));
    }

    private long upload(String type, String hashChar) {
        return jdbc.queryForObject("""
                INSERT INTO upload_file(file_type,original_filename,content_sha256,row_count,uploaded_at)
                VALUES (?, ?, ?, 1, now()) RETURNING id
                """, Long.class, type, type + ".csv", hashChar.repeat(64));
    }

    private void business(String date, String type, long amount, long upload) {
        serial++;
        jdbc.update("""
                INSERT INTO business_event(vendor_id,upload_file_id,source_line_id,source_row_number,event_type,usage_date,expected_cash_date,amount)
                VALUES (?,?,?,?,?,?::date,?::date,?)
                """, vendor, upload, "BIZ-" + serial, (int) serial + 1, type,
                "USAGE".equals(type) ? date : null, "USAGE".equals(type) ? null : date, amount);
    }

    private void bank(String date, String direction, long amount, long upload) {
        serial++;
        jdbc.update("""
                INSERT INTO bank_transaction(upload_file_id,source_line_id,source_row_number,booked_date,direction,amount)
                VALUES (?,?,?,?::date,?,?)
                """, upload, "BANK-" + serial, (int) serial + 1, date, direction, amount);
    }

    private Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : new String[] {"vendor", "upload_file", "business_event", "bank_transaction", "daily_review"}) {
            String key = table.equals("daily_review") ? "review_date" : "id";
            result.put(table, jdbc.queryForObject("SELECT COALESCE(json_agg(t ORDER BY " + key + ")::text,'[]') FROM " + table + " t", String.class));
        }
        return result;
    }
}
