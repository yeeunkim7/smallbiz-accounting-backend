package com.smallbiz.reconciliation.receivables.matching;

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

@SpringBootTest(properties = {"AI_API_KEY=synthetic-key-present", "app.ai.enabled=true",
        "app.receivables.matching.max-payments=3", "app.receivables.matching.max-invoices=5", "app.receivables.matching.top-per-payment=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class MatchingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired MatchingService service;
    @MockitoBean AiCompletionClient ai;
    @MockitoBean ReadOnlyReviewQueries queries;
    private final List<Owner> owners = new ArrayList<>();
    @BeforeEach void prepare() { SecurityContextHolder.clearContext(); reset(ai, queries); }
    @AfterEach void cleanupOwnRows() {
        SecurityContextHolder.clearContext();
        try { verifyNoInteractions(ai, queries); }
        finally {
            for (Owner owner : owners) {
                for (String table : List.of("receivable_matching_usage_event", "receivable_monthly_usage", "receivable_matching_run", "receivable_allocation_command", "receivable_allocation_audit",
                        "receivable_allocation_cancellation", "receivable_allocation_line", "receivable_allocation_batch", "receivable_registration_request",
                        "receivable_invoice", "receivable_payment", "receivable_counterparty"))
                    jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_session WHERE account_id=?", owner.account());
                jdbc.update("DELETE FROM app_membership WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_tenant WHERE id=?", owner.tenant());
                jdbc.update("DELETE FROM app_account WHERE id=?", owner.account());
            }
        }
    }

    @Test void exactAmountAndSameNamePartialCandidatesCarryEvidenceWithoutChangingAllocationState() throws Exception {
        Owner owner = owner();
        var invoice = invoice(owner, "Customer", "100", "2026-01-01", "2026-11-20");
        var exact = payment(owner, "Other payer", "100", "2026-11-10");
        var partial = payment(owner, "CUSTOMER", "40", "2026-11-10");
        var run = run(owner, "rules", "2026-11-01", "2026-11-30");
        assertThat(run.path("ruleVersion").asText()).isEqualTo(MatchingService.RULE_VERSION);
        var amountCandidate = paymentResult(run, exact).path("candidates").get(0);
        assertThat(amountCandidate.path("invoiceId").asText()).isEqualTo(id(invoice));
        assertThat(amountCandidate.path("reasons").toString()).contains("EXACT_REMAINING_AMOUNT");
        assertThat(amountCandidate.path("warnings").toString()).contains("NAME_MISMATCH");
        var nameCandidate = paymentResult(run, partial).path("candidates").get(0);
        assertThat(nameCandidate.path("suggestedAmount").asText()).isEqualTo("40");
        assertThat(nameCandidate.path("reasons").toString()).contains("NORMALIZED_NAME_EXACT");
        assertThat(nameCandidate.path("warnings").toString()).contains("REMAINING_AMOUNT_MISMATCH");
        assertThat(run.path("invoices").get(0).path("outstandingAmount").asText()).isEqualTo("100");
        assertThat(run.path("invoices").get(0).path("counterpartyName").asText()).isEqualTo("Customer");
        assertThat(paymentResult(run, partial).path("payment").path("amount").isString()).isTrue();
        assertThat(count(owner, "receivable_allocation_batch")).isZero();
        assertThat(count(owner, "receivable_allocation_audit")).isZero();
    }

    @Test void noEligibleSourcesAndUnclearPaymentsHaveDistinctExplicitStates() throws Exception {
        Owner owner = owner();
        var empty = run(owner, "empty", "2026-11-01", "2026-11-30");
        assertThat(empty.path("dataPresence").asText()).isEqualTo("NO_ELIGIBLE_SOURCES");
        assertThat(empty.path("sourceCounts").path("paymentCount").asInt()).isZero();
        invoice(owner, "Customer", "100", "2026-01-01", "2026-11-20");
        assertThat(run(owner, "invoice-only", "2026-11-01", "2026-11-30").path("dataPresence").asText()).isEqualTo("INVOICES_ONLY");
        var pay = payment(owner, "Unclear", "40", "2026-11-10");
        var unclear = run(owner, "unclear", "2026-11-01", "2026-11-30");
        assertThat(unclear.path("dataPresence").asText()).isEqualTo("BOTH");
        assertThat(paymentResult(unclear, pay).path("status").asText()).isEqualTo("NEEDS_MANUAL_REVIEW");
        assertThat(paymentResult(unclear, pay).path("candidates")).isEmpty();
        Owner other = owner();
        payment(other, null, "1", "2026-11-10");
        assertThat(run(other, "payment-only", "2026-11-01", "2026-11-30").path("dataPresence").asText()).isEqualTo("PAYMENTS_ONLY");
    }

    @Test void paymentDateRangeIsInclusiveAndOlderInvoicesRemainEligibleWithTimingWarnings() throws Exception {
        Owner owner = owner();
        var older = invoice(owner, "Customer", "100", "2024-01-01", "2024-02-01");
        var laterIssue = invoice(owner, "Customer", "100", "2026-11-20", "2026-11-30");
        invoice(owner, "Customer", "100", "2026-12-01", "2026-12-01");
        var from = payment(owner, null, "100", "2026-11-01");
        var to = payment(owner, null, "100", "2026-11-30");
        payment(owner, null, "100", "2026-10-31");
        payment(owner, null, "100", "2026-12-01");
        var run = run(owner, "range", "2026-11-01", "2026-11-30");
        assertThat(run.path("sourceCounts").path("paymentCount").asInt()).isEqualTo(2);
        assertThat(run.path("sourceCounts").path("invoiceCount").asInt()).isEqualTo(2);
        assertThat(paymentResult(run, to).path("payment").path("bookedDate").asText()).isEqualTo("2026-11-30");
        var beforeIssue = candidate(paymentResult(run, from), laterIssue);
        assertThat(beforeIssue.path("warnings").toString()).contains("PAYMENT_BEFORE_ISSUE_DATE", "PAYER_NAME_MISSING");
        assertThat(candidate(paymentResult(run, from), older).path("warnings").toString()).contains("PAYMENT_AFTER_DUE_DATE");
    }

    @Test void fullyAllocatedSourcesAreExcludedAndCancellationRestoresEligibility() throws Exception {
        Owner owner = owner();
        var invoice = invoice(owner, "Customer", "100", "2026-01-01", "2026-11-20");
        var payment = payment(owner, "Customer", "100", "2026-11-10");
        var batch = allocate(owner, payment, invoice, "100", 201);
        assertThat(run(owner, "closed", "2026-11-01", "2026-11-30").path("dataPresence").asText()).isEqualTo("NO_ELIGIBLE_SOURCES");
        send(owner, "allocations/" + id(batch) + "/cancel", "cancel", Map.of("reason", "restore"), 200);
        var restored = run(owner, "restored", "2026-11-01", "2026-11-30");
        assertThat(restored.path("dataPresence").asText()).isEqualTo("BOTH");
        assertThat(paymentResult(restored, payment).path("payment").path("unallocatedAmount").asText()).isEqualTo("100");
        assertThat(restored.path("invoices").get(0).path("outstandingAmount").asText()).isEqualTo("100");
        // Bypass the supported allocation writer in this isolated DB to prove an invalid negative
        // remainder is rejected, rather than disappearing under an outstanding>0 source filter.
        UUID corrupt = UUID.randomUUID();
        jdbc.update("INSERT INTO receivable_allocation_batch (id,tenant_id,created_by,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                corrupt, owner.tenant(), owner.account());
        jdbc.update("""
                INSERT INTO receivable_allocation_line (id,tenant_id,batch_id,line_number,payment_id,invoice_id,amount)
                VALUES (?,?,?,1,?,?,101)
                """, UUID.randomUUID(), owner.tenant(), corrupt, UUID.fromString(id(payment)), UUID.fromString(id(invoice)));
        send(owner, "matching-runs", "invalid-balance", range("2026-11-01", "2026-11-30"), 503);
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(2);
    }

    @Test void rankUsesScoreThenDueDistanceThenUuidAndTopTruncationIsExplicit() throws Exception {
        Owner owner = owner();
        var tied1 = invoice(owner, "Customer", "100", "2026-01-01", "2026-11-05");
        var tied2 = invoice(owner, "Customer", "100", "2026-01-01", "2026-11-05");
        invoice(owner, "Customer", "100", "2026-01-01", "2026-11-25");
        invoice(owner, "Different", "100", "2026-01-01", "2026-11-10");
        var payment = payment(owner, "Customer", "100", "2026-11-10");
        var run = run(owner, "rank", "2026-11-01", "2026-11-30");
        var result = paymentResult(run, payment);
        assertThat(result.path("totalCandidateCount").asInt()).isEqualTo(4);
        assertThat(result.path("candidatesTruncated").asBoolean()).isTrue();
        assertThat(result.path("candidates").size()).isEqualTo(2);
        var ordered = List.of(id(tied1), id(tied2)).stream().sorted().toList();
        assertThat(result.path("candidates").get(0).path("invoiceId").asText()).isEqualTo(ordered.get(0));
        assertThat(result.path("candidates").get(1).path("invoiceId").asText()).isEqualTo(ordered.get(1));
        assertThat(result.path("candidates").get(0).path("dueDateDistanceDays").asLong()).isEqualTo(5);
        assertThat(run(owner, "again", "2026-11-01", "2026-11-30").path("payments")).isEqualTo(run.path("payments"));
    }

    @Test void unicodeCompatibilityCaseAndWhitespaceNormalizationPreservesOriginalEvidence() throws Exception {
        Owner owner = owner();
        invoice(owner, "ＡＣＭＥ\u00a0  주식회사", "200", "2026-01-01", "2026-11-20");
        var payment = payment(owner, "acme\t주식회사", "50", "2026-11-10");
        var run = run(owner, "unicode", "2026-11-01", "2026-11-30");
        assertThat(run.path("invoices").get(0).path("counterpartyName").asText()).contains("ＡＣＭＥ");
        assertThat(run.path("invoices").get(0).path("normalizedCounterpartyName").asText()).isEqualTo("acme 주식회사");
        assertThat(paymentResult(run, payment).path("candidates").get(0).path("reasons").toString()).contains("NORMALIZED_NAME_EXACT");
        assertThat(MatchingService.normalizeName(" I ")).isEqualTo("i");
        assertThat(MatchingService.normalizeName(null)).isEmpty();
    }

    @Test void sourceLimitsRejectEntireRunWithoutPersistingOrSilentlyDroppingSources() throws Exception {
        Owner payments = owner();
        for (int i = 0; i < 4; i++) payment(payments, null, "1", "2026-11-10");
        send(payments, "matching-runs", "too-many", range("2026-11-01", "2026-11-30"), 422)
                .andExpect(jsonPath("$.code").value("MATCHING_SOURCE_LIMIT"));
        assertThat(count(payments, "receivable_matching_run")).isZero();
        Owner invoices = owner();
        for (int i = 0; i < 6; i++) invoice(invoices, "Customer", "1", "2026-01-01", "2026-11-20");
        send(invoices, "matching-runs", "too-many", range("2026-11-01", "2026-11-30"), 422);
        assertThat(count(invoices, "receivable_matching_run")).isZero();
    }

    @Test void malformedRangesPrivilegeFieldsKeysAndPaginationAreRejected() throws Exception {
        Owner owner = owner();
        for (var body : List.of(range("2026-11-30", "2026-11-01"), range("2024-01-01", "2025-01-01"),
                range("2026-02-29", "2026-03-01"), range("0000-01-01", "2026-01-01"), range("2026-1-01", "2026-11-30"),
                Map.<String,Object>of("from", 20261101, "to", "2026-11-30"), Map.<String,Object>of("from", "2026-11-01"),
                Map.<String,Object>of("from", "2026-11-01", "to", "2026-11-30", "tenantId", owner.tenant())))
            send(owner, "matching-runs", "invalid", body, 400);
        for (String key : List.of("", "has space", "x".repeat(101))) send(owner, "matching-runs", key, range("2026-11-01", "2026-11-30"), 400);
        mvc.perform(post(path(owner, "matching-runs")).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(range("2026-11-01", "2026-11-30")))).andExpect(status().isBadRequest());
        run(owner, "inclusive-366", "2024-01-01", "2024-12-31");
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=x"))
            mvc.perform(get(path(owner, "matching-runs" + query)).header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(1);
    }

    @Test void invalidComputationConfigurationFailsClosed() {
        for (int[] invalid : List.of(new int[]{0, 200, 5}, new int[]{200, 0, 5}, new int[]{1001, 200, 5},
                new int[]{200, 1001, 5}, new int[]{200, 200, 0}, new int[]{200, 200, 21}))
            assertThatThrownBy(() -> new MatchingPolicy(invalid[0], invalid[1], invalid[2])).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MatchingPolicy(200, 200, 5).topPerPayment()).isEqualTo(5);
    }

    @Test void authenticatedTenantScopeAndEveryDirectServiceEntryPointAreRechecked() throws Exception {
        Owner a = owner(), b = owner();
        invoice(a, "Customer", "100", "2026-01-01", "2026-11-20");
        payment(a, "Customer", "100", "2026-11-10");
        var run = run(a, "shared", "2026-11-01", "2026-11-30");
        for (String suffix : List.of("matching-runs", "matching-runs/" + id(run))) {
            mvc.perform(get(path(a, suffix))).andExpect(status().isUnauthorized());
            mvc.perform(get(path(a, suffix)).header("Authorization", b.bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(post(path(a, "matching-runs")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(a, "matching-runs")).header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        String hidden = mvc.perform(get(path(b, "matching-runs/" + id(run))).header("Authorization", b.bearer()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get(path(b, "matching-runs/" + UUID.randomUUID())).header("Authorization", b.bearer()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(hidden).isEqualTo(unknown);
        assertThat(run(b, "shared", "2026-11-01", "2026-11-30").path("dataPresence").asText()).isEqualTo("NO_ELIGIBLE_SOURCES");
        assertThatThrownBy(() -> service.get(a.tenant(), UUID.fromString(id(run)))).isInstanceOf(AccountException.class);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(auth.authenticate(a.token()), null, List.of()));
        SecurityContextHolder.setContext(context);
        assertThatThrownBy(() -> service.create(b.tenant(), "denied", Map.of())).isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(403));
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE account_id=?", a.account());
        for (Runnable action : List.<Runnable>of(() -> service.create(a.tenant(), "denied", Map.of()),
                () -> service.get(a.tenant(), UUID.fromString(id(run))), () -> service.list(a.tenant(), 0, 20)))
            assertThatThrownBy(action::run).isInstanceOfSatisfying(AccountException.class, ex -> assertThat(ex.status()).isEqualTo(401));
    }

    @Test void retryUsesCanonicalRangeAndStoredSnapshotWhileListOnlyReturnsMetadata() throws Exception {
        Owner owner = owner();
        var body = range("2026-11-01", "2026-11-30");
        var original = run(owner, "same", "2026-11-01", "2026-11-30");
        var reverseFields = new java.util.LinkedHashMap<String,Object>(); reverseFields.put("to", "2026-11-30"); reverseFields.put("from", "2026-11-01");
        assertThat(sendJson(owner, "matching-runs", "same", reverseFields, 201)).isEqualTo(original);
        send(owner, "matching-runs", "same", range("2026-11-02", "2026-11-30"), 409).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(getJson(owner, "matching-runs/" + id(original))).isEqualTo(original);
        run(owner, "another", "2026-11-01", "2026-11-30");
        var list = getJson(owner, "matching-runs?size=1&page=0");
        assertThat(list.path("hasNext").asBoolean()).isTrue();
        assertThat(list.path("items").get(0).has("payments")).isFalse();
        assertThat(list.path("items").get(0).has("sourceCounts")).isTrue();
        assertThat(getJson(owner, "matching-runs?page=2147483647&size=100").path("items")).isEmpty();
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(2);
    }

    @Test void concurrentDuplicateAndConflictingPayloadsCommitExactlyOneRunForEachKey() throws Exception {
        Owner owner = owner();
        var same = range("2026-11-01", "2026-11-30");
        var pair = parallel(() -> response(owner, "same", same), () -> response(owner, "same", same));
        assertThat(pair.get(0).status()).isEqualTo(201);
        assertThat(pair.get(1)).isEqualTo(pair.get(0));
        var conflict = parallel(() -> response(owner, "conflict", same), () -> response(owner, "conflict", range("2026-11-02", "2026-11-30")));
        assertThat(conflict.stream().map(Result::status)).containsExactlyInAnyOrder(201, 409);
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_matching_run WHERE tenant_id=? AND status<>'SUCCESS'", Integer.class, owner.tenant())).isZero();
    }

    @Test void snapshotRemainsOriginalAfterNewAllocationAndStaleSuggestionMustPassExistingConfirmationChecks() throws Exception {
        Owner owner = owner();
        var invoice = invoice(owner, "Customer", "100", "2026-01-01", "2026-11-20");
        var payment = payment(owner, "Customer", "100", "2026-11-10");
        var original = run(owner, "before", "2026-11-01", "2026-11-30");
        allocate(owner, payment, invoice, "50", 201);
        assertThat(getJson(owner, "matching-runs/" + id(original))).isEqualTo(original);
        assertThat(run(owner, "before", "2026-11-01", "2026-11-30")).isEqualTo(original);
        allocate(owner, payment, invoice, "100", 409);
        var current = run(owner, "after", "2026-11-01", "2026-11-30");
        assertThat(paymentResult(current, payment).path("payment").path("unallocatedAmount").asText()).isEqualTo("50");
        assertThat(current.path("invoices").get(0).path("outstandingAmount").asText()).isEqualTo("50");
        assertThat(paymentResult(current, payment).path("candidates").get(0).path("suggestedAmount").asText()).isEqualTo("50");
        assertThat(count(owner, "receivable_allocation_batch")).isEqualTo(1);
    }

    @Test void lateSnapshotStorageFailureRollsBackProvisionalRunAndAllowsSameKeyRetry() throws Exception {
        Owner owner = owner();
        jdbc.execute("ALTER TABLE receivable_matching_run ADD CONSTRAINT ck_test_reject_matching_result CHECK (result_json IS NULL) NOT VALID");
        try {
            send(owner, "matching-runs", "retry", range("2026-11-01", "2026-11-30"), 503)
                    .andExpect(jsonPath("$.code").value("MATCHING_STORAGE_UNAVAILABLE"));
            assertThat(count(owner, "receivable_matching_run")).isZero();
        } finally { jdbc.execute("ALTER TABLE receivable_matching_run DROP CONSTRAINT ck_test_reject_matching_result"); }
        run(owner, "retry", "2026-11-01", "2026-11-30");
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(1);
    }

    private Owner owner() throws Exception {
        var signup = mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("login", "matching-" + UUID.randomUUID(), "password", "password-123456", "tenantName", "Synthetic shop"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = signup.path("accessToken").asText();
        var me = mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var owner = new Owner(UUID.fromString(me.path("accountId").asText()), UUID.fromString(me.path("tenants").get(0).path("id").asText()), token);
        owners.add(owner); return owner;
    }
    private JsonNode invoice(Owner owner, String name, String amount, String issue, String due) throws Exception {
        String key = UUID.randomUUID().toString();
        var cp = sendJson(owner, "counterparties", key, Map.of("code", "C" + key.replace("-", "").substring(0, 20), "name", name), 201);
        return sendJson(owner, "invoices", key, Map.of("counterpartyId", id(cp), "invoiceNumber", key, "amount", amount, "issueDate", issue, "dueDate", due), 201);
    }
    private JsonNode payment(Owner owner, String payer, String amount, String booked) throws Exception {
        var body = new HashMap<String,Object>(); body.put("amount", amount); body.put("bookedDate", booked); if (payer != null) body.put("payerName", payer);
        return sendJson(owner, "payments", UUID.randomUUID().toString(), body, 201);
    }
    private JsonNode allocate(Owner owner, JsonNode pay, JsonNode invoice, String amount, int status) throws Exception {
        return sendJson(owner, "allocations", UUID.randomUUID().toString(), Map.of("lines", List.of(Map.of("paymentId", id(pay), "invoiceId", id(invoice), "amount", amount))), status);
    }
    private JsonNode run(Owner owner, String key, String from, String to) throws Exception { return sendJson(owner, "matching-runs", key, range(from, to), 201); }
    private JsonNode sendJson(Owner owner, String suffix, String key, Map<String,Object> body, int status) throws Exception {
        return mapper.readTree(send(owner, suffix, key, body, status).andReturn().getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.ResultActions send(Owner owner, String suffix, String key, Map<String,Object> body, int expected) throws Exception {
        return mvc.perform(post(path(owner, suffix)).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().is(expected))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    private JsonNode getJson(Owner owner, String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(path(owner, suffix)).header("Authorization", owner.bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private Result response(Owner owner, String key, Map<String,Object> body) throws Exception {
        var result = mvc.perform(post(path(owner, "matching-runs")).header("Authorization", owner.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn().getResponse();
        return new Result(result.getStatus(), mapper.readTree(result.getContentAsString()));
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
    private static JsonNode paymentResult(JsonNode run, JsonNode payment) {
        for (JsonNode result : run.path("payments")) if (id(result.path("payment")).equals(id(payment))) return result;
        throw new AssertionError("Missing payment result");
    }
    private static JsonNode candidate(JsonNode result, JsonNode invoice) {
        for (JsonNode candidate : result.path("candidates")) if (candidate.path("invoiceId").asText().equals(id(invoice))) return candidate;
        throw new AssertionError("Missing candidate");
    }
    private static Map<String,Object> range(String from, String to) { return Map.of("from", from, "to", to); }
    private static String id(JsonNode row) { return row.path("id").asText(); }
    private static String path(Owner owner, String suffix) { return "/api/v1/tenants/" + owner.tenant() + "/" + suffix; }
    private record Owner(UUID account, UUID tenant, String token) { String bearer() { return "Bearer " + token; } }
    private record Result(int status, JsonNode json) {}
}
