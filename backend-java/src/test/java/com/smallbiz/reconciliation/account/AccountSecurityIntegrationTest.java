package com.smallbiz.reconciliation.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.smallbiz.reconciliation.aireview.ai.*;
import com.smallbiz.reconciliation.aireview.query.ReadOnlyReviewQueries;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"AI_API_KEY=synthetic-key-present", "app.ai.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test") // legacy flag is true in test.yml, but no legacy-dev profile: secure by default.
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class AccountSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AiReviewService service;
    @MockitoBean AiCompletionClient client;
    @MockitoBean ReadOnlyReviewQueries queries;

    @BeforeEach void clean() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM app_session");
        jdbc.update("DELETE FROM app_membership");
        jdbc.update("DELETE FROM app_tenant");
        jdbc.update("DELETE FROM app_account");
        reset(client,queries);
        when(client.configured()).thenReturn(true);
    }
    @Test void signupNormalizesLoginCreatesFreeMembershipAndNeverReturnsSecretsFromMe() throws Exception {
        var session = signup(" Owner@Example.COM ");
        String token = session.path("accessToken").asText();
        assertThat(token).hasSize(43);
        assertThat(session.path("expiresAtEpoch").asLong()).isGreaterThan(System.currentTimeMillis()/1000);
        var result = mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.login").value("owner@example.com"))
                .andExpect(jsonPath("$.tenants[0].plan").value("FREE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("password", "accessToken", token, "sessionHash");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_account",String.class)).startsWith("$2a$12$");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM app_session",String.class)).isEqualTo(AuthService.hashToken(token));
    }
    @Test void loginWrongPasswordUnknownLoginAndRevokedOrExpiredSessionsHaveSafeErrors() throws Exception {
        var session=signup("owner");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login","OWNER","password","wrong-password-123"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login","absent","password","wrong-password-123"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        String token=mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login","OWNER","password","password-123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization",bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(token))).andExpect(status().isUnauthorized());
        String first=session.path("accessToken").asText();
        jdbc.update("UPDATE app_session SET created_at_epoch=1,expires_at_epoch=2 WHERE token_hash=?",AuthService.hashToken(first));
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(first))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_session WHERE revoked_at_epoch IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @Test void twoAccountsCannotReadEachOthersTenantOrGlobalLegacyData() throws Exception {
        var a=signup("owner-a"); var b=signup("owner-b");
        String aToken=a.path("accessToken").asText(), bToken=b.path("accessToken").asText();
        JsonNode me=mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(aToken)))
                .andReturn().getResponse().getContentAsString());
        String tenant=me.path("tenants").path(0).path("id").asText();
        mvc.perform(get("/api/v1/tenants/"+tenant).header("Authorization",bearer(aToken))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tenants/"+tenant).header("Authorization",bearer(bToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/tenants/"+UUID.randomUUID()).header("Authorization",bearer(bToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenants/"+tenant)).andExpect(status().isUnauthorized());
        for(String path:List.of("/api/v1/vendors","/api/v1/uploads","/api/v1/ai-review/summary?date=2026-10-31")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",bearer(aToken))).andExpect(status().isForbidden());
        }
    }
    @Test void clientCannotChoosePlanMembershipOrTenantAndDuplicateLoginLeavesNoOrphan() throws Exception {
        for(String field:List.of("plan","accountId","tenantId","role","passwordHash")) {
            var body=new java.util.HashMap<String,Object>(Map.of("login","owner","password","password-123456","tenantName","Shop"));
            body.put(field,"PRO");
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        signup("owner");
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login","OWNER","password","password-123456","tenantName","Other"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_UNAVAILABLE"));
        assertCounts(1);
    }
    @Test void concurrentNormalizedSignupHasOneAccountTenantMembershipAndSession() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> attempt=()-> {
                try {auth.signup("RACE","password-123456","Shop"); return "created";}
                catch(AccountException ex) {return ex.code();}
            };
            var results=pool.invokeAll(List.of(attempt,attempt));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder("created","LOGIN_UNAVAILABLE");
        }
        assertCounts(1);
    }
    @Test void lateSignupStorageFailureRollsBackAccountTenantMembershipAndSession() throws Exception {
        // Dedicated test DB only. Force the final signup INSERT to fail after all ownership rows exist.
        jdbc.execute("ALTER TABLE app_session ADD CONSTRAINT ck_test_reject_sessions CHECK (false) NOT VALID");
        try {
            mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("login","rollback-owner","password","password-123456","tenantName","Shop"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_STORAGE_UNAVAILABLE"));
            assertCounts(0);
        } finally {jdbc.execute("ALTER TABLE app_session DROP CONSTRAINT ck_test_reject_sessions");}
    }
    @Test void firewallRejectsAmbiguousLegacyPathsAndContextDoesNotLeakAcrossRequests() throws Exception {
        var session=signup("owner");String token=session.path("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer(token))).andExpect(status().isOk());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        for(String path:List.of("/api/v1/vendors;ignored", "/api//v1/vendors", "/api/v1/auth/../vendors"))
            mvc.perform(get(path).header("Authorization",bearer(token))).andExpect(status().is4xxClientError());
        mvc.perform(get("/local/api/v1/vendors").contextPath("/local").header("Authorization",bearer(token)))
                .andExpect(status().isForbidden());
    }
    @Test void revokedOrExpiredPrincipalCannotBeReusedForDirectServiceInvocation() throws Exception {
        var session=signup("owner");String token=session.path("accessToken").asText();
        var principal=auth.authenticate(token);
        jdbc.update("UPDATE app_session SET revoked_at_epoch=1 WHERE token_hash=?",principal.sessionHash());
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of()));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(()->service.review("2026-10-31"))
                    .isInstanceOfSatisfying(AccountException.class,ex->assertThat(ex.code()).isEqualTo("AUTH_REQUIRED"));
        } finally {SecurityContextHolder.clearContext();}
        verifyNoInteractions(queries,client);
    }
    @Test void bcryptByteLimitRejectsTruncationAndMalformedBearerCannotBecomeAnonymous() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login","owner","password","한".repeat(25),"tenantName","Shop"))))
                .andExpect(status().isBadRequest());
        assertCounts(0);
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Basic invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer one","Bearer two")).andExpect(status().isUnauthorized());
    }
    @Test void freeStandardUnauthenticatedAndProNeverReachAiQueriesOrClientWithKeyAndSwitchEnabled() throws Exception {
        mvc.perform(post("/api/v1/ai-review/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-10-31\"}"))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(()->service.review("2026-10-31")).isInstanceOf(AccountException.class);
        var session=signup("owner");String token=session.path("accessToken").asText();
        for(String plan:List.of("FREE","STANDARD","PRO")) {
            jdbc.update("UPDATE app_tenant SET plan=?",plan);
            mvc.perform(post("/api/v1/ai-review/reviews").header("Authorization",bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-10-31\",\"plan\":\"PRO\"}"))
                    .andExpect(status().is(plan.equals("PRO")?503:403))
                    .andExpect(jsonPath("$.code").value(plan.equals("PRO")?"AI_TENANT_TOOLS_UNAVAILABLE":"AI_PLAN_REQUIRED"));
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(auth.authenticate(token),null,List.of()));
            SecurityContextHolder.setContext(context);
            try {assertThatThrownBy(()->service.review("2026-10-31")).isInstanceOf(AiReviewException.class);}
            finally {SecurityContextHolder.clearContext();}
        }
        mvc.perform(get("/api/v1/ai-review/capabilities").header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        verifyNoInteractions(queries,client);
    }
    private void assertCounts(int expected) {
        for(String table:List.of("app_account","app_tenant","app_membership","app_session"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).as(table).isEqualTo(expected);
    }
    private JsonNode signup(String login) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login",login,"password","password-123456","tenantName","Shop"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String json(Object value) {return mapper.writeValueAsString(value);}
    private static String bearer(String token) {return "Bearer "+token;}
}
