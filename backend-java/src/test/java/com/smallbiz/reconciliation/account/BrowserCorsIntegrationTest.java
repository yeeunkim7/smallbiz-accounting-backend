package com.smallbiz.reconciliation.account;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.smallbiz.reconciliation.vendor.RejectDevelopmentDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.DefaultCorsProcessor;

@SpringBootTest(properties="app.cors.allowed-origins=http://127.0.0.1:8081")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers=RejectDevelopmentDatabaseInitializer.class)
class BrowserCorsIntegrationTest {
    private static final String ORIGIN="http://127.0.0.1:8081";
    @Autowired MockMvc mvc;

    @Test void exactOriginPreflightAcceptsBearerAndIdempotencyWithoutCookies() throws Exception {
        mvc.perform(options("/api/v1/auth/signup").header("Origin",ORIGIN)
                .header("Access-Control-Request-Method","POST")
                .header("Access-Control-Request-Headers","authorization,content-type,idempotency-key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
    @Test void unknownOriginAndPortAreRejected() throws Exception {
        for (String origin:java.util.List.of("https://untrusted.example","http://127.0.0.1:8082"))
            mvc.perform(options("/api/v1/auth/signup").header("Origin",origin)
                    .header("Access-Control-Request-Method","POST"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void allowedBrowserOriginDoesNotGrantAuthenticationAndNativeBehaviorIsRetained() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Origin",ORIGIN))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN))
                .andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void unapprovedMethodsAndHeadersAreRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/signup").header("Origin",ORIGIN)
                .header("Access-Control-Request-Method","DELETE")).andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/signup").header("Origin",ORIGIN)
                .header("Access-Control-Request-Method","POST")
                .header("Access-Control-Request-Headers","X-Plan")).andExpect(status().isForbidden());
    }
    @Test void emptyConfigurationRejectsCrossOriginPreflight() throws Exception {
        var source=new BrowserCorsConfiguration().browserCors("");
        var request=new MockHttpServletRequest("OPTIONS","/api/v1/auth/signup");
        request.addHeader("Origin",ORIGIN); request.addHeader("Access-Control-Request-Method","POST");
        var response=new MockHttpServletResponse();
        assertThat(new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request),request,response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void wildcardCredentialsPathsAndInvalidOriginsFailConfiguration() {
        for (String value:java.util.List.of("*","https://*.example.com","https://user:pass@example.com", "https://example.com/",
                "https://example.com/path","https://example.com?token=x","file:///app","https://example.com:70000"))
            assertThatThrownBy(() -> new BrowserCorsConfiguration().browserCors(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
