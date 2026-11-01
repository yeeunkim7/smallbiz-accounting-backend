package com.smallbiz.reconciliation.receivables.bankimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.smallbiz.reconciliation.account.AccountException;
import com.smallbiz.reconciliation.account.AuthService;
import com.smallbiz.reconciliation.aireview.ai.AiCompletionClient;
import com.smallbiz.reconciliation.aireview.query.ReadOnlyReviewQueries;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"AI_API_KEY=synthetic-key-present","app.ai.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers=RejectDevelopmentDatabaseInitializer.class)
class BankImportIntegrationTest {
    private static final String HEADER="booked_date,direction,amount,counterparty_name,description,source_line_id\r\n";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired BankImportService service;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean AiCompletionClient ai;
    @MockitoBean ReadOnlyReviewQueries queries;
    private final List<Owner> owners=new ArrayList<>();
    @BeforeEach void prepare() { SecurityContextHolder.clearContext(); reset(ai,queries); }
    @AfterEach void cleanupOwnRows() {
        SecurityContextHolder.clearContext();
        try { verifyNoInteractions(ai,queries); }
        finally {
            for (var owner:owners) new TransactionTemplate(manager).executeWithoutResult(status -> {
                for (String table:List.of("receivable_allocation_command","receivable_allocation_audit","receivable_allocation_cancellation",
                        "receivable_allocation_line","receivable_allocation_batch","receivable_bank_import_request","receivable_bank_transaction",
                        "receivable_payment","receivable_bank_source_row","receivable_bank_import","receivable_registration_request",
                        "receivable_invoice","receivable_counterparty"))
                    jdbc.update("DELETE FROM "+table+" WHERE tenant_id=?",owner.tenant());
                jdbc.update("DELETE FROM app_session WHERE account_id=?",owner.account());
                jdbc.update("DELETE FROM app_membership WHERE tenant_id=?",owner.tenant());
                jdbc.update("DELETE FROM app_tenant WHERE id=?",owner.tenant());
                jdbc.update("DELETE FROM app_account WHERE id=?",owner.account());
            });
        }
    }

    @Test void importRetainsLegitimateEqualPaymentsAndOriginalCellsWithBomAndMultilineCsv() throws Exception {
        Owner owner=owner();
        String csv="\uFEFF"+HEADER+"2026-01-01,IN,100, Kim ,\"memo,\nnext\",txn-1\r\n"
                +"2026-01-01,IN,100, Kim ,memo,txn-2\r\n"+row("txn-3","OUT","40");
        var result=upload(owner,"one","primary",csv);
        assertThat(result.path("rowCount").asInt()).isEqualTo(3);
        assertThat(result.path("importedCount").asInt()).isEqualTo(2);
        assertThat(result.path("withdrawalCount").asInt()).isEqualTo(1);
        var rows=read(owner,"bank-imports/"+id(result)+"/rows").path("items");
        assertThat(rows.get(0).path("originalCells").get(3).asText()).isEqualTo(" Kim ");
        assertThat(rows.get(0).path("originalCells").get(4).asText()).isEqualTo("memo,\nnext");
        assertThat(rows.get(1).path("recordNumber").asInt()).isEqualTo(3);
        assertThat(rows.get(0).path("payerName").asText()).isEqualTo("Kim");
        assertThat(rows.get(0).path("paymentId")).isNotEqualTo(rows.get(1).path("paymentId"));
        assertThat(rows.get(2).path("paymentId").isNull()).isTrue();
        var payments=read(owner,"payments").path("items");
        assertThat(payments.size()).isEqualTo(2);
        assertThat(payments.get(0).path("sourceKind").asText()).isEqualTo("BANK_CSV");
        assertThat(count(owner,"receivable_allocation_line")).isZero();
    }

    @Test void tenantAuthAndForeignImportAndPaymentSourceIdsAreNeverLeaked() throws Exception {
        Owner a=owner(),b=owner();
        var imported=upload(a,"first","primary",HEADER+row("t","IN","5"));
        var source=read(a,"bank-imports/"+id(imported)+"/rows").path("items").get(0);
        for (String suffix:List.of("bank-imports","bank-imports/"+id(imported),"bank-imports/"+id(imported)+"/rows","payments/"+source.path("paymentId").asText()+"/source")) {
            mvc.perform(get(path(a,suffix))).andExpect(status().isUnauthorized());
            mvc.perform(get(path(a,suffix)).header("Authorization",b.bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(path(a,"bank-imports")).file(file(HEADER+row("t","IN","5"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(path(a,"bank-imports")).header("Authorization",b.bearer()))
                .andExpect(status().isForbidden());
        for (String suffix:List.of("bank-imports/"+id(imported),"bank-imports/"+id(imported)+"/rows","payments/"+source.path("paymentId").asText()+"/source"))
            mvc.perform(get(path(b,suffix)).header("Authorization",b.bearer())).andExpect(status().isNotFound());
        assertThat(read(b,"bank-imports").path("items")).isEmpty();
        // Identical identity/file is independent in another tenant.
        upload(b,"first","primary",HEADER+row("t","IN","5"));
        assertThat(count(b,"receivable_payment")).isEqualTo(1);
    }

    @Test void unicodeTextUsesManualCodePointAndWhitespaceRulesWhilePreservingOriginals() throws Exception {
        Owner owner=owner(); String payer="😀".repeat(100),note="😀".repeat(1000);
        var imported=upload(owner,"unicode","primary",HEADER+"2026-01-01,IN,1,\u2003"+payer+"\u2003,\u2003"+note+"\u2003,t\r\n");
        var row=read(owner,"bank-imports/"+id(imported)+"/rows").path("items").get(0);
        assertThat(row.path("payerName").asText()).isEqualTo(payer);
        assertThat(row.path("note").asText()).isEqualTo(note);
        assertThat(row.path("originalCells").get(3).asText()).isEqualTo("\u2003"+payer+"\u2003");
        send(owner,"long-unicode","primary",file(HEADER+"2026-01-01,IN,1,Kim,"+note+"😀,u\r\n"),400)
                .andExpect(jsonPath("$.field").value("description"));
        assertThat(count(owner,"receivable_payment")).isEqualTo(1);
    }

    @Test void fileReplayIgnoresSafeFilenameRenameAndNewKeyButAccountsRemainDistinct() throws Exception {
        Owner owner=owner(); String csv=HEADER+row("t","IN","5");
        var first=upload(owner,"first","primary",csv);
        var replay=mapper.readTree(send(owner,"first","primary",new MockMultipartFile("file","renamed.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)),201)
                .andReturn().getResponse().getContentAsString());
        assertThat(replay).isEqualTo(first);
        assertThat(upload(owner,"second","primary",csv)).isEqualTo(first);
        assertThat(id(upload(owner,"third","secondary",csv))).isNotEqualTo(id(first));
        send(owner,"first","primary",file(HEADER+row("different","IN","5")),409).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        send(owner,"first","secondary",file(csv),409).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count(owner,"receivable_bank_import")).isEqualTo(2);
        assertThat(count(owner,"receivable_bank_import_request")).isEqualTo(3);
        assertThat(count(owner,"receivable_payment")).isEqualTo(2);
    }

    @Test void overlapsPreserveEachFilesEvidenceAndChangedStableIdentityRollsBackEarlierRows() throws Exception {
        Owner owner=owner();
        var first=upload(owner,"first","primary",HEADER+row("t1","IN","5"));
        var next=upload(owner,"next","primary",HEADER+row("t1","IN","5")+row("t2","IN","5"));
        assertThat(next.path("importedCount").asInt()).isEqualTo(1);
        assertThat(next.path("duplicateCount").asInt()).isEqualTo(1);
        var a=read(owner,"bank-imports/"+id(first)+"/rows").path("items").get(0);
        var b=read(owner,"bank-imports/"+id(next)+"/rows").path("items").get(0);
        assertThat(a.path("id")).isNotEqualTo(b.path("id"));
        assertThat(a.path("paymentId")).isEqualTo(b.path("paymentId"));
        assertThat(b.path("firstSourceRowId")).isEqualTo(a.path("id"));
        String rejected=send(owner,"retry","primary",file(HEADER+row("new","IN","77")+row("t1","IN","6")),409)
                .andExpect(jsonPath("$.code").value("BANK_TRANSACTION_CONFLICT")).andReturn().getResponse().getContentAsString();
        assertThat(rejected).doesNotContain("77","t1","INSERT","receivable_");
        assertThat(count(owner,"receivable_payment")).isEqualTo(2);
        assertThat(count(owner,"receivable_bank_source_row")).isEqualTo(3);
        assertThat(count(owner,"receivable_bank_import_request")).isEqualTo(2);
        // Late conflict reservation rolls back, so same key can be corrected.
        upload(owner,"retry","primary",HEADER+row("new","IN","77"));
        for (String changed:List.of("2026-01-02,IN,5,Kim,memo,t1\r\n","2026-01-01,OUT,5,Kim,memo,t1\r\n",
                "2026-01-01,IN,5,Other,memo,t1\r\n","2026-01-01,IN,5,Kim,changed,t1\r\n"))
            send(owner,UUID.randomUUID().toString(),"primary",file(HEADER+changed),409);
    }

    @Test void withdrawalsOnlyHistoryIsDataWithoutAnIncomingPaymentInference() throws Exception {
        Owner owner=owner();
        assertThat(read(owner,"bank-imports").path("items")).isEmpty();
        var data=upload(owner,"out","primary",HEADER+row("t","OUT","10"));
        assertThat(data.path("importedCount").asInt()).isZero();
        assertThat(data.path("withdrawalCount").asInt()).isEqualTo(1);
        assertThat(read(owner,"bank-imports").path("items").size()).isEqualTo(1);
        assertThat(read(owner,"payments").path("items")).isEmpty();
        var overlap=upload(owner,"overlap","primary",HEADER+row("t","OUT","10")+row("u","OUT","10"));
        assertThat(overlap.path("withdrawalCount").asInt()).isEqualTo(2);
        assertThat(count(owner,"receivable_bank_transaction")).isEqualTo(2);
        send(owner,"changed-out","primary",file(HEADER+row("t","OUT","11")),409);
    }

    @Test void strictFinancialValidationAndDuplicateSourceIdsRejectWholeFileWithSafeRecordField() throws Exception {
        Owner owner=owner(); int i=0;
        for (String amount:List.of("0","-1","+1","01","1.0","1e3"," 1","1000000000001","9223372036854775808","１")) {
            send(owner,"amount-"+i++,"primary",file(HEADER+row("valid","IN","1")+row("bad","IN",amount)),400)
                    .andExpect(jsonPath("$.recordNumber").value(3)).andExpect(jsonPath("$.field").value("amount"));
        }
        for (String date:List.of("0000-01-01","2026-02-29","2026-1-01"," 2026-01-01","10000-01-01"))
            send(owner,"date-"+i++,"primary",file(HEADER+date+",IN,1,Kim,memo,t\r\n"),400)
                    .andExpect(jsonPath("$.recordNumber").value(2)).andExpect(jsonPath("$.field").value("booked_date"));
        for (String bad:List.of(HEADER+row("same","IN","1")+row("same","IN","1"),HEADER,
                HEADER+"2026-01-01,IN,1,Kim,memo,\r\n",HEADER+"2026-01-01,IN,1,"+"x".repeat(101)+",memo,t\r\n",
                HEADER+"2026-01-01,IN,1,Kim,"+"x".repeat(1001)+",t\r\n",HEADER+row("t","ZERO","1")))
            send(owner,"invalid-"+i++,"primary",file(bad),400);
        assertThat(count(owner,"receivable_bank_import_request")).isZero();
        assertThat(count(owner,"receivable_payment")).isZero();
    }

    @Test void malformedCsvEncodingUnexpectedHeadersAndControlCharactersReturnNoRawEvidence() throws Exception {
        Owner owner=owner(); int i=0;
        for (String csv:List.of(HEADER+"2026-01-01,IN,1,Kim,\"private-secret,t\r\n",HEADER+"\r\n",HEADER+"1,2,3\r\n",
                "거래일시,거래구분,기재내용,출금금액,입금금액,잔액,취급점\r\n",HEADER.replace("amount","direction"),
                HEADER+"2026-01-01,IN,1,Kim,private-secret\u0000,t\r\n")) {
            String body=send(owner,"bad-"+i++,"primary",file(csv),400).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("private-secret","CSVParser","Exception","SELECT");
        }
        byte[] bytes=(HEADER+row("t","IN","1")).getBytes(StandardCharsets.UTF_8); bytes[bytes.length-3]=(byte)0xFF;
        send(owner,"encoding","primary",new MockMultipartFile("file","bank.csv","text/csv",bytes),400)
                .andExpect(jsonPath("$.field").value("encoding"));
        assertThat(count(owner,"receivable_bank_import")).isZero();
    }

    @Test void defaultRowAndActualStreamByteLimitsAreEnforcedBeforeStorage() throws Exception {
        Owner owner=owner();
        StringBuilder csv=new StringBuilder(HEADER);
        for (int i=0;i<1000;i++) csv.append(row("t"+i,"OUT","1"));
        assertThat(upload(owner,"boundary","primary",csv.toString()).path("rowCount").asInt()).isEqualTo(1000);
        send(owner,"too-many","primary",file(csv+row("extra","IN","1")),413).andExpect(jsonPath("$.field").value("rows"));
        byte[] large=new byte[1048577];
        send(owner,"too-large","primary",new MockMultipartFile("file","bank.csv","text/csv",large),413);
        var lyingFile=new MockMultipartFile("file","bank.csv","text/csv",large) {
            @Override public long getSize() { return 1; }
        };
        authenticate(owner);
        assertThatThrownBy(() -> service.importFile(owner.tenant(),"lying","primary",lyingFile))
                .isInstanceOfSatisfying(BankImportException.class,ex -> assertThat(ex.status()).isEqualTo(413));
        assertThat(count(owner,"receivable_bank_import_request")).isEqualTo(1);
    }

    @Test void missingKeyAccountFileAndUnsafeFilenameAreRejectedAndListsAreBounded() throws Exception {
        Owner owner=owner();
        mvc.perform(multipart(path(owner,"bank-imports")).file(file(HEADER+row("t","IN","1"))).param("bankAccountKey","primary")
                .header("Authorization",owner.bearer())).andExpect(status().isBadRequest());
        for (String account:List.of("","has space","x".repeat(101))) send(owner,"key",account,file(HEADER+row("t","IN","1")),400);
        for (String key:List.of("","has space","x".repeat(101))) send(owner,key,"primary",file(HEADER+row("t","IN","1")),400);
        mvc.perform(multipart(path(owner,"bank-imports")).param("bankAccountKey","primary").header("Idempotency-Key","missing")
                .header("Authorization",owner.bearer())).andExpect(status().isBadRequest());
        send(owner,"filename","primary",new MockMultipartFile("file","bad\nname.csv","text/csv",(HEADER+row("t","IN","1")).getBytes(StandardCharsets.UTF_8)),400);
        var a=upload(owner,"a","primary",HEADER+row("a","IN","1"));
        var b=upload(owner,"b","primary",HEADER+row("b","IN","1")+row("c","IN","1"));
        jdbc.update("UPDATE receivable_bank_import SET created_at=TIMESTAMPTZ '2026-01-01 00:00:00Z' WHERE tenant_id=?",owner.tenant());
        var first=read(owner,"bank-imports?size=1");
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(read(owner,"bank-imports?size=1&page=1").path("hasNext").asBoolean()).isFalse();
        assertThat(read(owner,"bank-imports/"+id(b)+"/rows?size=1").path("hasNext").asBoolean()).isTrue();
        assertThat(read(owner,"bank-imports/"+id(b)+"/rows?size=1&page=1").path("items").get(0).path("recordNumber").asInt()).isEqualTo(3);
        for (String query:List.of("?size=101","?size=0","?page=-1")) for (String suffix:List.of("bank-imports","bank-imports/"+id(a)+"/rows"))
            mvc.perform(get(path(owner,suffix+query)).header("Authorization",owner.bearer())).andExpect(status().isBadRequest());
        assertThat(read(owner,"bank-imports?page=2147483647&size=100").path("items")).isEmpty();
    }

    @Test void concurrentSameFileAndKeyReplayOneResultAndDifferentPayloadKeyRaceConflicts() throws Exception {
        Owner owner=owner(); var barrier=new CyclicBarrier(2); String csv=HEADER+row("t","IN","1");
        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<JsonNode> same=() -> { barrier.await(10,TimeUnit.SECONDS); return upload(owner,"same","primary",csv); };
            var jobs=pool.invokeAll(List.of(same,same));
            assertThat(jobs.get(0).get(20,TimeUnit.SECONDS)).isEqualTo(jobs.get(1).get(20,TimeUnit.SECONDS));
            List<Callable<Integer>> different=List.of("x","y").stream().<Callable<Integer>>map(t -> () -> {
                barrier.await(10,TimeUnit.SECONDS);
                return mvc.perform(multipart(path(owner,"bank-imports")).file(file(HEADER+row(t,"IN","1"))).param("bankAccountKey","primary")
                        .header("Authorization",owner.bearer()).header("Idempotency-Key","race")).andReturn().getResponse().getStatus();
            }).toList();
            var results=pool.invokeAll(different);
            assertThat(List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(count(owner,"receivable_bank_import")).isEqualTo(2);
        assertThat(count(owner,"receivable_payment")).isEqualTo(2);
    }

    @Test void concurrentDifferentKeysSameFileAndOverlappingReverseOrderRowsNeverDuplicatePayments() throws Exception {
        Owner owner=owner(); var barrier=new CyclicBarrier(2);
        try (var pool=Executors.newFixedThreadPool(2)) {
            List<Callable<JsonNode>> same=List.of("a","b").stream().<Callable<JsonNode>>map(key -> () -> {
                barrier.await(10,TimeUnit.SECONDS); return upload(owner,key,"primary",HEADER+row("t","IN","1"));
            }).toList();
            var files=pool.invokeAll(same);
            assertThat(files.get(0).get(20,TimeUnit.SECONDS)).isEqualTo(files.get(1).get(20,TimeUnit.SECONDS));
            List<Callable<JsonNode>> overlaps=List.of("c","d").stream().<Callable<JsonNode>>map(key -> () -> {
                barrier.await(10,TimeUnit.SECONDS);
                return upload(owner,key,"primary",HEADER+(key.equals("c") ? row("u","IN","2")+row("v","IN","2") : row("v","IN","2")+row("u","IN","2")));
            }).toList();
            var results=pool.invokeAll(overlaps);
            assertThat(results.get(0).get(20,TimeUnit.SECONDS).path("importedCount").asInt()+results.get(1).get(20,TimeUnit.SECONDS).path("importedCount").asInt()).isEqualTo(2);
        }
        assertThat(count(owner,"receivable_bank_import")).isEqualTo(3);
        assertThat(count(owner,"receivable_bank_import_request")).isEqualTo(4);
        assertThat(count(owner,"receivable_payment")).isEqualTo(3);
        assertThat(count(owner,"receivable_bank_source_row")).isEqualTo(5);
    }

    @Test void lateStorageFailureRollsBackAllRowsAndIdempotencyAndCanRetry() throws Exception {
        Owner owner=owner(); String csv=HEADER+row("a","IN","1")+row("b","IN","2");
        jdbc.execute("ALTER TABLE receivable_payment ADD CONSTRAINT ck_test_bank_fail_second CHECK (amount <> 2) NOT VALID");
        try {
            send(owner,"retry","primary",file(csv),503).andExpect(jsonPath("$.code").value("BANK_IMPORT_STORAGE_UNAVAILABLE"));
            for (String table:List.of("receivable_bank_import","receivable_bank_import_request","receivable_bank_source_row","receivable_bank_transaction","receivable_payment"))
                assertThat(count(owner,table)).isZero();
        } finally { jdbc.execute("ALTER TABLE receivable_payment DROP CONSTRAINT ck_test_bank_fail_second"); }
        upload(owner,"retry","primary",csv);
        assertThat(count(owner,"receivable_payment")).isEqualTo(2);
    }

    @Test void evidenceSurvivesExistingManualAllocationConfirmationAndCancellation() throws Exception {
        Owner owner=owner(); var imported=upload(owner,"import","primary",HEADER+row("t","IN","100"));
        var row=read(owner,"bank-imports/"+id(imported)+"/rows").path("items").get(0); String payment=row.path("paymentId").asText();
        var source=read(owner,"payments/"+payment+"/source");
        var cp=postJson(owner,"counterparties","c",Map.of("code","C","name","Kim"),201);
        var invoice=postJson(owner,"invoices","i",Map.of("counterpartyId",id(cp),"invoiceNumber","I","amount","100","issueDate","2026-01-01","dueDate","2026-01-01"),201);
        var allocation=postJson(owner,"allocations","allocate",Map.of("lines",List.of(Map.of("paymentId",payment,"invoiceId",id(invoice),"amount","100"))),201);
        assertThat(read(owner,"payments/"+payment+"/balance").path("unallocatedAmount").asText()).isEqualTo("0");
        postJson(owner,"allocations/"+id(allocation)+"/cancel","cancel",Map.of("reason","wrong payment"),200);
        assertThat(read(owner,"payments/"+payment+"/source")).isEqualTo(source);
        assertThat(read(owner,"bank-imports/"+id(imported)+"/rows").path("items").get(0)).isEqualTo(row);
        assertThat(read(owner,"payments/"+payment).path("sourceKind").asText()).isEqualTo("BANK_CSV");
        assertThat(read(owner,"payments/"+payment+"/balance").path("unallocatedAmount").asText()).isEqualTo("100");
        assertThat(upload(owner,"replay","primary",HEADER+row("t","IN","100"))).isEqualTo(imported);
    }

    @Test void directServicePathsCheckAuthenticationTenantAndRevocationBeforeLookupOrValidation() throws Exception {
        Owner a=owner(),b=owner();
        assertThatThrownBy(() -> service.list(a.tenant(),0,20)).isInstanceOf(AccountException.class);
        authenticate(a);
        assertThatThrownBy(() -> service.importFile(b.tenant(),null,null,null)).isInstanceOfSatisfying(AccountException.class,ex -> assertThat(ex.status()).isEqualTo(403));
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE account_id=?",a.account());
        for (Runnable action:List.<Runnable>of(() -> service.list(a.tenant(),0,20),() -> service.get(a.tenant(),UUID.randomUUID()),
                () -> service.rows(a.tenant(),UUID.randomUUID(),0,20),() -> service.paymentSource(a.tenant(),UUID.randomUUID()),
                () -> service.importFile(a.tenant(),null,null,null)))
            assertThatThrownBy(action::run).isInstanceOfSatisfying(AccountException.class,ex -> assertThat(ex.status()).isEqualTo(401));
    }

    @Test void compositeForeignKeysRejectCrossTenantSourceReferencesAndManualSourceKindInjection() throws Exception {
        Owner a=owner(),b=owner(); var imported=upload(a,"import","primary",HEADER+row("t","IN","1"));
        String source=read(a,"bank-imports/"+id(imported)+"/rows").path("items").get(0).path("id").asText();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO receivable_payment (id,tenant_id,amount,booked_date,source_kind,bank_source_row_id,created_by,created_at)
                VALUES (?,?,1,DATE '2026-01-01','BANK_CSV',?,?,CURRENT_TIMESTAMP)
                """,UUID.randomUUID(),b.tenant(),UUID.fromString(source),b.account())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO receivable_payment (id,tenant_id,amount,booked_date,source_kind,bank_source_row_id,created_by,created_at)
                VALUES (?,?,1,DATE '2026-01-01','MANUAL',?,?,CURRENT_TIMESTAMP)
                """,UUID.randomUUID(),a.tenant(),UUID.fromString(source),a.account())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Owner owner() throws Exception {
        var signup=mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("login","imp-"+UUID.randomUUID(),"password","password-123456","tenantName","Synthetic shop"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token=signup.path("accessToken").asText();
        var me=mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var owner=new Owner(UUID.fromString(me.path("accountId").asText()),UUID.fromString(me.path("tenants").get(0).path("id").asText()),token);
        owners.add(owner); return owner;
    }
    private JsonNode upload(Owner o,String key,String account,String csv) throws Exception {
        return mapper.readTree(send(o,key,account,file(csv),201).andReturn().getResponse().getContentAsString());
    }
    private ResultActions send(Owner o,String key,String account,MockMultipartFile file,int status) throws Exception {
        return mvc.perform(multipart(path(o,"bank-imports")).file(file).param("bankAccountKey",account).header("Authorization",o.bearer()).header("Idempotency-Key",key))
                .andExpect(status().is(status)).andExpect(header().string("Cache-Control","no-store"));
    }
    private JsonNode read(Owner o,String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(path(o,suffix)).header("Authorization",o.bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode postJson(Owner o,String suffix,String key,Map<String,Object> body,int status) throws Exception {
        return mapper.readTree(mvc.perform(post(path(o,suffix)).header("Authorization",o.bearer()).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }
    private void authenticate(Owner owner) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(auth.authenticate(owner.token()),null,List.of()));
        SecurityContextHolder.setContext(context);
    }
    private int count(Owner o,String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE tenant_id=?",Integer.class,o.tenant()); }
    private static String row(String id,String direction,String amount) { return "2026-01-01,"+direction+","+amount+",Kim,memo,"+id+"\r\n"; }
    private static MockMultipartFile file(String csv) { return new MockMultipartFile("file","bank.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)); }
    private static String id(JsonNode node) { return node.path("id").asText(); }
    private static String path(Owner owner,String suffix) { return "/api/v1/tenants/"+owner.tenant()+"/"+suffix; }
    private record Owner(UUID account,UUID tenant,String token) { String bearer() { return "Bearer "+token; } }
}
