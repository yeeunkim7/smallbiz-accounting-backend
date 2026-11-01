package com.smallbiz.reconciliation.receivables.matching;

import static com.smallbiz.reconciliation.receivables.ReceivablesValidation.*;
import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.account.TenantAccess;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class MatchingService {
    static final String RULE_VERSION = "manual-review-rules-v1";
    private final JdbcTemplate jdbc;
    private final TenantAccess tenants;
    private final AuthService auth;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final MatchingPolicy policy;
    private final TransactionTemplate transaction;

    public MatchingService(JdbcTemplate jdbc, TenantAccess tenants, AuthService auth, Clock clock, ObjectMapper mapper,
            MatchingPolicy policy, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.tenants = tenants; this.auth = auth; this.clock = clock; this.mapper = mapper; this.policy = policy;
        transaction = new TransactionTemplate(transactionManager);
        // Duplicate INSERT must see a preceding transaction's committed result in the next statement.
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public RunResponse create(UUID tenant, String requestKey, Map<String, Object> body) {
        return transaction.execute(status -> {
            tenants.require(tenant);
            key(requestKey);
            Range range = range(body);
            String hash = hash(range);
            UUID id = UUID.randomUUID(), actor = auth.requireActiveSession().accountId();
            Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            int inserted = jdbc.update("""
                    INSERT INTO receivable_matching_run
                    (id,tenant_id,request_key,request_hash,rule_version,from_date,to_date,status,created_by,created_at)
                    VALUES (?,?,?,?,?,?,?,'RUNNING',?,?) ON CONFLICT (tenant_id,request_key) DO NOTHING
                    """, id, tenant, requestKey, hash, RULE_VERSION, range.from(), range.to(), actor, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
            if (inserted == 0) {
                return jdbc.queryForObject("SELECT request_hash,status,result_json::text FROM receivable_matching_run WHERE tenant_id=? AND request_key=?",
                        (rs, row) -> {
                            if (!hash.equals(rs.getString("request_hash"))) throw MatchingException.conflict();
                            return snapshot(rs);
                        }, tenant, requestKey);
            }
            Sources sources = sources(tenant, range);
            var results = sources.payments().stream().map(payment -> suggestions(payment, sources.invoices())).toList();
            String presence = sources.payments().isEmpty()
                    ? sources.invoices().isEmpty() ? "NO_ELIGIBLE_SOURCES" : "INVOICES_ONLY"
                    : sources.invoices().isEmpty() ? "PAYMENTS_ONLY" : "BOTH";
            RunResponse result = new RunResponse(id, tenant, RULE_VERSION, range.from(), range.to(), "SUCCESS", actor, createdAt,
                    new SourceCounts(sources.payments().size(), sources.invoices().size()), presence, policy.topPerPayment(),
                    sources.invoices(), results);
            int saved = jdbc.update("""
                    UPDATE receivable_matching_run SET status='SUCCESS',result_json=CAST(? AS jsonb)
                    WHERE tenant_id=? AND id=? AND status='RUNNING'
                    """, mapper.writeValueAsString(result), tenant, id);
            if (saved != 1) throw MatchingException.storage();
            return result;
        });
    }
    public RunResponse get(UUID tenant, UUID id) {
        tenants.require(tenant);
        var rows = jdbc.query("SELECT status,result_json::text FROM receivable_matching_run WHERE tenant_id=? AND id=?",
                (rs, row) -> snapshot(rs), tenant, id);
        if (rows.isEmpty()) throw MatchingException.missing();
        return rows.getFirst();
    }
    public Page list(UUID tenant, int page, int size) {
        tenants.require(tenant);
        page(page, size);
        var rows = jdbc.query("""
                SELECT id,rule_version,from_date,to_date,status,created_by,created_at,
                    result_json->'sourceCounts' AS source_counts,result_json->>'dataPresence' AS data_presence
                FROM receivable_matching_run WHERE tenant_id=? AND status='SUCCESS'
                ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new RunSummary(rs.getObject("id", UUID.class), rs.getString("rule_version"),
                rs.getObject("from_date", LocalDate.class), rs.getObject("to_date", LocalDate.class), rs.getString("status"),
                rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                mapper.readValue(rs.getString("source_counts"), SourceCounts.class), rs.getString("data_presence")), tenant, size + 1, (long) page * size);
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, rows.size() > size);
    }

    private Sources sources(UUID tenant, Range range) {
        // Both balances and their evidence use ONE PostgreSQL statement snapshot; no parent locks,
        // no stale cache, and no separate queries that can straddle a confirmation/cancellation.
        var rows = jdbc.query("""
                WITH active AS (
                    SELECT l.payment_id,l.invoice_id,l.amount FROM receivable_allocation_line l
                    LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                    WHERE l.tenant_id=? AND c.id IS NULL
                ), payment_rows AS (
                    SELECT p.id,p.amount,p.booked_date,p.payer_name,coalesce(sum(a.amount),0) AS allocated
                    FROM receivable_payment p LEFT JOIN active a ON a.payment_id=p.id
                    WHERE p.tenant_id=? AND p.booked_date BETWEEN ? AND ? GROUP BY p.id
                ), invoice_rows AS (
                    SELECT i.id,i.counterparty_id,i.invoice_number,i.amount,i.issue_date,i.due_date,cp.name,
                        coalesce(sum(a.amount),0) AS allocated
                    FROM receivable_invoice i
                    JOIN receivable_counterparty cp ON cp.tenant_id=i.tenant_id AND cp.id=i.counterparty_id
                    LEFT JOIN active a ON a.invoice_id=i.id
                    WHERE i.tenant_id=? AND i.issue_date<=? GROUP BY i.id,cp.id
                ), bounded_payments AS (
                    SELECT * FROM payment_rows WHERE allocated<>amount ORDER BY id LIMIT ?
                ), bounded_invoices AS (
                    SELECT * FROM invoice_rows WHERE allocated<>amount ORDER BY id LIMIT ?
                )
                SELECT 'PAYMENT' AS kind,id,amount,allocated,booked_date AS source_date,NULL::date AS due_date,
                    payer_name AS name,NULL::uuid AS counterparty_id,NULL::varchar AS invoice_number FROM bounded_payments
                UNION ALL
                SELECT 'INVOICE',id,amount,allocated,issue_date,due_date,name,counterparty_id,invoice_number FROM bounded_invoices
                """, (rs, row) -> new SourceRow(rs.getString("kind"), rs.getObject("id", UUID.class), rs.getLong("amount"),
                rs.getBigDecimal("allocated").toBigIntegerExact(), rs.getObject("source_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class), rs.getString("name"), rs.getObject("counterparty_id", UUID.class),
                rs.getString("invoice_number")), tenant, tenant, range.from(), range.to(), tenant, range.to(), policy.maxPayments() + 1, policy.maxInvoices() + 1);
        List<PaymentSource> payments = new ArrayList<>();
        List<InvoiceSource> invoices = new ArrayList<>();
        for (var row : rows) {
            if (row.allocated().signum() < 0 || row.allocated().compareTo(BigInteger.valueOf(row.amount())) > 0) throw MatchingException.storage();
            String remaining = BigInteger.valueOf(row.amount()).subtract(row.allocated()).toString();
            if (row.kind().equals("PAYMENT")) {
                payments.add(new PaymentSource(row.id(), Long.toString(row.amount()), row.allocated().toString(), remaining,
                        row.sourceDate(), row.name(), normalizeName(row.name())));
            } else {
                invoices.add(new InvoiceSource(row.id(), row.counterpartyId(), row.invoiceNumber(), Long.toString(row.amount()),
                        row.allocated().toString(), remaining, row.sourceDate(), row.dueDate(), row.name(), normalizeName(row.name())));
            }
        }
        if (payments.size() > policy.maxPayments() || invoices.size() > policy.maxInvoices()) throw MatchingException.limit();
        payments.sort(Comparator.comparing(p -> p.id().toString()));
        invoices.sort(Comparator.comparing(i -> i.id().toString()));
        return new Sources(List.copyOf(payments), List.copyOf(invoices));
    }
    private PaymentResult suggestions(PaymentSource payment, List<InvoiceSource> invoices) {
        List<Candidate> candidates = new ArrayList<>();
        long available = Long.parseLong(payment.unallocatedAmount());
        for (InvoiceSource invoice : invoices) {
            long outstanding = Long.parseLong(invoice.outstandingAmount());
            boolean amount = available == outstanding;
            boolean name = !payment.normalizedPayerName().isEmpty() && payment.normalizedPayerName().equals(invoice.normalizedCounterpartyName());
            if (!amount && !name) continue;
            List<String> reasons = new ArrayList<>(), warnings = new ArrayList<>();
            int score = 0;
            if (amount) { reasons.add("EXACT_REMAINING_AMOUNT"); score += 100; }
            else warnings.add("REMAINING_AMOUNT_MISMATCH");
            if (name) { reasons.add("NORMALIZED_NAME_EXACT"); score += 50; }
            if (payment.normalizedPayerName().isEmpty()) warnings.add("PAYER_NAME_MISSING");
            if (invoice.normalizedCounterpartyName().isEmpty()) warnings.add("COUNTERPARTY_NAME_MISSING");
            if (!name && !payment.normalizedPayerName().isEmpty() && !invoice.normalizedCounterpartyName().isEmpty()) warnings.add("NAME_MISMATCH");
            if (payment.bookedDate().isBefore(invoice.issueDate())) warnings.add("PAYMENT_BEFORE_ISSUE_DATE");
            else { reasons.add("BOOKED_ON_OR_AFTER_ISSUE_DATE"); score += 10; }
            if (payment.bookedDate().isAfter(invoice.dueDate())) warnings.add("PAYMENT_AFTER_DUE_DATE");
            candidates.add(new Candidate(invoice.id(), Long.toString(Math.min(available, outstanding)), score,
                    Math.abs(ChronoUnit.DAYS.between(payment.bookedDate(), invoice.dueDate())), List.copyOf(reasons), List.copyOf(warnings)));
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingLong(Candidate::dueDateDistanceDays)
                .thenComparing(c -> c.invoiceId().toString()));
        return new PaymentResult(payment, candidates.isEmpty() ? "NEEDS_MANUAL_REVIEW" : "CANDIDATES_AVAILABLE", candidates.size(),
                candidates.size() > policy.topPerPayment(), List.copyOf(candidates.subList(0, Math.min(candidates.size(), policy.topPerPayment()))));
    }
    static String normalizeName(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKC).replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
    private Range range(Map<String, Object> body) {
        fields(body, Set.of("from", "to"));
        LocalDate from = date(body.get("from")), to = date(body.get("to"));
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) throw MatchingException.invalid();
        return new Range(from, to);
    }
    private String hash(Object input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private RunResponse snapshot(ResultSet rs) throws SQLException {
        if (!"SUCCESS".equals(rs.getString("status"))) throw MatchingException.storage();
        return mapper.readValue(rs.getString("result_json"), RunResponse.class);
    }
    private record Range(LocalDate from, LocalDate to) {}
    private record SourceRow(String kind, UUID id, long amount, BigInteger allocated, LocalDate sourceDate, LocalDate dueDate,
            String name, UUID counterpartyId, String invoiceNumber) {}
    private record Sources(List<PaymentSource> payments, List<InvoiceSource> invoices) {}
    public record SourceCounts(int paymentCount, int invoiceCount) {}
    public record PaymentSource(UUID id, String amount, String allocatedAmount, String unallocatedAmount, LocalDate bookedDate,
            String payerName, String normalizedPayerName) {}
    public record InvoiceSource(UUID id, UUID counterpartyId, String invoiceNumber, String amount, String allocatedAmount,
            String outstandingAmount, LocalDate issueDate, LocalDate dueDate, String counterpartyName, String normalizedCounterpartyName) {}
    public record Candidate(UUID invoiceId, String suggestedAmount, int score, long dueDateDistanceDays, List<String> reasons, List<String> warnings) {}
    public record PaymentResult(PaymentSource payment, String status, int totalCandidateCount, boolean candidatesTruncated, List<Candidate> candidates) {}
    public record RunResponse(UUID id, UUID tenantId, String ruleVersion, LocalDate from, LocalDate to, String status, UUID createdBy,
            Instant createdAt, SourceCounts sourceCounts, String dataPresence, int topPerPayment, List<InvoiceSource> invoices, List<PaymentResult> payments) {}
    public record RunSummary(UUID id, String ruleVersion, LocalDate from, LocalDate to, String status, UUID createdBy, Instant createdAt,
            SourceCounts sourceCounts, String dataPresence) {}
    public record Page(List<RunSummary> items, int page, int size, boolean hasNext) {}
}
