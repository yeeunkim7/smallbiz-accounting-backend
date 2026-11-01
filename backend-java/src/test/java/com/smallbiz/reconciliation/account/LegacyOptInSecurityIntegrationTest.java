package com.smallbiz.reconciliation.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test","legacy-dev"})
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class LegacyOptInSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Test void explicitLegacyFixtureAllowsAnonymousButNeverAccountOrAiReview() throws Exception {
        mvc.perform(get("/api/v1/vendors")).andExpect(status().isOk());
        var session=auth.signup("fixture-"+java.util.UUID.randomUUID(),"password-123456","Shop");
        mvc.perform(get("/api/v1/vendors").header("Authorization","Bearer "+session.accessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vendors").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai-review/reviews").contentType("application/json").content("{\"date\":\"2026-10-31\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai-review/reviews").header("Authorization","Bearer "+session.accessToken())
                .contentType("application/json").content("{\"date\":\"2026-10-31\"}"))
                .andExpect(status().isForbidden());
    }
}
