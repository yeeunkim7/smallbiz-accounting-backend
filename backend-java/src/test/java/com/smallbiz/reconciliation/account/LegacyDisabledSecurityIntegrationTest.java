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

@SpringBootTest(properties="app.legacy.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles({"test","legacy-dev"})
@ContextConfiguration(initializers = RejectDevelopmentDatabaseInitializer.class)
class LegacyDisabledSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void profileAloneCannotExposeLegacyData() throws Exception {
        mvc.perform(get("/api/v1/vendors")).andExpect(status().isUnauthorized());
    }
}
