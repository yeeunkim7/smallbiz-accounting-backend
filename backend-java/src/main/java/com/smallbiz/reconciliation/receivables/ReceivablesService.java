package com.smallbiz.reconciliation.receivables;

import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.account.TenantAccess;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReceivablesService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenants;
    private final AuthService auth;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public ReceivablesService(JdbcTemplate jdbc, TenantAccess tenants, AuthService auth, Clock clock,
            ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.tenants = tenants; this.auth = auth; this.clock = clock; this.mapper = mapper;
        transactions = new TransactionTemplate(transactionManager);
    }

    public CounterpartyResponse createCounterparty(UUID tenant, String key, Map<String, Object> body) {
        return create(tenant, key, () -> RegistrationInput.counterparty(body), input -> {
            UUID id = UUID.randomUUID();
            return replayOrCreate(tenant, key, Operation.COUNTERPARTY, input, id, resultId -> {
                jdbc.update("""
                        INSERT INTO receivable_counterparty (id,tenant_id,code,name,created_by,created_at)
                        VALUES (?,?,?,?,?,?)
                        """, resultId, tenant, input.code(), input.name(), auth.requireActiveSession().accountId(), now());
                return counterparty(tenant, resultId);
            }, resultId -> counterparty(tenant, resultId));
        });
    }

    public InvoiceResponse createInvoice(UUID tenant, String key, Map<String, Object> body) {
        return create(tenant, key, () -> RegistrationInput.invoice(body), input -> {
            // Tenant-filtered lookup precedes all uniqueness/idempotency lookups.
            counterparty(tenant, input.counterpartyId());
            return replayOrCreate(tenant, key, Operation.INVOICE, input, UUID.randomUUID(), resultId -> {
                jdbc.update("""
                        INSERT INTO receivable_invoice
                        (id,tenant_id,counterparty_id,invoice_number,amount,issue_date,due_date,created_by,created_at)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """, resultId, tenant, input.counterpartyId(), input.invoiceNumber(), input.amount(),
                        input.issueDate(), input.dueDate(), auth.requireActiveSession().accountId(), now());
                return invoice(tenant, resultId);
            }, resultId -> invoice(tenant, resultId));
        });
    }

    public PaymentResponse createPayment(UUID tenant, String key, Map<String, Object> body) {
        return create(tenant, key, () -> RegistrationInput.payment(body), input ->
                replayOrCreate(tenant, key, Operation.PAYMENT, input, UUID.randomUUID(), resultId -> {
                    jdbc.update("""
                            INSERT INTO receivable_payment (id,tenant_id,amount,booked_date,payer_name,note,created_by,created_at)
                            VALUES (?,?,?,?,?,?,?,?)
                            """, resultId, tenant, input.amount(), input.bookedDate(), input.payerName(), input.note(),
                            auth.requireActiveSession().accountId(), now());
                    return payment(tenant, resultId);
                }, resultId -> payment(tenant, resultId)));
    }

    public CounterpartyResponse getCounterparty(UUID tenant, UUID id) {
        tenants.require(tenant);
        return counterparty(tenant, id);
    }
    public InvoiceResponse getInvoice(UUID tenant, UUID id) {
        tenants.require(tenant);
        return invoice(tenant, id);
    }
    public PaymentResponse getPayment(UUID tenant, UUID id) {
        tenants.require(tenant);
        return payment(tenant, id);
    }
    public Page<CounterpartyResponse> listCounterparties(UUID tenant, int page, int size) {
        tenants.require(tenant);
        RegistrationInput.page(page, size);
        List<CounterpartyResponse> rows = jdbc.query("""
                SELECT * FROM receivable_counterparty WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, COUNTERPARTY, tenant, size + 1, offset(page, size));
        return page(rows, page, size);
    }
    public Page<InvoiceResponse> listInvoices(UUID tenant, int page, int size, UUID counterpartyId) {
        tenants.require(tenant);
        RegistrationInput.page(page, size);
        List<InvoiceResponse> rows;
        if (counterpartyId == null) {
            rows = jdbc.query("""
                    SELECT * FROM receivable_invoice WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                    """, INVOICE, tenant, size + 1, offset(page, size));
        } else {
            counterparty(tenant, counterpartyId);
            rows = jdbc.query("""
                    SELECT * FROM receivable_invoice WHERE tenant_id=? AND counterparty_id=?
                    ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                    """, INVOICE, tenant, counterpartyId, size + 1, offset(page, size));
        }
        return page(rows, page, size);
    }
    public Page<PaymentResponse> listPayments(UUID tenant, int page, int size) {
        tenants.require(tenant);
        RegistrationInput.page(page, size);
        List<PaymentResponse> rows = jdbc.query("""
                SELECT * FROM receivable_payment WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, PAYMENT, tenant, size + 1, offset(page, size));
        return page(rows, page, size);
    }

    private <I, R> R create(UUID tenant, String key, java.util.function.Supplier<I> parse,
            Function<I, R> save) {
        try {
            return transactions.execute(status -> {
                tenants.require(tenant);
                RegistrationInput.key(key);
                return save.apply(parse.get());
            });
        } catch (DuplicateKeyException ex) {
            // The transaction is already rolled back. Never expose SQL, constraint names or other tenant data.
            throw ReceivablesException.duplicate();
        }
    }

    private <R> R replayOrCreate(UUID tenant, String key, Operation operation, Object input, UUID id,
            Function<UUID, R> save, Function<UUID, R> read) {
        String hash = hash(input);
        // ON CONFLICT waits for the concurrent creator's transaction. After it commits, the following
        // READ COMMITTED statement sees its result. A rollback lets this request acquire the key.
        int inserted = jdbc.update("""
                INSERT INTO receivable_registration_request
                (tenant_id,operation,request_key,request_hash,counterparty_id,invoice_id,payment_id)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (tenant_id,operation,request_key) DO NOTHING
                """, tenant, operation.name(), key, hash,
                operation == Operation.COUNTERPARTY ? id : null,
                operation == Operation.INVOICE ? id : null,
                operation == Operation.PAYMENT ? id : null);
        if (inserted == 1) return save.apply(id);
        StoredRequest previous = jdbc.queryForObject("""
                SELECT request_hash,coalesce(counterparty_id,invoice_id,payment_id) AS result_id
                FROM receivable_registration_request WHERE tenant_id=? AND operation=? AND request_key=?
                """, (rs, row) -> new StoredRequest(rs.getString("request_hash"), rs.getObject("result_id", UUID.class)),
                tenant, operation.name(), key);
        if (previous == null || !hash.equals(previous.hash())) throw ReceivablesException.keyConflict();
        return read.apply(previous.id());
    }

    private CounterpartyResponse counterparty(UUID tenant, UUID id) {
        return one(jdbc.query("SELECT * FROM receivable_counterparty WHERE tenant_id=? AND id=?", COUNTERPARTY, tenant, id));
    }
    private InvoiceResponse invoice(UUID tenant, UUID id) {
        return one(jdbc.query("SELECT * FROM receivable_invoice WHERE tenant_id=? AND id=?", INVOICE, tenant, id));
    }
    private PaymentResponse payment(UUID tenant, UUID id) {
        return one(jdbc.query("SELECT * FROM receivable_payment WHERE tenant_id=? AND id=?", PAYMENT, tenant, id));
    }
    private static <T> T one(List<T> rows) {
        if (rows.isEmpty()) throw ReceivablesException.missing();
        return rows.getFirst();
    }
    private static long offset(int page, int size) { return (long) page * size; }
    private static <T> Page<T> page(List<T> rows, int page, int size) {
        return new Page<>(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, rows.size() > size);
    }
    private OffsetDateTime now() { return OffsetDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC); }
    private String hash(Object input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(input).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private enum Operation { COUNTERPARTY, INVOICE, PAYMENT }
    private record StoredRequest(String hash, UUID id) {}
    public record Page<T>(List<T> items, int page, int size, boolean hasNext) {}
    public record CounterpartyResponse(UUID id, UUID tenantId, String code, String name,
            String sourceKind, UUID createdBy, Instant createdAt) {}
    public record InvoiceResponse(UUID id, UUID tenantId, UUID counterpartyId, String invoiceNumber, String amount,
            String currency, LocalDate issueDate, LocalDate dueDate, String sourceKind, UUID createdBy, Instant createdAt) {}
    public record PaymentResponse(UUID id, UUID tenantId, String amount, String currency, LocalDate bookedDate,
            String payerName, String note, String sourceKind, UUID createdBy, Instant createdAt) {}

    private static UUID uuid(ResultSet rs, String name) throws SQLException { return rs.getObject(name, UUID.class); }
    private static Instant createdAt(ResultSet rs) throws SQLException {
        return rs.getObject("created_at", OffsetDateTime.class).toInstant();
    }
    private static final RowMapper<CounterpartyResponse> COUNTERPARTY = (rs, row) -> new CounterpartyResponse(
            uuid(rs, "id"), uuid(rs, "tenant_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("source_kind"), uuid(rs, "created_by"), createdAt(rs));
    private static final RowMapper<InvoiceResponse> INVOICE = (rs, row) -> new InvoiceResponse(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "counterparty_id"), rs.getString("invoice_number"),
            Long.toString(rs.getLong("amount")), rs.getString("currency"), rs.getObject("issue_date", LocalDate.class),
            rs.getObject("due_date", LocalDate.class), rs.getString("source_kind"), uuid(rs, "created_by"), createdAt(rs));
    private static final RowMapper<PaymentResponse> PAYMENT = (rs, row) -> new PaymentResponse(
            uuid(rs, "id"), uuid(rs, "tenant_id"), Long.toString(rs.getLong("amount")), rs.getString("currency"),
            rs.getObject("booked_date", LocalDate.class), rs.getString("payer_name"), rs.getString("note"),
            rs.getString("source_kind"), uuid(rs, "created_by"), createdAt(rs));
}
