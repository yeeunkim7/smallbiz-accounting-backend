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
        "app.receivables.matching.max-payments=1", "app.receivables.usage.standard-limit=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class UsageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired MatchingService service;
    @MockitoBean AiCompletionClient ai;
    @MockitoBean ReadOnlyReviewQueries queries;
    @Autowired UsageService usage;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean UsageClock usageClock;
    private final List<Owner> owners = new ArrayList<>();
    @BeforeEach void prepare() { SecurityContextHolder.clearContext(); reset(ai, queries, usageClock); when(usageClock.instant()).thenReturn(java.time.Instant.parse("2026-11-01T00:00:00Z")); }
    @AfterEach void cleanupOwnRows() {
        SecurityContextHolder.clearContext();
        try { verifyNoInteractions(ai, queries); }
        finally {
            for (Owner owner : owners) new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> {
                for (String table : List.of("receivable_matching_usage_event", "receivable_monthly_usage", "receivable_matching_run", "receivable_bank_import_request", "receivable_bank_transaction", "receivable_bank_source_row", "receivable_bank_import", "receivable_allocation_command", "receivable_allocation_audit",
                        "receivable_allocation_cancellation", "receivable_allocation_line", "receivable_allocation_batch", "receivable_registration_request",
                        "receivable_invoice", "receivable_payment", "receivable_counterparty"))
                    jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_session WHERE account_id=?", owner.account());
                jdbc.update("DELETE FROM app_membership WHERE tenant_id=?", owner.tenant());
                jdbc.update("DELETE FROM app_tenant WHERE id=?", owner.tenant());
                jdbc.update("DELETE FROM app_account WHERE id=?", owner.account());
            });
        }
    }


    @Test void tenthSuccessAndEleventhRejectionDoNotConsumeRejectedKeyAndConflictPrecedesQuota() throws Exception {
        Owner owner = owner();
        var first = run(owner, "run0", "2026-11-01", "2026-11-30");
        for (int i=1; i<10; i++) run(owner, "run"+i, "2026-11-01", "2026-11-30");
        send(owner, "matching-runs", "retryable", range("2026-11-01", "2026-11-30"), 429)
                .andExpect(jsonPath("$.code").value("MATCHING_MONTHLY_LIMIT"))
                .andExpect(jsonPath("$.usage.completed").value(10)).andExpect(jsonPath("$.usage.remaining").value(0))
                .andExpect(jsonPath("$.usage.resetAt").value("2026-11-30T15:00:00Z"))
                .andExpect(header().exists("Retry-After"));
        assertThat(run(owner, "run0", "2026-11-01", "2026-11-30")).isEqualTo(first);
        send(owner, "matching-runs", "run0", range("2026-11-02", "2026-11-30"), 409);
        assertThat(count(owner, "receivable_matching_run")).isEqualTo(10);
        assertThat(count(owner, "receivable_matching_usage_event")).isEqualTo(10);
        when(usageClock.instant()).thenReturn(java.time.Instant.parse("2026-11-30T15:00:00Z"));
        run(owner, "retryable", "2026-11-01", "2026-11-30");
        assertThat(getJson(owner, "matching-usage").path("completed").asInt()).isEqualTo(1);
    }

    @Test void currentReadUsesSeoulDbTimeAndNeverAllocatesRowsOrTrustsClientMonth() throws Exception {
        Owner owner = owner();
        when(usageClock.instant()).thenReturn(java.time.Instant.parse("2026-10-31T15:00:00Z"));
        for (int i=0;i<2;i++) {
            var current = getJson(owner, "matching-usage");
            assertThat(current.path("month").asText()).isEqualTo("2026-11");
            assertThat(current.path("timezone").asText()).isEqualTo("Asia/Seoul");
            assertThat(current.path("plan").asText()).isEqualTo("FREE");
            assertThat(current.path("policyVersion").asText()).isEqualTo("matching-usage-v1");
            assertThat(current.path("limit").asInt()).isEqualTo(10);
            assertThat(current.path("remaining").asInt()).isEqualTo(10);
        }
        mvc.perform(get(path(owner,"matching-usage?month=2027-01")).header("Authorization",owner.bearer())).andExpect(status().isBadRequest());
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
        // The production clock is PostgreSQL wall time, independent of the mocked test seam.
        var actual = new UsageClock(jdbc).instant();
        var db = jdbc.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class).toInstant();
        assertThat(java.time.Duration.between(actual,db).abs().toSeconds()).isLessThan(5);
    }

    @Test void concurrentDifferentKeysCompeteForLastSlotWithoutOvershoot() throws Exception {
        Owner owner=owner();
        for(int i=0;i<9;i++) run(owner,"fill"+i,"2026-11-01","2026-11-30");
        var pair=parallel(() -> response(owner,"a",range("2026-11-01","2026-11-30")), () -> response(owner,"b",range("2026-11-01","2026-11-30")));
        assertThat(pair.stream().map(Result::status)).containsExactlyInAnyOrder(201,429);
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isEqualTo(10);
        assertThat(count(owner,"receivable_matching_run")).isEqualTo(10);
        assertThat(count(owner,"receivable_matching_usage_event")).isEqualTo(10);
    }

    @Test void newMonthFirstRowRaceAndDuplicateRaceSerialize() throws Exception {
        Owner owner=owner();
        jdbc.update("UPDATE app_tenant SET plan='STANDARD' WHERE id=?",owner.tenant());
        var body=range("2026-01-01","2026-01-31");
        var duplicate=parallel(() -> response(owner,"duplicate",body), () -> response(owner,"duplicate",body));
        assertThat(duplicate.get(0).status()).isEqualTo(201);
        assertThat(duplicate.get(1)).isEqualTo(duplicate.get(0));
        assertThat(count(owner,"receivable_matching_usage_event")).isEqualTo(1);
        when(usageClock.instant()).thenReturn(java.time.Instant.parse("2026-11-30T15:00:00Z"));
        var race=parallel(() -> response(owner,"next1",body), () -> response(owner,"next2",body));
        assertThat(race.stream().map(Result::status)).containsExactlyInAnyOrder(201,429);
        assertThat(count(owner,"receivable_monthly_usage")).isEqualTo(2);
        assertThat(count(owner,"receivable_matching_usage_event")).isEqualTo(2);
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isEqualTo(1);
    }

    @Test void admissionMonthIsFrozenAcrossCompletionAndSameKeyRetries() throws Exception {
        Owner owner=owner();
        var november=java.time.Instant.parse("2026-11-30T14:59:59.999999Z");
        var december=java.time.Instant.parse("2026-11-30T15:00:00Z");
        when(usageClock.instant()).thenReturn(november,november,december);
        var original=run(owner,"month-end","2020-01-01","2020-01-31");
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isZero();
        assertThat(run(owner,"month-end","2020-01-01","2020-01-31")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT usage_month FROM receivable_matching_run WHERE tenant_id=?",java.time.LocalDate.class,owner.tenant())).hasToString("2026-11-01");
        assertThat(jdbc.queryForObject("SELECT usage_month FROM receivable_matching_usage_event WHERE tenant_id=?",java.time.LocalDate.class,owner.tenant())).hasToString("2026-11-01");
        assertThat(count(owner,"receivable_monthly_usage")).isEqualTo(1);
        verify(usageClock,times(3)).instant(); // Only first admission and GET; replay does not choose a month.
    }

    @Test void monthChangeWhileWaitingRollsBackAndSameKeyRetriesInNewMonth() throws Exception {
        Owner owner=owner();
        when(usageClock.instant()).thenReturn(java.time.Instant.parse("2026-11-30T14:59:59Z"), java.time.Instant.parse("2026-11-30T15:00:00Z"));
        send(owner,"matching-runs","boundary",range("2026-11-01","2026-11-30"),409)
                .andExpect(jsonPath("$.code").value("MATCHING_MONTH_CHANGED"));
        assertThat(count(owner,"receivable_matching_run")).isZero();
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
        run(owner,"boundary","2026-11-01","2026-11-30");
        assertThat(getJson(owner,"matching-usage").path("month").asText()).isEqualTo("2026-12");
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isEqualTo(1);
    }

    @Test void historicalCompletedRunReplaysWithoutRetroactiveCharge() throws Exception {
        Owner owner=owner();
        var old=run(owner,"historical","2026-11-01","2026-11-30");
        // Restore V11's shape: SUCCESS snapshot with no metering metadata or event.
        jdbc.update("DELETE FROM receivable_matching_usage_event WHERE tenant_id=?",owner.tenant());
        jdbc.update("DELETE FROM receivable_monthly_usage WHERE tenant_id=?",owner.tenant());
        jdbc.update("UPDATE receivable_matching_run SET usage_month=NULL WHERE tenant_id=?",owner.tenant());
        jdbc.update("UPDATE app_tenant SET plan='PRO' WHERE id=?",owner.tenant());
        assertThat(run(owner,"historical","2026-11-01","2026-11-30")).isEqualTo(old);
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
    }

    @Test void failedValidationAndSourceLimitLeaveNoUsageOrProvisionalRun() throws Exception {
        Owner owner=owner();
        send(owner,"matching-runs","retry",range("2026-11-30","2026-11-01"),400);
        payment(owner,null,"1","2026-11-02"); payment(owner,null,"1","2026-11-02");
        send(owner,"matching-runs","retry",range("2026-11-01","2026-11-30"),422);
        assertThat(count(owner,"receivable_matching_run")).isZero();
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
        jdbc.update("DELETE FROM receivable_registration_request WHERE tenant_id=?",owner.tenant());
        jdbc.update("DELETE FROM receivable_payment WHERE tenant_id=?",owner.tenant());
        run(owner,"retry","2026-11-01","2026-11-30");
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isEqualTo(1);
    }

    @Test void invalidSourceBalanceRollsBackAdmissionAndAllUsage() throws Exception {
        Owner owner=owner();
        var inv=invoice(owner,"Customer","100","2026-01-01","2026-11-20");
        var pay=payment(owner,"Customer","100","2026-11-10");
        UUID batch=UUID.randomUUID();
        jdbc.update("INSERT INTO receivable_allocation_batch (id,tenant_id,created_by,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",batch,owner.tenant(),owner.account());
        jdbc.update("INSERT INTO receivable_allocation_line (id,tenant_id,batch_id,line_number,payment_id,invoice_id,amount) VALUES (?,?,?,1,?,?,101)",UUID.randomUUID(),owner.tenant(),batch,UUID.fromString(id(pay)),UUID.fromString(id(inv)));
        send(owner,"matching-runs","corrupt",range("2026-11-01","2026-11-30"),503);
        assertThat(count(owner,"receivable_matching_run")).isZero();
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
    }

    @Test void snapshotLedgerAndCounterFailuresRollbackEverythingAndPermitSameKeyRetry() throws Exception {
        Owner owner=owner();
        for(String constraint : List.of("snapshot","ledger","counter")) {
            String table=switch(constraint) {case "snapshot" -> "receivable_matching_run";case "ledger" -> "receivable_matching_usage_event";default -> "receivable_monthly_usage";};
            String check=switch(constraint) {case "snapshot" -> "result_json IS NULL";case "ledger" -> "limit_count<0";default -> "completed=0";};
            jdbc.execute("ALTER TABLE "+table+" ADD CONSTRAINT ck_test_usage_failure CHECK ("+check+") NOT VALID");
            try {
                send(owner,"matching-runs",constraint,range("2026-11-01","2026-11-30"),503);
                assertThat(count(owner,"receivable_matching_run")).isZero();
                assertThat(count(owner,"receivable_monthly_usage")).isZero();
                assertThat(count(owner,"receivable_matching_usage_event")).isZero();
            } finally { jdbc.execute("ALTER TABLE "+table+" DROP CONSTRAINT ck_test_usage_failure"); }
        }
        run(owner,"ledger","2026-11-01","2026-11-30");
        assertThat(getJson(owner,"matching-usage").path("completed").asInt()).isEqualTo(1);
    }

    @Test void emptySuccessChargesOnceAndLivePlanDowngradePreservesActualCount() throws Exception {
        Owner owner=owner();
        assertThat(run(owner,"empty1","2026-11-01","2026-11-30").path("dataPresence").asText()).isEqualTo("NO_ELIGIBLE_SOURCES");
        run(owner,"empty2","2026-11-01","2026-11-30");
        jdbc.update("UPDATE app_tenant SET plan='STANDARD' WHERE id=?",owner.tenant());
        var current=getJson(owner,"matching-usage");
        assertThat(current.path("plan").asText()).isEqualTo("STANDARD");
        assertThat(current.path("limit").asInt()).isEqualTo(1);
        assertThat(current.path("completed").asInt()).isEqualTo(2);
        assertThat(current.path("remaining").asInt()).isZero();
        send(owner,"matching-runs","blocked",range("2026-11-01","2026-11-30"),429);
        assertThat(jdbc.queryForList("SELECT plan FROM receivable_matching_usage_event WHERE tenant_id=?",String.class,owner.tenant())).containsOnly("FREE");
        jdbc.update("UPDATE app_tenant SET plan='PRO' WHERE id=?",owner.tenant());
        assertThat(getJson(owner,"matching-usage").path("limit").asInt()).isZero();
        send(owner,"matching-runs","blocked",range("2026-11-01","2026-11-30"),429);
    }

    @Test void usageHttpAndDirectServiceRecheckTenantMembershipAndRevokedSession() throws Exception {
        Owner owner=owner(), other=owner();
        mvc.perform(get(path(owner,"matching-usage"))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(owner,"matching-usage")).header("Authorization",other.bearer())).andExpect(status().isForbidden());
        assertThatThrownBy(()->usage.current(owner.tenant())).isInstanceOf(AccountException.class);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(auth.authenticate(owner.token()),null,List.of()));
        SecurityContextHolder.setContext(context);
        assertThatThrownBy(()->usage.current(other.tenant())).isInstanceOf(AccountException.class);
        jdbc.update("DELETE FROM app_membership WHERE tenant_id=?",owner.tenant());
        assertThatThrownBy(()->usage.current(owner.tenant())).isInstanceOfSatisfying(AccountException.class,ex->assertThat(ex.status()).isEqualTo(403));
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE account_id=?",owner.account());
        assertThatThrownBy(()->usage.current(owner.tenant())).isInstanceOfSatisfying(AccountException.class,ex->assertThat(ex.status()).isEqualTo(401));
        assertThatThrownBy(()->service.create(owner.tenant(),"revoked",range("2026-11-01","2026-11-30"))).isInstanceOf(AccountException.class);
    }

    @Test void exhaustedQuotaNeverBlocksManualAllocationCancellationImportOrReads() throws Exception {
        Owner owner=owner();
        jdbc.update("UPDATE app_tenant SET plan='PRO' WHERE id=?",owner.tenant());
        send(owner,"matching-runs","blocked",range("2026-11-01","2026-11-30"),429);
        var inv=invoice(owner,"Customer","100","2026-01-01","2026-11-20");
        var pay=payment(owner,"Customer","100","2026-11-10");
        var batch=allocate(owner,pay,inv,"50",201);
        send(owner,"allocations/"+id(batch)+"/cancel","cancel",Map.of("reason","correction"),200);
        getJson(owner,"invoices"); getJson(owner,"payments"); getJson(owner,"matching-runs");
        String csv="booked_date,direction,amount,counterparty_name,description,source_line_id\r\n2026-11-02,IN,1,Customer,note,quota-import\r\n";
        var file=new org.springframework.mock.web.MockMultipartFile("file","bank.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(path(owner,"bank-imports")).file(file).param("bankAccountKey","primary")
                .header("Authorization",owner.bearer()).header("Idempotency-Key","quota-import"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importedCount").value(1));
        assertThat(count(owner,"receivable_bank_import")).isEqualTo(1);
        assertThat(count(owner,"receivable_monthly_usage")).isZero();
        assertThat(count(owner,"receivable_matching_usage_event")).isZero();
    }

    @Test void schemaRejectsCounterOvershootDuplicateEventAndCrossTenantRunLink() throws Exception {
        Owner owner=owner(),other=owner();
        var saved=run(owner,"run","2026-11-01","2026-11-30");
        assertThatThrownBy(()->jdbc.update("UPDATE receivable_monthly_usage SET completed=limit_count+1 WHERE tenant_id=?",owner.tenant())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE receivable_monthly_usage SET completed=-1 WHERE tenant_id=?",owner.tenant())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO receivable_matching_usage_event SELECT * FROM receivable_matching_usage_event WHERE tenant_id=?",owner.tenant())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO receivable_monthly_usage (tenant_id,usage_month,limit_count) VALUES (?,DATE '2026-11-01',10)",other.tenant());
        assertThatThrownBy(()->jdbc.update("INSERT INTO receivable_matching_usage_event (tenant_id,run_id,usage_month,plan,policy_version,limit_count,approved_at) VALUES (?,?,DATE '2026-11-01','FREE','test',10,CURRENT_TIMESTAMP)",other.tenant(),UUID.fromString(id(saved)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void configurablePolicyFailsClosedForPaidPlansAndRejectsInvalidSettings() {
        var defaults=new UsagePolicy(10,0,0,"test-v1");
        assertThat(defaults.limit("FREE")).isEqualTo(10);
        assertThat(defaults.limit("STANDARD")).isZero(); assertThat(defaults.limit("PRO")).isZero();
        assertThatThrownBy(()->defaults.limit("UNKNOWN")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new UsagePolicy(-1,0,0,"v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UsagePolicy(10,-1,0,"v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UsagePolicy(10,0,-1,"v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UsagePolicy(10,0,0,"")).isInstanceOf(IllegalArgumentException.class);
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
    private static Map<String,Object> range(String from, String to) { return Map.of("from", from, "to", to); }
    private static String id(JsonNode row) { return row.path("id").asText(); }
    private static String path(Owner owner, String suffix) { return "/api/v1/tenants/" + owner.tenant() + "/" + suffix; }
    private record Owner(UUID account, UUID tenant, String token) { String bearer() { return "Bearer " + token; } }
    private record Result(int status, JsonNode json) {}
}
