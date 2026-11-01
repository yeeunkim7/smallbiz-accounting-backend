package com.smallbiz.reconciliation.receivables.allocation;

import static com.smallbiz.reconciliation.receivables.ReceivablesValidation.*;
import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.account.TenantAccess;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class AllocationService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenants;
    private final AuthService auth;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    public AllocationService(JdbcTemplate jdbc, TenantAccess tenants, AuthService auth, Clock clock,
            ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.tenants = tenants; this.auth = auth; this.clock = clock; this.mapper = mapper;
        write = new TransactionTemplate(transactionManager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read = new TransactionTemplate(transactionManager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public AllocationResponse confirm(UUID tenant, String requestKey, Map<String, Object> body) {
        return write.execute(transaction -> {
            tenants.require(tenant);
            key(requestKey);
            var input = AllocationInput.confirmation(body);
            var command = reserve(tenant, "CONFIRM", requestKey, input);
            if (command.replay() != null) return command.replay();
            lockAndValidate(tenant, input.lines());
            UUID batch = UUID.randomUUID(), actor = auth.requireActiveSession().accountId();
            OffsetDateTime time = now();
            jdbc.update("INSERT INTO receivable_allocation_batch (id,tenant_id,reason,created_by,created_at) VALUES (?,?,?,?,?)",
                    batch, tenant, input.reason(), actor, time);
            int number = 0;
            for (var line : input.lines()) {
                jdbc.update("""
                        INSERT INTO receivable_allocation_line (id,tenant_id,batch_id,line_number,payment_id,invoice_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, UUID.randomUUID(), tenant, batch, ++number, line.paymentId(), line.invoiceId(), line.amount());
            }
            return audit(tenant, batch, command.auditId(), "CONFIRMED", null, input.reason(), actor, time);
        });
    }

    public AllocationResponse cancel(UUID tenant, UUID batch, String requestKey, Map<String, Object> body) {
        return write.execute(transaction -> {
            tenants.require(tenant);
            key(requestKey);
            var input = AllocationInput.cancellation(batch, body);
            var command = reserve(tenant, "CANCEL", requestKey, input);
            if (command.replay() != null) return command.replay();
            requireBatch(tenant, batch);
            // Lines never change. Discover parents without locking batch/cancellation state first.
            var lines = lines(tenant, batch);
            lockParents(tenant, lines.stream().map(l -> new AllocationInput.Line(l.paymentId(), l.invoiceId(), Long.parseLong(l.amount()))).toList());
            // Parent locks serialize confirmations and competing cancellations before this recheck.
            if (cancellation(tenant, batch) != null) throw AllocationException.cancelled();
            UUID cancellation = UUID.randomUUID(), actor = auth.requireActiveSession().accountId();
            OffsetDateTime time = now();
            jdbc.update("""
                    INSERT INTO receivable_allocation_cancellation (id,tenant_id,batch_id,reason,created_by,created_at)
                    VALUES (?,?,?,?,?,?)
                    """, cancellation, tenant, batch, input.reason(), actor, time);
            return audit(tenant, batch, command.auditId(), "CANCELLED", cancellation, input.reason(), actor, time);
        });
    }

    public AllocationResponse get(UUID tenant, UUID batch) {
        return read.execute(transaction -> {
            tenants.require(tenant);
            return detail(tenant, batch);
        });
    }
    public Page list(UUID tenant, int page, int size) {
        return read.execute(transaction -> {
            tenants.require(tenant);
            page(page, size);
            var ids = jdbc.query("""
                    SELECT id FROM receivable_allocation_batch WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                    """, (rs, row) -> uuid(rs, "id"), tenant, size + 1, (long) page * size);
            return new Page(ids.subList(0, Math.min(ids.size(), size)).stream().map(id -> detail(tenant, id)).toList(), page, size, ids.size() > size);
        });
    }

    public InvoiceBalance invoiceBalance(UUID tenant, UUID invoice, String asOf) {
        tenants.require(tenant);
        LocalDate cutoff = date(asOf);
        var rows = jdbc.query("""
                SELECT i.amount,i.due_date,coalesce(sum(l.amount) FILTER (WHERE c.id IS NULL),0) AS allocated
                FROM receivable_invoice i
                LEFT JOIN receivable_allocation_line l ON l.tenant_id=i.tenant_id AND l.invoice_id=i.id
                LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                WHERE i.tenant_id=? AND i.id=? GROUP BY i.id
                """, (rs, row) -> {
                    BigInteger original = rs.getBigDecimal("amount").toBigIntegerExact();
                    BigInteger allocated = rs.getBigDecimal("allocated").toBigIntegerExact();
                    BigInteger remaining = remainder(original, allocated);
                    LocalDate due = rs.getObject("due_date", LocalDate.class);
                    return new InvoiceBalance(invoice, tenant, original.toString(), allocated.toString(), remaining.toString(),
                            due.isBefore(cutoff) ? remaining.toString() : "0", due, cutoff,
                            allocated.signum() == 0 ? "UNPAID" : remaining.signum() == 0 ? "PAID" : "PARTIAL");
                }, tenant, invoice);
        return one(rows);
    }
    public PaymentBalance paymentBalance(UUID tenant, UUID payment) {
        tenants.require(tenant);
        var rows = jdbc.query("""
                SELECT p.amount,coalesce(sum(l.amount) FILTER (WHERE c.id IS NULL),0) AS allocated
                FROM receivable_payment p
                LEFT JOIN receivable_allocation_line l ON l.tenant_id=p.tenant_id AND l.payment_id=p.id
                LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                WHERE p.tenant_id=? AND p.id=? GROUP BY p.id
                """, (rs, row) -> {
                    BigInteger original = rs.getBigDecimal("amount").toBigIntegerExact();
                    BigInteger allocated = rs.getBigDecimal("allocated").toBigIntegerExact();
                    BigInteger remaining = remainder(original, allocated);
                    return new PaymentBalance(payment, tenant, original.toString(), allocated.toString(), remaining.toString(),
                            allocated.signum() == 0 ? "UNALLOCATED" : remaining.signum() == 0 ? "ALLOCATED" : "PARTIAL");
                }, tenant, payment);
        return one(rows);
    }
    public Summary summary(UUID tenant, String asOf) {
        tenants.require(tenant);
        LocalDate cutoff = date(asOf);
        // One SQL statement supplies a coherent current snapshot. PostgreSQL SUM(bigint) is numeric.
        return jdbc.queryForObject("""
                WITH active AS (
                    SELECT l.invoice_id,l.payment_id,l.amount FROM receivable_allocation_line l
                    LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                    WHERE l.tenant_id=? AND c.id IS NULL
                ), inv AS (
                    SELECT i.amount,i.due_date,coalesce(sum(a.amount),0) AS allocated
                    FROM receivable_invoice i LEFT JOIN active a ON a.invoice_id=i.id
                    WHERE i.tenant_id=? GROUP BY i.id
                ), pay AS (
                    SELECT p.amount,coalesce(sum(a.amount),0) AS allocated
                    FROM receivable_payment p LEFT JOIN active a ON a.payment_id=p.id
                    WHERE p.tenant_id=? GROUP BY p.id
                ) SELECT
                    (SELECT count(*) FROM inv) AS invoice_count,
                    (SELECT count(*) FROM pay) AS payment_count,
                    (SELECT count(*) FROM active) AS active_line_count,
                    (SELECT coalesce(sum(amount),0) FROM inv) AS invoice_amount,
                    (SELECT coalesce(sum(allocated),0) FROM inv) AS allocated_amount,
                    (SELECT coalesce(sum(amount-allocated),0) FROM inv) AS outstanding,
                    (SELECT coalesce(sum(CASE WHEN due_date<? THEN amount-allocated ELSE 0 END),0) FROM inv) AS overdue,
                    (SELECT coalesce(sum(amount),0) FROM pay) AS payment_amount,
                    (SELECT coalesce(sum(amount-allocated),0) FROM pay) AS unallocated,
                    (SELECT count(*) FROM inv WHERE allocated<0 OR allocated>amount) +
                    (SELECT count(*) FROM pay WHERE allocated<0 OR allocated>amount) AS invalid
                """, (rs, row) -> {
                    if (rs.getLong("invalid") != 0) throw AllocationException.invariant();
                    return new Summary(tenant, cutoff, "CURRENT", rs.getLong("invoice_count"), rs.getLong("payment_count"),
                            rs.getLong("active_line_count"), integer(rs, "invoice_amount"), integer(rs, "allocated_amount"),
                            integer(rs, "outstanding"), integer(rs, "overdue"), integer(rs, "payment_amount"), integer(rs, "unallocated"));
                }, tenant, tenant, tenant, cutoff);
    }

    private Command reserve(UUID tenant, String operation, String key, Object input) {
        UUID event = UUID.randomUUID();
        String hash = hash(input);
        int inserted = jdbc.update("""
                INSERT INTO receivable_allocation_command (tenant_id,operation,request_key,request_hash,result_audit_id)
                VALUES (?,?,?,?,?) ON CONFLICT (tenant_id,operation,request_key) DO NOTHING
                """, tenant, operation, key, hash, event);
        if (inserted == 1) return new Command(event, null);
        return jdbc.queryForObject("""
                SELECT c.request_hash,a.result_json::text FROM receivable_allocation_command c
                JOIN receivable_allocation_audit a ON a.tenant_id=c.tenant_id AND a.id=c.result_audit_id
                WHERE c.tenant_id=? AND c.operation=? AND c.request_key=?
                """, (rs, row) -> {
                    if (!hash.equals(rs.getString("request_hash"))) throw AllocationException.conflict();
                    return new Command(null, mapper.readValue(rs.getString("result_json"), AllocationResponse.class));
                }, tenant, operation, key);
    }
    private void lockAndValidate(UUID tenant, List<AllocationInput.Line> lines) {
        var parents = lockParents(tenant, lines);
        Map<UUID, Long> payments = new HashMap<>(), invoices = new HashMap<>();
        for (var line : lines) {
            payments.merge(line.paymentId(), line.amount(), Math::addExact);
            invoices.merge(line.invoiceId(), line.amount(), Math::addExact);
        }
        for (var entry : payments.entrySet())
            checkAvailable(tenant, entry.getKey(), entry.getValue(), parents.payments().get(entry.getKey()), true);
        for (var entry : invoices.entrySet())
            checkAvailable(tenant, entry.getKey(), entry.getValue(), parents.invoices().get(entry.getKey()), false);
    }
    private Parents lockParents(UUID tenant, List<AllocationInput.Line> lines) {
        Map<UUID, Long> payments = new HashMap<>(), invoices = new HashMap<>();
        for (UUID id : lines.stream().map(AllocationInput.Line::paymentId).distinct().sorted(AllocationInput.UUID_ORDER).toList()) {
            payments.put(id, one(jdbc.query("SELECT amount FROM receivable_payment WHERE tenant_id=? AND id=? FOR UPDATE",
                    (rs, row) -> rs.getLong("amount"), tenant, id)));
        }
        for (UUID id : lines.stream().map(AllocationInput.Line::invoiceId).distinct().sorted(AllocationInput.UUID_ORDER).toList()) {
            invoices.put(id, one(jdbc.query("SELECT amount FROM receivable_invoice WHERE tenant_id=? AND id=? FOR UPDATE",
                    (rs, row) -> rs.getLong("amount"), tenant, id)));
        }
        return new Parents(payments, invoices);
    }
    private void checkAvailable(UUID tenant, UUID id, long requested, long original, boolean payment) {
        String sql = payment ? """
                SELECT coalesce(sum(l.amount),0) FROM receivable_allocation_line l
                LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                WHERE l.tenant_id=? AND l.payment_id=? AND c.id IS NULL
                """ : """
                SELECT coalesce(sum(l.amount),0) FROM receivable_allocation_line l
                LEFT JOIN receivable_allocation_cancellation c ON c.tenant_id=l.tenant_id AND c.batch_id=l.batch_id
                WHERE l.tenant_id=? AND l.invoice_id=? AND c.id IS NULL
                """;
        BigInteger allocated = jdbc.queryForObject(sql, java.math.BigDecimal.class, tenant, id).toBigIntegerExact();
        if (BigInteger.valueOf(requested).compareTo(remainder(BigInteger.valueOf(original), allocated)) > 0)
            throw AllocationException.exceeded();
    }
    private AllocationResponse audit(UUID tenant, UUID batch, UUID eventId, String eventType, UUID cancellationId,
            String reason, UUID actor, OffsetDateTime time) {
        AllocationResponse current = detail(tenant, batch);
        var events = new ArrayList<>(current.audit());
        events.add(new AuditResponse(eventId, eventType, current.version(), cancellationId, reason, actor, time.toInstant()));
        var result = new AllocationResponse(batch, tenant, current.version(), current.status(), current.lines(), current.reason(),
                current.createdBy(), current.createdAt(), current.cancellation(), List.copyOf(events));
        jdbc.update("""
                INSERT INTO receivable_allocation_audit
                (id,tenant_id,batch_id,event_type,version,cancellation_id,reason,created_by,created_at,result_json)
                VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))
                """, eventId, tenant, batch, eventType, result.version(), cancellationId, reason, actor, time, mapper.writeValueAsString(result));
        return result;
    }
    private AllocationResponse detail(UUID tenant, UUID batch) {
        Batch row = requireBatch(tenant, batch);
        CancellationResponse cancelled = cancellation(tenant, batch);
        var events = jdbc.query("""
                SELECT id,event_type,version,cancellation_id,reason,created_by,created_at FROM receivable_allocation_audit
                WHERE tenant_id=? AND batch_id=? ORDER BY version
                """, (rs, number) -> new AuditResponse(uuid(rs, "id"), rs.getString("event_type"), rs.getInt("version"),
                uuid(rs, "cancellation_id"), rs.getString("reason"), uuid(rs, "created_by"), timestamp(rs)), tenant, batch);
        return new AllocationResponse(batch, tenant, cancelled == null ? 1 : 2, cancelled == null ? "CONFIRMED" : "CANCELLED",
                lines(tenant, batch), row.reason(), row.actor(), row.createdAt(), cancelled, events);
    }
    private Batch requireBatch(UUID tenant, UUID batch) {
        return one(jdbc.query("SELECT reason,created_by,created_at FROM receivable_allocation_batch WHERE tenant_id=? AND id=?",
                (rs, row) -> new Batch(rs.getString("reason"), uuid(rs, "created_by"), timestamp(rs)), tenant, batch));
    }
    private List<LineResponse> lines(UUID tenant, UUID batch) {
        return jdbc.query("SELECT id,payment_id,invoice_id,amount FROM receivable_allocation_line WHERE tenant_id=? AND batch_id=? ORDER BY line_number",
                (rs, row) -> new LineResponse(uuid(rs, "id"), uuid(rs, "payment_id"), uuid(rs, "invoice_id"), Long.toString(rs.getLong("amount"))), tenant, batch);
    }
    private CancellationResponse cancellation(UUID tenant, UUID batch) {
        var rows = jdbc.query("SELECT id,reason,created_by,created_at FROM receivable_allocation_cancellation WHERE tenant_id=? AND batch_id=?",
                (rs, row) -> new CancellationResponse(uuid(rs, "id"), rs.getString("reason"), uuid(rs, "created_by"), timestamp(rs)), tenant, batch);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private String hash(Object input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC); }
    private static BigInteger remainder(BigInteger original, BigInteger allocated) {
        if (allocated.signum() < 0 || allocated.compareTo(original) > 0) throw AllocationException.invariant();
        return original.subtract(allocated);
    }
    private static String integer(ResultSet rs, String name) throws SQLException { return rs.getBigDecimal(name).toBigIntegerExact().toString(); }
    private static UUID uuid(ResultSet rs, String name) throws SQLException { return rs.getObject(name, UUID.class); }
    private static Instant timestamp(ResultSet rs) throws SQLException { return rs.getObject("created_at", OffsetDateTime.class).toInstant(); }
    private static <T> T one(List<T> rows) { if (rows.isEmpty()) throw AllocationException.missing(); return rows.getFirst(); }
    private record Command(UUID auditId, AllocationResponse replay) {}
    private record Parents(Map<UUID, Long> payments, Map<UUID, Long> invoices) {}
    private record Batch(String reason, UUID actor, Instant createdAt) {}
    public record LineResponse(UUID id, UUID paymentId, UUID invoiceId, String amount) {}
    public record AuditResponse(UUID id, String eventType, int version, UUID cancellationId, String reason, UUID createdBy, Instant createdAt) {}
    public record CancellationResponse(UUID id, String reason, UUID createdBy, Instant createdAt) {}
    public record AllocationResponse(UUID id, UUID tenantId, int version, String status, List<LineResponse> lines, String reason,
            UUID createdBy, Instant createdAt, CancellationResponse cancellation, List<AuditResponse> audit) {}
    public record Page(List<AllocationResponse> items, int page, int size, boolean hasNext) {}
    public record InvoiceBalance(UUID invoiceId, UUID tenantId, String amount, String allocatedAmount, String outstandingAmount,
            String overdueAmount, LocalDate dueDate, LocalDate asOf, String status) {}
    public record PaymentBalance(UUID paymentId, UUID tenantId, String amount, String allocatedAmount, String unallocatedAmount, String status) {}
    public record Summary(UUID tenantId, LocalDate asOf, String allocationState, long invoiceCount, long paymentCount,
            long activeAllocationLineCount, String invoiceAmount, String allocatedAmount, String outstandingAmount,
            String overdueAmount, String paymentAmount, String unallocatedAmount) {}
}
