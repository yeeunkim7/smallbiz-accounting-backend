package com.smallbiz.reconciliation.receivables.allocation;

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
import java.util.concurrent.TimeUnit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"AI_API_KEY=synthetic-key-present", "app.ai.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class AllocationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AllocationService service;
    @MockitoBean AiCompletionClient ai;
    @MockitoBean ReadOnlyReviewQueries queries;
    private final List<Owner> owners = new ArrayList<>();

    @BeforeEach void prepare() { SecurityContextHolder.clearContext(); reset(ai, queries); }
    @AfterEach void cleanupOwnRows() {
        SecurityContextHolder.clearContext();
        try { verifyNoInteractions(ai, queries); }
        finally {
            for (Owner owner : owners) {
                // No production cascade and no other test's rows are deleted.
                for (String table : List.of("receivable_allocation_command", "receivable_allocation_audit", "receivable_allocation_cancellation",
                        "receivable_allocation_line", "receivable_allocation_batch", "receivable_registration_request", "receivable_invoice",
                        "receivable_payment", "receivable_counterparty"))
                    jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_session WHERE account_id=?", owner.account());
                jdbc.update("DELETE FROM app_membership WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_tenant WHERE id=?", owner.tenant());
                jdbc.update("DELETE FROM app_account WHERE id=?", owner.account());
            }
        }
    }

    @Test void reviewedHundredThousandInvoiceFortyThousandPaymentRetryAndCancellationPreserveOriginals() throws Exception {
        Owner owner = owner();
        var invoice = invoice(owner, "100000", "2026-11-01");
        var payment = payment(owner, "40000");
        var lines = List.of(line(payment, invoice, "40000"));
        var confirmed = confirm(owner, "review-confirm", lines, "partial payment review");
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"),
                "allocatedAmount", "40000", "outstandingAmount", "60000");
        assertMoney(paymentBalance(owner, payment), "unallocatedAmount", "0");

        assertThat(confirm(owner, "review-confirm", lines, "partial payment review")).isEqualTo(confirmed);
        assertThat(count(owner, "receivable_allocation_batch")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_line")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(1);

        send(owner, "allocations", "review-over", request(List.of(line(payment, invoice, "1")), null), 409)
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_AVAILABLE"));
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"),
                "allocatedAmount", "40000", "outstandingAmount", "60000");
        assertMoney(paymentBalance(owner, payment), "unallocatedAmount", "0");
        assertThat(count(owner, "receivable_allocation_command")).isEqualTo(1);

        var cancelled = cancel(owner, confirmed, "review-cancel", "incorrect connection");
        assertMoney(summary(owner, "2026-11-01"), "invoiceAmount", "100000", "paymentAmount", "40000",
                "allocatedAmount", "0", "outstandingAmount", "100000", "unallocatedAmount", "40000");
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "outstandingAmount", "100000");
        assertMoney(paymentBalance(owner, payment), "unallocatedAmount", "40000");
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("lines")).isEqualTo(confirmed.path("lines"));
        assertThat(cancelled.path("audit").get(0).path("eventType").asText()).isEqualTo("CONFIRMED");
        assertThat(cancelled.path("audit").get(1).path("eventType").asText()).isEqualTo("CANCELLED");
        assertThat(getJson(owner, "payments/" + id(payment))).isEqualTo(payment);
        assertThat(getJson(owner, "invoices/" + id(invoice))).isEqualTo(invoice);
        assertThat(count(owner, "receivable_allocation_line")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_cancellation")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(2);
    }

    @Test void partialPaymentsCompleteInvoiceAndCancellationRestoresExactBalancesAndAudit() throws Exception {
        Owner owner = owner();
        var invoice = invoice(owner, "100000", "2026-11-01");
        JsonNode p40 = payment(owner, "40000"), p60 = payment(owner, "60000");
        var first = confirm(owner, "first", List.of(line(p40, invoice, "40000")), "first part");
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "allocatedAmount", "40000", "outstandingAmount", "60000", "overdueAmount", "0");
        assertMoney(invoiceBalance(owner, invoice, "2026-11-02"), "overdueAmount", "60000");
        assertThat(invoiceBalance(owner, invoice, "2026-11-02").path("status").asText()).isEqualTo("PARTIAL");
        var second = confirm(owner, "second", List.of(line(p60, invoice, "60000")), null);
        assertMoney(invoiceBalance(owner, invoice, "2026-11-02"), "outstandingAmount", "0", "overdueAmount", "0");
        assertThat(invoiceBalance(owner, invoice, "2026-11-02").path("status").asText()).isEqualTo("PAID");
        assertMoney(paymentBalance(owner, p60), "unallocatedAmount", "0");
        var cancelled = cancel(owner, second, "cancel", "wrong payment");
        assertThat(cancelled.path("version").asInt()).isEqualTo(2);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("audit").size()).isEqualTo(2);
        assertThat(cancelled.path("audit").get(0).path("eventType").asText()).isEqualTo("CONFIRMED");
        assertThat(cancelled.path("audit").get(1).path("eventType").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancellation").path("createdBy").asText()).isEqualTo(owner.account().toString());
        assertThat(cancelled.path("cancellation").path("createdAt").asText()).endsWith("Z");
        assertThat(cancelled.path("lines")).isEqualTo(second.path("lines"));
        assertThat(getJson(owner, "allocations/" + id(first))).isEqualTo(first);
        assertMoney(invoiceBalance(owner, invoice, "2026-11-02"), "outstandingAmount", "60000", "overdueAmount", "60000");
        assertMoney(paymentBalance(owner, p60), "unallocatedAmount", "60000");
        assertMoney(getJson(owner, "invoices/" + id(invoice)), "amount", "100000");
        assertThat(count(owner, "receivable_allocation_line")).isEqualTo(2);
    }

    @Test void onePaymentCanBeSplitAcrossInvoicesAndCounterpartiesWithExplicitManualConfirmation() throws Exception {
        Owner owner = owner();
        JsonNode i30 = invoice(owner, "30000", "2025-01-01"), i70 = invoice(owner, "70000", "2027-01-01");
        var pay = payment(owner, "100000");
        var result = confirm(owner, "bundle", List.of(line(pay, i30, "30000"), line(pay, i70, "70000")), "manual cross-date confirmation");
        assertThat(result.path("lines").size()).isEqualTo(2);
        assertMoney(invoiceBalance(owner, i30, "2026-11-02"), "outstandingAmount", "0");
        assertMoney(invoiceBalance(owner, i70, "2026-11-02"), "outstandingAmount", "0");
        assertMoney(paymentBalance(owner, pay), "unallocatedAmount", "0");
        var summary = summary(owner, "2026-11-02");
        assertMoney(summary, "invoiceAmount", "100000", "paymentAmount", "100000", "allocatedAmount", "100000", "outstandingAmount", "0", "unallocatedAmount", "0");
        assertThat(summary.path("activeAllocationLineCount").asLong()).isEqualTo(2);
    }

    @Test void invoiceAndPaymentOverallocationsIncludingWithinBatchAreRejectedAtomically() throws Exception {
        Owner owner = owner();
        JsonNode i100 = invoice(owner, "100", "2026-11-01"), i200 = invoice(owner, "200", "2026-11-01");
        JsonNode p100 = payment(owner, "100"), p200 = payment(owner, "200");
        send(owner, "allocations", "invoice-too-much", request(List.of(line(p200, i100, "101")), null), 409);
        send(owner, "allocations", "payment-too-much", request(List.of(line(p100, i200, "101")), null), 409);
        send(owner, "allocations", "sum-payment", request(List.of(line(p100, i100, "60"), line(p100, i200, "60")), null), 409);
        send(owner, "allocations", "sum-invoice", request(List.of(line(p100, i100, "60"), line(p200, i100, "60")), null), 409);
        assertThat(count(owner, "receivable_allocation_command")).isZero();
        assertThat(count(owner, "receivable_allocation_batch")).isZero();
        confirm(owner, "valid", List.of(line(p100, i100, "80")), null);
        send(owner, "allocations", "late-over", request(List.of(line(p200, i100, "21")), null), 409);
        assertMoney(invoiceBalance(owner, i100, "2026-11-01"), "outstandingAmount", "20");
        assertMoney(paymentBalance(owner, p200), "unallocatedAmount", "200");
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(1);
    }

    @Test void idempotentConfirmationNormalizesOrderAndPreservesOriginalSnapshotAfterCancellation() throws Exception {
        Owner owner = owner();
        JsonNode i1 = invoice(owner, "100", "2026-11-01"), i2 = invoice(owner, "100", "2026-11-01");
        JsonNode p1 = payment(owner, "100"), p2 = payment(owner, "100");
        var lines = List.of(line(p1, i1, "40"), line(p2, i2, "60"));
        var original = confirm(owner, "same", lines, " memo ");
        assertThat(confirm(owner, "same", List.of(lines.get(1), lines.get(0)), "memo")).isEqualTo(original);
        send(owner, "allocations", "same", request(List.of(line(p1, i1, "41"), lines.get(1)), "memo"), 409)
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertMoney(invoiceBalance(owner, i1, "2026-11-01"),
                "allocatedAmount", "40", "outstandingAmount", "60");
        assertMoney(paymentBalance(owner, p1), "unallocatedAmount", "60");
        var cancelled = cancel(owner, original, "same", " cancel reason "); // operation-scoped key
        assertThat(cancel(owner, original, "same", "cancel reason")).isEqualTo(cancelled);
        assertThat(confirm(owner, "same", lines, "memo")).isEqualTo(original);
        assertThat(original.path("version").asInt()).isEqualTo(1);
        assertThat(getJson(owner, "allocations/" + id(original))).isEqualTo(cancelled);
        send(owner, "allocations/" + id(original) + "/cancel", "different", Map.of("reason", "again"), 409)
                .andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_CANCELLED"));
        send(owner, "allocations/" + id(original) + "/cancel", "same", Map.of("reason", "changed"), 409);
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(2);
        assertThat(count(owner, "receivable_allocation_command")).isEqualTo(2);
        // Explicit cancel followed by new confirmation is the supported correction flow.
        confirm(owner, "replacement", lines, "corrected confirmation");
        assertMoney(invoiceBalance(owner, i1, "2026-11-01"), "allocatedAmount", "40");
    }

    @Test void inputLimitsRejectMalformedLinesAmountsDuplicatePairsAndCancellationReason() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "1000000000000", "2026-11-01"), pay = payment(owner, "1000000000000");
        int n = 0;
        for (Object amount : List.of(1, 1.5, true, "0", "-1", "+1", "01", "1.0", "1e3", " 1", "1000000000001", "9223372036854775808")) {
            var line = new HashMap<String, Object>(line(pay, invoice, "1")); line.put("amount", amount);
            send(owner, "allocations", "bad-" + n++, request(List.of(line), null), 400);
        }
        var valid = line(pay, invoice, "1");
        send(owner, "allocations", "empty", request(List.of(), null), 400);
        send(owner, "allocations", "duplicate", request(List.of(valid, valid), null), 400);
        send(owner, "allocations", "too-many", request(java.util.Collections.nCopies(101, valid), null), 400);
        send(owner, "allocations", "wrong-lines", Map.of("lines", "wrong"), 400);
        send(owner, "allocations", "wrong-row", Map.of("lines", List.of("wrong")), 400);
        var unknownField = new HashMap<>(valid); unknownField.put("tenantId", owner.tenant().toString());
        send(owner, "allocations", "line-inject", request(List.of(unknownField), null), 400);
        send(owner, "allocations", "inject", Map.of("lines", List.of(valid), "createdBy", owner.account()), 400);
        send(owner, "allocations", "long-reason", request(List.of(valid), "x".repeat(1001)), 400);
        mvc.perform(post(path(owner, "allocations")).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request(List.of(valid), null)))).andExpect(status().isBadRequest());
        var batch = confirm(owner, "max", List.of(line(pay, invoice, "1000000000000")), null);
        for (var body : List.of(Map.<String, Object>of(), Map.<String, Object>of("reason", " "), Map.<String, Object>of("reason", 1),
                Map.<String, Object>of("reason", "x".repeat(1001)), Map.<String, Object>of("reason", "ok", "version", 1)))
            send(owner, "allocations/" + id(batch) + "/cancel", "bad-cancel", body, 400);
        assertThat(count(owner, "receivable_allocation_command")).isEqualTo(1);
    }

    @Test void allReadAndWriteRoutesRequireSessionAndTenantMembershipAndHideForeignIds() throws Exception {
        Owner a = owner(), b = owner();
        JsonNode invoice = invoice(a, "100", "2026-11-01"), pay = payment(a, "100");
        var batch = confirm(a, "a", List.of(line(pay, invoice, "50")), null);
        for (String suffix : List.of("allocations", "allocations/" + id(batch), "invoices/" + id(invoice) + "/balance?asOf=2026-11-01",
                "payments/" + id(pay) + "/balance", "receivables/summary?asOf=2026-11-01")) {
            mvc.perform(get(path(a, suffix))).andExpect(status().isUnauthorized());
            mvc.perform(get(path(a, suffix)).header("Authorization", b.bearer())).andExpect(status().isForbidden());
        }
        for (String suffix : List.of("allocations", "allocations/" + id(batch) + "/cancel")) {
            mvc.perform(post(path(a, suffix)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path(a, suffix)).header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        for (String suffix : List.of("allocations/" + id(batch), "invoices/" + id(invoice) + "/balance?asOf=2026-11-01", "payments/" + id(pay) + "/balance"))
            mvc.perform(get(path(b, suffix)).header("Authorization", b.bearer())).andExpect(status().isNotFound());
        JsonNode bInvoice = invoice(b, "100", "2026-11-01"), bPay = payment(b, "100");
        send(b, "allocations", "foreign-invoice", request(List.of(line(bPay, invoice, "1")), null), 404);
        send(b, "allocations", "foreign-payment", request(List.of(line(pay, bInvoice, "1")), null), 404);
        String hidden = send(b, "allocations/" + id(batch) + "/cancel", "c1", Map.of("reason", "no"), 404).andReturn().getResponse().getContentAsString();
        String unknown = send(b, "allocations/" + UUID.randomUUID() + "/cancel", "c2", Map.of("reason", "no"), 404).andReturn().getResponse().getContentAsString();
        assertThat(hidden).isEqualTo(unknown);
        assertThat(count(b, "receivable_allocation_command")).isZero();
        // The same key in another tenant creates its own command/result.
        confirm(b, "a", List.of(line(bPay, bInvoice, "1")), null);
    }

    @Test void balanceAndSummaryDateContractDistinguishesNoDataUnallocatedAndSettledWithoutStaleCache() throws Exception {
        Owner owner = owner();
        var empty = summary(owner, "2026-11-01");
        assertThat(empty.path("invoiceCount").asLong()).isZero();
        assertThat(empty.path("paymentCount").asLong()).isZero();
        assertMoney(empty, "outstandingAmount", "0", "unallocatedAmount", "0");
        var invoice = invoice(owner, "1000000000000", "2026-11-01");
        var invoice2 = invoice(owner, "1000000000000", "2026-11-02");
        var pay = payment(owner, "1000000000000");
        var unmatched = summary(owner, "2026-11-02");
        assertThat(unmatched.path("invoiceCount").asLong()).isEqualTo(2);
        assertThat(unmatched.path("activeAllocationLineCount").asLong()).isZero();
        assertMoney(unmatched, "invoiceAmount", "2000000000000", "outstandingAmount", "2000000000000", "overdueAmount", "1000000000000", "unallocatedAmount", "1000000000000");
        assertMoney(invoiceBalance(owner, invoice2, "2026-11-02"), "overdueAmount", "0");
        var batch = confirm(owner, "max", List.of(line(pay, invoice, "1000000000000")), null);
        assertMoney(summary(owner, "2026-11-02"), "allocatedAmount", "1000000000000", "outstandingAmount", "1000000000000", "overdueAmount", "0", "unallocatedAmount", "0");
        cancel(owner, batch, "cancel", "restore");
        assertThat(summary(owner, "2026-11-02")).isEqualTo(unmatched);
        for (String bad : List.of("2026-02-29", "0000-01-01", "2026-1-01", "2026-01-01T00:00:00Z")) {
            mvc.perform(get(path(owner, "receivables/summary")).header("Authorization", owner.bearer()).param("asOf", bad)).andExpect(status().isBadRequest());
            mvc.perform(get(path(owner, "invoices/" + id(invoice) + "/balance")).header("Authorization", owner.bearer()).param("asOf", bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(owner, "receivables/summary")).header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
        mvc.perform(get(path(owner, "invoices/" + id(invoice) + "/balance")).header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
    }

    @Test void concurrentIdenticalConfirmationAndCancellationHaveOneLedgerEventPerOperation() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var body = request(List.of(line(pay, invoice, "100")), null);
        var results = parallel(() -> response(owner, "allocations", "same", body), () -> response(owner, "allocations", "same", body));
        assertThat(results.get(0).status()).isEqualTo(201);
        assertThat(results.get(1)).isEqualTo(results.get(0));
        var batch = results.getFirst().json();
        String cancel = "allocations/" + id(batch) + "/cancel";
        results = parallel(() -> response(owner, cancel, "same", Map.of("reason", "fix")), () -> response(owner, cancel, "same", Map.of("reason", "fix")));
        assertThat(results.get(0).status()).isEqualTo(200);
        assertThat(results.get(1)).isEqualTo(results.get(0));
        assertThat(count(owner, "receivable_allocation_batch")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_cancellation")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(2);
        assertThat(count(owner, "receivable_allocation_command")).isEqualTo(2);
    }

    @Test void concurrentSameKeyDifferentPayloadConflictsWithoutDuplicatingFinancialRows() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var results = parallel(
                () -> response(owner, "allocations", "same", request(List.of(line(pay, invoice, "40")), null)),
                () -> response(owner, "allocations", "same", request(List.of(line(pay, invoice, "60")), null)));
        assertThat(results.stream().map(Result::status)).containsExactlyInAnyOrder(201, 409);
        assertThat(results.stream().filter(r -> r.status() == 409).findFirst().orElseThrow().json().path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(count(owner, "receivable_allocation_line")).isEqualTo(1);
        assertThat(count(owner, "receivable_allocation_audit")).isEqualTo(1);
    }

    @Test void competingInvoicesCannotOverAllocateOnePayment() throws Exception {
        Owner owner = owner();
        JsonNode i1 = invoice(owner, "100", "2026-11-01"), i2 = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var results = parallel(
                () -> response(owner, "allocations", "one", request(List.of(line(pay, i1, "100")), null)),
                () -> response(owner, "allocations", "two", request(List.of(line(pay, i2, "100")), null)));
        assertThat(results.stream().map(Result::status)).containsExactlyInAnyOrder(201, 409);
        assertMoney(paymentBalance(owner, pay), "allocatedAmount", "100", "unallocatedAmount", "0");
        assertMoney(summary(owner, "2026-11-01"), "allocatedAmount", "100", "outstandingAmount", "100");
        assertThat(count(owner, "receivable_allocation_command")).isEqualTo(1);
    }

    @Test void competingPaymentsCannotOverAllocateOneInvoice() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), p1 = payment(owner, "100"), p2 = payment(owner, "100");
        var results = parallel(
                () -> response(owner, "allocations", "one", request(List.of(line(p1, invoice, "100")), null)),
                () -> response(owner, "allocations", "two", request(List.of(line(p2, invoice, "100")), null)));
        assertThat(results.stream().map(Result::status)).containsExactlyInAnyOrder(201, 409);
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "allocatedAmount", "100", "outstandingAmount", "0");
        assertMoney(summary(owner, "2026-11-01"), "unallocatedAmount", "100");
    }

    @Test void opposingParentOrdersLockConsistentlyForConfirmationAndCancellation() throws Exception {
        Owner owner = owner();
        JsonNode i1 = invoice(owner, "100", "2026-11-01"), i2 = invoice(owner, "100", "2026-11-01");
        JsonNode p1 = payment(owner, "100"), p2 = payment(owner, "100");
        var a = List.of(line(p1, i2, "10"), line(p2, i1, "10"));
        var b = List.of(line(p2, i2, "10"), line(p1, i1, "10"));
        var results = parallel(() -> response(owner, "allocations", "a", request(a, null)), () -> response(owner, "allocations", "b", request(b, null)));
        assertThat(results.stream().map(Result::status)).containsOnly(201);
        JsonNode first = results.get(0).json(), second = results.get(1).json();
        var cancellations = parallel(
                () -> response(owner, "allocations/" + id(first) + "/cancel", "ca", Map.of("reason", "a")),
                () -> response(owner, "allocations/" + id(second) + "/cancel", "cb", Map.of("reason", "b")));
        assertThat(cancellations.stream().map(Result::status)).containsOnly(200);
        assertMoney(summary(owner, "2026-11-01"), "allocatedAmount", "0", "outstandingAmount", "200", "unallocatedAmount", "200");
    }

    @Test void competingCancellationKeysProduceOneReversalAndCancelConfirmRacePreservesCapacity() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var batch = confirm(owner, "first", List.of(line(pay, invoice, "40")), null);
        String cancel = "allocations/" + id(batch) + "/cancel";
        var cancels = parallel(() -> response(owner, cancel, "one", Map.of("reason", "a")), () -> response(owner, cancel, "two", Map.of("reason", "b")));
        assertThat(cancels.stream().map(Result::status)).containsExactlyInAnyOrder(200, 409);
        assertThat(count(owner, "receivable_allocation_cancellation")).isEqualTo(1);
        var again = confirm(owner, "again", List.of(line(pay, invoice, "40")), null);
        var race = parallel(
                () -> response(owner, "allocations/" + id(again) + "/cancel", "third", Map.of("reason", "release")),
                () -> response(owner, "allocations", "new", request(List.of(line(pay, invoice, "100")), null)));
        assertThat(race.get(0).status()).isEqualTo(200);
        assertThat(race.get(1).status()).isIn(201, 409);
        String expected = race.get(1).status() == 201 ? "100" : "0";
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "allocatedAmount", expected);
        assertMoney(paymentBalance(owner, pay), "allocatedAmount", expected);
    }

    @Test void lateAuditFailureRollsBackConfirmationAndCancellationAndSameKeysCanRetry() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var body = request(List.of(line(pay, invoice, "100")), null);
        jdbc.execute("ALTER TABLE receivable_allocation_audit ADD CONSTRAINT ck_test_reject_allocation_audit CHECK (false) NOT VALID");
        try {
            send(owner, "allocations", "retry", body, 503).andExpect(jsonPath("$.code").value("ALLOCATION_STORAGE_UNAVAILABLE"));
            for (String table : List.of("receivable_allocation_command", "receivable_allocation_batch", "receivable_allocation_line", "receivable_allocation_audit"))
                assertThat(count(owner, table)).isZero();
        } finally { jdbc.execute("ALTER TABLE receivable_allocation_audit DROP CONSTRAINT ck_test_reject_allocation_audit"); }
        var batch = confirm(owner, "retry", List.of(line(pay, invoice, "100")), null);
        jdbc.execute("ALTER TABLE receivable_allocation_audit ADD CONSTRAINT ck_test_reject_allocation_audit CHECK (event_type<>'CANCELLED') NOT VALID");
        try {
            send(owner, "allocations/" + id(batch) + "/cancel", "retry", Map.of("reason", "fix"), 503);
            assertThat(count(owner, "receivable_allocation_cancellation")).isZero();
            assertThat(count(owner, "receivable_allocation_command")).isEqualTo(1);
            assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "outstandingAmount", "0");
        } finally { jdbc.execute("ALTER TABLE receivable_allocation_audit DROP CONSTRAINT ck_test_reject_allocation_audit"); }
        cancel(owner, batch, "retry", "fix");
        assertMoney(invoiceBalance(owner, invoice, "2026-11-01"), "outstandingAmount", "100");
    }

    @Test void multiLineFailureDoesNotPersistEarlierValidLinesAndDatabaseCompositeFksRejectCrossTenant() throws Exception {
        Owner a = owner(), b = owner();
        JsonNode ai = invoice(a, "100", "2026-11-01"), ap = payment(a, "100"), bi = invoice(b, "100", "2026-11-01");
        send(a, "allocations", "rollback", request(List.of(line(ap, ai, "1"), line(ap, bi, "1")), null), 404);
        assertThat(count(a, "receivable_allocation_batch")).isZero();
        var batch = confirm(a, "valid", List.of(line(ap, ai, "1")), null);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO receivable_allocation_line (id,tenant_id,batch_id,line_number,payment_id,invoice_id,amount)
                VALUES (?,?,?,2,?,?,1)
                """, UUID.randomUUID(), a.tenant(), UUID.fromString(id(batch)), UUID.fromString(id(ap)), UUID.fromString(id(bi))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(a, "receivable_allocation_line")).isEqualTo(1);
    }

    @Test void listIsTenantScopedBoundedAndCancelledHistoryRemainsVisible() throws Exception {
        Owner owner = owner();
        JsonNode invoice = invoice(owner, "100", "2026-11-01"), pay = payment(owner, "100");
        var first = confirm(owner, "a", List.of(line(pay, invoice, "10")), null);
        cancel(owner, first, "c", "history");
        var second = confirm(owner, "b", List.of(line(pay, invoice, "10")), null);
        var page0 = getJson(owner, "allocations?size=1&page=0");
        assertThat(page0.path("items").get(0)).isEqualTo(second);
        assertThat(page0.path("hasNext").asBoolean()).isTrue();
        assertThat(getJson(owner, "allocations?size=1&page=1").path("items").get(0).path("status").asText()).isEqualTo("CANCELLED");
        for (String query : List.of("?size=0", "?size=101", "?page=-1", "?page=x"))
            mvc.perform(get(path(owner, "allocations" + query)).header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
        assertThat(getJson(owner, "allocations?page=2147483647&size=100").path("items")).isEmpty();
    }

    @Test void everyServiceEntryPointRechecksMembershipAndRevokedSession() throws Exception {
        Owner a = owner(), b = owner();
        assertThatThrownBy(() -> service.summary(a.tenant(), "2026-11-01")).isInstanceOf(AccountException.class);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(auth.authenticate(a.token()), null, List.of()));
        SecurityContextHolder.setContext(context);
        assertThatThrownBy(() -> service.confirm(b.tenant(), "k", Map.of()))
                .isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(403));
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE account_id=?", a.account());
        for (Runnable action : List.<Runnable>of(
                () -> service.confirm(a.tenant(), "k", Map.of()),
                () -> service.cancel(a.tenant(), UUID.randomUUID(), "k", Map.of()),
                () -> service.get(a.tenant(), UUID.randomUUID()),
                () -> service.list(a.tenant(), 0, 20),
                () -> service.invoiceBalance(a.tenant(), UUID.randomUUID(), "2026-11-01"),
                () -> service.paymentBalance(a.tenant(), UUID.randomUUID()),
                () -> service.summary(a.tenant(), "2026-11-01")))
            assertThatThrownBy(action::run).isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(401));
        assertThat(count(a, "receivable_allocation_command")).isZero();
    }

    private Owner owner() throws Exception {
        var signup = mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("login", "allocation-" + UUID.randomUUID(), "password", "password-123456", "tenantName", "Synthetic shop"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = signup.path("accessToken").asText();
        var me = mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var owner = new Owner(UUID.fromString(me.path("accountId").asText()), UUID.fromString(me.path("tenants").get(0).path("id").asText()), token);
        owners.add(owner);
        return owner;
    }
    private JsonNode invoice(Owner owner, String amount, String due) throws Exception {
        String key = UUID.randomUUID().toString();
        var cp = postJson(owner, "counterparties", key, Map.of("code", "C" + key.replace("-", "").substring(0, 20), "name", "Synthetic customer"), 201);
        return postJson(owner, "invoices", key, Map.of("counterpartyId", id(cp), "invoiceNumber", key, "amount", amount, "issueDate", "2025-01-01", "dueDate", due), 201);
    }
    private JsonNode payment(Owner owner, String amount) throws Exception {
        return postJson(owner, "payments", UUID.randomUUID().toString(), Map.of("amount", amount, "bookedDate", "2026-11-01", "payerName", "Different payer"), 201);
    }
    private JsonNode confirm(Owner owner, String key, List<Map<String, Object>> lines, String reason) throws Exception {
        return postJson(owner, "allocations", key, request(lines, reason), 201);
    }
    private JsonNode cancel(Owner owner, JsonNode batch, String key, String reason) throws Exception {
        return postJson(owner, "allocations/" + id(batch) + "/cancel", key, Map.of("reason", reason), 200);
    }
    private JsonNode invoiceBalance(Owner owner, JsonNode invoice, String date) throws Exception { return getJson(owner, "invoices/" + id(invoice) + "/balance?asOf=" + date); }
    private JsonNode paymentBalance(Owner owner, JsonNode payment) throws Exception { return getJson(owner, "payments/" + id(payment) + "/balance"); }
    private JsonNode summary(Owner owner, String date) throws Exception { return getJson(owner, "receivables/summary?asOf=" + date); }
    private JsonNode postJson(Owner owner, String suffix, String key, Map<String, Object> body, int status) throws Exception {
        return mapper.readTree(send(owner, suffix, key, body, status).andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.ResultActions send(Owner owner, String suffix, String key, Map<String, Object> body, int expected) throws Exception {
        return mvc.perform(post(path(owner, suffix)).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(expected)).andExpect(header().string("Cache-Control", "no-store"));
    }
    private Result response(Owner owner, String suffix, String key, Map<String, Object> body) throws Exception {
        var result = mvc.perform(post(path(owner, suffix)).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn().getResponse();
        return new Result(result.getStatus(), mapper.readTree(result.getContentAsString()));
    }
    private JsonNode getJson(Owner owner, String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(path(owner, suffix)).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private List<Result> parallel(Callable<Result> first, Callable<Result> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
    private int count(Owner owner, String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id=?", Integer.class, owner.tenant()); }
    private static String id(JsonNode row) { return row.path("id").asText(); }
    private static String path(Owner owner, String suffix) { return "/api/v1/tenants/" + owner.tenant() + "/" + suffix; }
    private static Map<String, Object> line(JsonNode pay, JsonNode invoice, String amount) { return Map.of("paymentId", id(pay), "invoiceId", id(invoice), "amount", amount); }
    private static Map<String, Object> request(List<Map<String, Object>> lines, String reason) {
        var request = new HashMap<String, Object>(); request.put("lines", lines); if (reason != null) request.put("reason", reason); return request;
    }
    private static void assertMoney(JsonNode value, String... fieldsAndValues) {
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            assertThat(value.path(fieldsAndValues[i]).isString()).isTrue();
            assertThat(value.path(fieldsAndValues[i]).asText()).isEqualTo(fieldsAndValues[i + 1]);
        }
    }
    private record Owner(UUID account, UUID tenant, String token) { String bearer() { return "Bearer " + token; } }
    private record Result(int status, JsonNode json) {}
}
