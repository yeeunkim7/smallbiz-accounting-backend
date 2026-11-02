package com.smallbiz.reconciliation.receivables;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smallbiz.reconciliation.account.AccountException;
import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.aireview.ai.AiCompletionClient;
import com.smallbiz.reconciliation.aireview.query.ReadOnlyReviewQueries;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"AI_API_KEY=synthetic-key-present", "app.ai.enabled=true"})
@AutoConfigureMockMvc // Keep the real bearer/security filters.
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class ReceivablesRegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired ReceivablesService service;
    @MockitoBean AiCompletionClient ai;
    @MockitoBean ReadOnlyReviewQueries queries;
    private final List<Owner> owners = new ArrayList<>();

    @BeforeEach void prepare() { SecurityContextHolder.clearContext(); reset(ai, queries); }
    @AfterEach void cleanupOnlyThisTestsRows() {
        SecurityContextHolder.clearContext();
        try {
            verifyNoInteractions(ai, queries);
        } finally {
            for (Owner owner : owners) {
                jdbc.update("DELETE FROM receivable_registration_request WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM receivable_invoice WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM receivable_payment WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM receivable_counterparty WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_session WHERE account_id=?", owner.account());
                jdbc.update("DELETE FROM app_membership WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_tenant WHERE id=?", owner.tenant());
                jdbc.update("DELETE FROM app_account WHERE id=?", owner.account());
            }
        }
    }

    @Test void manualRecordsKeepExactMoneyDatesProvenanceAndOriginalDetail() throws Exception {
        Owner owner = owner();
        var counterparty = created(owner, "counterparties", "c-1", Map.of("code", " shop_1 ", "name", " 고객 "));
        assertThat(counterparty.path("code").asText()).isEqualTo("SHOP_1");
        assertThat(counterparty.path("name").asText()).isEqualTo("고객");
        assertThat(counterparty.path("createdBy").asText()).isEqualTo(owner.account().toString());
        assertThat(counterparty.path("sourceKind").asText()).isEqualTo("MANUAL");
        assertThat(counterparty.path("createdAt").asText()).endsWith("Z");
        var invoice = created(owner, "invoices", "i-1", invoice(counterparty, " INV-1 ", "1000000000000", "2024-02-29", "9999-12-31"));
        assertThat(invoice.path("amount").isString()).isTrue();
        assertThat(invoice.path("amount").asText()).isEqualTo("1000000000000");
        assertThat(invoice.path("currency").asText()).isEqualTo("KRW");
        assertThat(invoice.path("invoiceNumber").asText()).isEqualTo("INV-1");
        assertThat(invoice.toString()).doesNotContain("outstanding", "paid", "matched", "balance");
        // A future booked date is accepted as the user's stated date, without asserting bank verification.
        var payment = created(owner, "payments", "p-1", Map.of("amount", "1", "bookedDate", "9999-12-31", "payerName", " Kim ", "note", " memo "));
        assertThat(payment.path("amount").asText()).isEqualTo("1");
        assertThat(payment.path("payerName").asText()).isEqualTo("Kim");
        for (var row : List.of(Map.entry("counterparties", counterparty), Map.entry("invoices", invoice), Map.entry("payments", payment))) {
            JsonNode detail = read(owner, row.getKey() + "/" + row.getValue().path("id").asText());
            assertThat(detail).isEqualTo(row.getValue());
            assertThat(detail.path("tenantId").asText()).isEqualTo(owner.tenant().toString());
            assertThat(detail.path("sourceKind").asText()).isEqualTo("MANUAL");
        }
    }

    @Test void normalizedIdempotentReplaysReturnOriginalAndKeysAreScopedByOperationAndTenant() throws Exception {
        Owner a = owner(), b = owner();
        var cp = created(a, "counterparties", "shared-key", Map.of("code", " shop ", "name", " Shop "));
        var replay = created(a, "counterparties", "shared-key", Map.of("code", "SHOP", "name", "Shop"));
        assertThat(replay).isEqualTo(cp);
        postRecord(a, "counterparties", "shared-key", Map.of("code", "SHOP", "name", "Changed"), 409)
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        var foreign = created(b, "counterparties", "shared-key", Map.of("code", "SHOP", "name", "Other"));
        assertThat(foreign.path("id").asText()).isNotEqualTo(cp.path("id").asText());
        var inv = created(a, "invoices", "shared-key", invoice(cp, "INV", "500", "2026-01-01", "2026-01-01"));
        assertThat(created(a, "invoices", "shared-key", invoice(cp, " INV ", "500", "2026-01-01", "2026-01-01"))).isEqualTo(inv);
        Map<String, Object> payment = Map.of("amount", "500", "bookedDate", "2026-01-01");
        var pay = created(a, "payments", "shared-key", payment);
        assertThat(created(a, "payments", "shared-key", Map.of("amount", "500", "bookedDate", "2026-01-01", "note", " "))).isEqualTo(pay);
        postRecord(a, "payments", "shared-key", Map.of("amount", "501", "bookedDate", "2026-01-01"), 409);
        // Separate explicit keys preserve legitimate same-date/same-amount deposits.
        assertThat(created(a, "payments", "another-key", payment).path("id").asText()).isNotEqualTo(pay.path("id").asText());
        assertThat(count("receivable_payment", a)).isEqualTo(2);
        assertThat(count("receivable_registration_request", a)).isEqualTo(4);
    }

    @Test void duplicateCodesAndInvoiceNumbersAreTenantLocalAndFailuresLeaveNoRequestRows() throws Exception {
        Owner a = owner(), b = owner();
        var cp = created(a, "counterparties", "cp", Map.of("code", "SAME", "name", "One"));
        postRecord(a, "counterparties", "other-cp", Map.of("code", "same", "name", "Two"), 409)
                .andExpect(jsonPath("$.code").value("RECEIVABLE_DUPLICATE"));
        var bcp = created(b, "counterparties", "cp", Map.of("code", "SAME", "name", "Other tenant"));
        created(a, "invoices", "i", invoice(cp, "SAME", "1", "0001-01-01", "0001-01-01"));
        postRecord(a, "invoices", "other-i", invoice(cp, "SAME", "2", "2026-01-01", "2026-01-01"), 409);
        created(b, "invoices", "i", invoice(bcp, "SAME", "2", "2026-01-01", "2026-01-01"));
        assertThat(count("receivable_registration_request", a)).isEqualTo(2);
        assertThat(count("receivable_invoice", a)).isEqualTo(1);
    }

    @Test void allRoutesRejectAnonymousAndForeignTenantsAndConcealForeignResourceIds() throws Exception {
        Owner a = owner(), b = owner();
        var cp = created(a, "counterparties", "c", Map.of("code", "A", "name", "A"));
        var inv = created(a, "invoices", "i", invoice(cp, "A", "1", "2026-01-01", "2026-01-01"));
        var pay = created(a, "payments", "p", Map.of("amount", "1", "bookedDate", "2026-01-01"));
        for (var item : List.of(Map.entry("counterparties", cp), Map.entry("invoices", inv), Map.entry("payments", pay))) {
            String kind = item.getKey(), id = item.getValue().path("id").asText();
            mvc.perform(get(path(a, kind))).andExpect(status().isUnauthorized());
            mvc.perform(get(path(a, kind + "/" + id))).andExpect(status().isUnauthorized());
            mvc.perform(post(path(a, kind)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path(a, kind)).header("Authorization", b.bearer())).andExpect(status().isForbidden());
            mvc.perform(get(path(a, kind + "/" + id)).header("Authorization", b.bearer())).andExpect(status().isForbidden());
            // A valid foreign tenant is denied before body validation or uniqueness checks.
            mvc.perform(post(path(a, kind)).header("Authorization", b.bearer()).header("Idempotency-Key", "c")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            String foreign = mvc.perform(get(path(b, kind + "/" + id)).header("Authorization", b.bearer()))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String unknown = mvc.perform(get(path(b, kind + "/" + UUID.randomUUID())).header("Authorization", b.bearer()))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(foreign).isEqualTo(unknown);
            assertThat(read(b, kind).path("items")).isEmpty();
        }
        postRecord(b, "invoices", "cross-fk", invoice(cp, "A", "1", "2026-01-01", "2026-01-01"), 404);
        mvc.perform(get(path(b, "invoices")).header("Authorization", b.bearer()).param("counterpartyId", cp.path("id").asText()))
                .andExpect(status().isNotFound());
        assertThat(count("receivable_registration_request", b)).isZero();
    }

    @Test void strictMoneyRejectsNumericCoercionFractionsSignsWhitespaceOverflowAndMissingValues() throws Exception {
        Owner owner = owner();
        var cp = created(owner, "counterparties", "c", Map.of("code", "A", "name", "A"));
        List<Object> invalid = List.of(1, 1.9, true, "0", "-1", "+1", "01", "1.0", "1e3", " 1", "1 ", "1000000000001", "9223372036854775808", "１", "");
        int index = 0;
        for (Object amount : invalid) {
            postRecord(owner, "payments", "p" + index, Map.of("amount", amount, "bookedDate", "2026-01-01"), 400);
            var invoice = new HashMap<>(invoice(cp, "I" + index, "1", "2026-01-01", "2026-01-01"));
            invoice.put("amount", amount);
            postRecord(owner, "invoices", "i" + index++, invoice, 400);
        }
        postRecord(owner, "payments", "missing", Map.of("bookedDate", "2026-01-01"), 400);
        assertThat(count("receivable_payment", owner)).isZero();
        assertThat(count("receivable_invoice", owner)).isZero();
        assertThat(count("receivable_registration_request", owner)).isEqualTo(1);
    }

    @Test void datesRequireExactRealCalendarDatesAndDueDateCannotPrecedeIssueDate() throws Exception {
        Owner owner = owner();
        var cp = created(owner, "counterparties", "c", Map.of("code", "A", "name", "A"));
        int index = 0;
        for (Object date : List.of("2026-02-29", "2026-04-31", "2026-1-01", "2026-01-1", "0000-01-01", "+10000-01-01", "2026-01-01T00:00:00Z", " 2026-01-01", 20260101)) {
            postRecord(owner, "payments", "p" + index, Map.of("amount", "1", "bookedDate", date), 400);
            var input = new HashMap<>(invoice(cp, "I" + index, "1", "2026-01-01", "9999-12-31"));
            input.put("issueDate", date);
            postRecord(owner, "invoices", "i" + index++, input, 400);
        }
        postRecord(owner, "invoices", "backwards", invoice(cp, "I", "1", "2026-01-02", "2026-01-01"), 400);
        created(owner, "payments", "ancient", Map.of("amount", "1", "bookedDate", "0001-01-01"));
        assertThat(count("receivable_invoice", owner)).isZero();
    }

    @Test void fieldAllowlistsTextLimitsAndIdempotencyHeadersAreValidatedWithoutPrivilegeInjection() throws Exception {
        Owner owner = owner();
        for (String code : List.of("a b", "한글", "a.b", "ß", "ſ", "x".repeat(33), ""))
            postRecord(owner, "counterparties", "invalid-code", Map.of("code", code, "name", "A"), 400);
        postRecord(owner, "counterparties", "invalid-name", Map.of("code", "A", "name", "x".repeat(101)), 400);
        for (String field : List.of("tenantId", "createdBy", "sourceKind", "plan", "outstandingAmount")) {
            var body = new HashMap<String, Object>(Map.of("code", "A", "name", "A")); body.put(field, "injected");
            postRecord(owner, "counterparties", "privilege", body, 400);
        }
        for (String key : List.of("", "has space", "x".repeat(101), "a,b"))
            postRecord(owner, "payments", key, Map.of("amount", "1", "bookedDate", "2026-01-01"), 400);
        mvc.perform(post(path(owner, "payments")).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"1\",\"bookedDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        postRecord(owner, "payments", "long-payer", Map.of("amount", "1", "bookedDate", "2026-01-01", "payerName", "x".repeat(101)), 400);
        postRecord(owner, "payments", "long-note", Map.of("amount", "1", "bookedDate", "2026-01-01", "note", "x".repeat(1001)), 400);
        assertThat(count("receivable_registration_request", owner)).isZero();
    }

    @Test void listsAreBoundedStableAndInvoiceCounterpartyFilterIsTenantChecked() throws Exception {
        Owner owner = owner();
        var a = created(owner, "counterparties", "a", Map.of("code", "A", "name", "A"));
        var b = created(owner, "counterparties", "b", Map.of("code", "B", "name", "B"));
        created(owner, "invoices", "ia", invoice(a, "A", "1", "2026-01-01", "2026-01-01"));
        created(owner, "invoices", "ib", invoice(b, "B", "1", "2026-01-01", "2026-01-01"));
        created(owner, "payments", "pa", Map.of("amount", "1", "bookedDate", "2026-01-01"));
        created(owner, "payments", "pb", Map.of("amount", "1", "bookedDate", "2026-01-01"));
        for (String kind : List.of("counterparties", "invoices", "payments")) {
            // Force equal timestamps to exercise the ID tie-breaker in every list.
            String table = switch (kind) {
                case "counterparties" -> "receivable_counterparty";
                case "invoices" -> "receivable_invoice";
                default -> "receivable_payment";
            };
            jdbc.update("UPDATE " + table + " SET created_at=TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE tenant_id=?", owner.tenant());
            var all = read(owner, kind);
            assertThat(all.path("size").asInt()).isEqualTo(20);
            assertThat(all.path("items").size()).isEqualTo(2);
            var first = read(owner, kind + "?size=1&page=0");
            var second = read(owner, kind + "?size=1&page=1");
            assertThat(first.path("hasNext").asBoolean()).isTrue();
            assertThat(second.path("hasNext").asBoolean()).isFalse();
            assertThat(first.path("items").get(0)).isEqualTo(all.path("items").get(0));
            assertThat(second.path("items").get(0)).isEqualTo(all.path("items").get(1));
            for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=2147483648", "?size=x"))
                mvc.perform(get(path(owner, kind + query)).header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
            assertThat(read(owner, kind + "?page=2147483647&size=100").path("items")).isEmpty();
        }
        var filtered = read(owner, "invoices?counterpartyId=" + a.path("id").asText());
        assertThat(filtered.path("items").size()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("invoiceNumber").asText()).isEqualTo("A");
    }

    @Test void concurrentIdenticalPaymentRequestsReturnOneCommittedResult() throws Exception {
        Owner owner = owner();
        var barrier = new CyclicBarrier(2);
        Callable<MvcResult> request = () -> {
            barrier.await();
            return postRecord(owner, "payments", "race", Map.of("amount", "500", "bookedDate", "2026-01-01"), 201).andReturn();
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(request, request));
            var a = mapper.readTree(results.get(0).get().getResponse().getContentAsString());
            var b = mapper.readTree(results.get(1).get().getResponse().getContentAsString());
            assertThat(a).isEqualTo(b);
        }
        assertThat(count("receivable_payment", owner)).isEqualTo(1);
        assertThat(count("receivable_registration_request", owner)).isEqualTo(1);
    }

    @Test void concurrentDifferentPayloadsForSameKeyReturnConflictWithoutAnotherPayment() throws Exception {
        Owner owner = owner();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> requests = List.of("1", "2").stream().<Callable<Integer>>map(amount -> () -> {
                barrier.await();
                return mvc.perform(post(path(owner, "payments")).header("Authorization", owner.bearer()).header("Idempotency-Key", "race")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("amount", amount, "bookedDate", "2026-01-01"))))
                        .andReturn().getResponse().getStatus();
            }).toList();
            var results = pool.invokeAll(requests);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("receivable_payment", owner)).isEqualTo(1);
        assertThat(count("receivable_registration_request", owner)).isEqualTo(1);
    }

    @Test void concurrentInvoiceNumberCollisionRollsBackLosingRequestAndCanRetryWithNewNumber() throws Exception {
        Owner owner = owner();
        var cp = created(owner, "counterparties", "c", Map.of("code", "A", "name", "A"));
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> requests = List.of("i1", "i2").stream().<Callable<Integer>>map(key -> () -> {
                barrier.await();
                return mvc.perform(post(path(owner, "invoices")).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(invoice(cp, "SAME", "1", "2026-01-01", "2026-01-01"))))
                        .andReturn().getResponse().getStatus();
            }).toList();
            var results = pool.invokeAll(requests);
            int first = results.get(0).get(), second = results.get(1).get();
            assertThat(List.of(first, second)).containsExactlyInAnyOrder(201, 409);
            created(owner, "invoices", first == 409 ? "i1" : "i2", invoice(cp, "NEW", "1", "2026-01-01", "2026-01-01"));
        }
        assertThat(count("receivable_invoice", owner)).isEqualTo(2);
        assertThat(count("receivable_registration_request", owner)).isEqualTo(3);
    }

    @Test void failedStorageRollsBackIdempotencyReservationAndAllowsRetry() throws Exception {
        Owner owner = owner();
        jdbc.execute("ALTER TABLE receivable_payment ADD CONSTRAINT ck_test_reject_payment CHECK (false) NOT VALID");
        try {
            postRecord(owner, "payments", "retry", Map.of("amount", "1", "bookedDate", "2026-01-01"), 503)
                    .andExpect(jsonPath("$.code").value("RECEIVABLE_STORAGE_UNAVAILABLE"));
            assertThat(count("receivable_payment", owner)).isZero();
            assertThat(count("receivable_registration_request", owner)).isZero();
        } finally { jdbc.execute("ALTER TABLE receivable_payment DROP CONSTRAINT ck_test_reject_payment"); }
        created(owner, "payments", "retry", Map.of("amount", "1", "bookedDate", "2026-01-01"));
    }

    @Test void databaseRejectsCrossTenantCounterpartyEvenWhenApplicationIsBypassed() throws Exception {
        Owner a = owner(), b = owner();
        var cp = created(a, "counterparties", "c", Map.of("code", "A", "name", "A"));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO receivable_invoice (id,tenant_id,counterparty_id,invoice_number,amount,issue_date,due_date,created_by,created_at)
                VALUES (?,?,?,'CROSS',1,DATE '2026-01-01',DATE '2026-01-01',?,CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), b.tenant(), UUID.fromString(cp.path("id").asText()), b.account()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("receivable_invoice", b)).isZero();
    }

    @Test void directServiceCallsRecheckMembershipAndRejectRevokedSessions() throws Exception {
        Owner a = owner(), b = owner();
        assertThatThrownBy(() -> service.listPayments(a.tenant(), 0, 20)).isInstanceOf(AccountException.class);
        var principal = auth.authenticate(a.token());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        assertThatThrownBy(() -> service.createPayment(b.tenant(), "x", Map.of("amount", "1", "bookedDate", "2026-01-01")))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(403));
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE account_id=?", a.account());
        for (Runnable action : List.<Runnable>of(
                () -> service.listCounterparties(a.tenant(), 0, 20),
                () -> service.listInvoices(a.tenant(), 0, 20, null),
                () -> service.listPayments(a.tenant(), 0, 20),
                () -> service.getCounterparty(a.tenant(), UUID.randomUUID()),
                () -> service.getInvoice(a.tenant(), UUID.randomUUID()),
                () -> service.getPayment(a.tenant(), UUID.randomUUID()),
                () -> service.createCounterparty(a.tenant(), "x", Map.of()),
                () -> service.createInvoice(a.tenant(), "x", Map.of()),
                () -> service.createPayment(a.tenant(), "x", Map.of()))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(401));
        }
        assertThat(count("receivable_registration_request", a)).isZero();
    }

    private Owner owner() throws Exception {
        var signup = mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("login", "reg-" + UUID.randomUUID(), "password", "password-123456", "tenantName", "Synthetic shop"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = signup.path("accessToken").asText();
        var me = mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var owner = new Owner(UUID.fromString(me.path("accountId").asText()), UUID.fromString(me.path("tenants").get(0).path("id").asText()), token);
        owners.add(owner);
        return owner;
    }
    private JsonNode created(Owner owner, String kind, String key, Map<String, Object> body) throws Exception {
        return mapper.readTree(postRecord(owner, kind, key, body, 201).andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.ResultActions postRecord(Owner owner, String kind, String key, Map<String, Object> body, int status) throws Exception {
        return mvc.perform(post(path(owner, kind)).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status)).andExpect(header().string("Cache-Control", "no-store"));
    }
    private JsonNode read(Owner owner, String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(path(owner, suffix)).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private int count(String table, Owner owner) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id=?", Integer.class, owner.tenant());
    }
    private static Map<String, Object> invoice(JsonNode cp, String number, String amount, String issue, String due) {
        return Map.of("counterpartyId", cp.path("id").asText(), "invoiceNumber", number, "amount", amount, "issueDate", issue, "dueDate", due);
    }
    private static String path(Owner owner, String suffix) { return "/api/v1/tenants/" + owner.tenant() + "/" + suffix; }
    private record Owner(UUID account, UUID tenant, String token) { String bearer() { return "Bearer " + token; } }
}
