package com.smallbiz.reconciliation.aireview.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class OpenAiCompletionClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final OpenAiCompletionClient client = new OpenAiCompletionClient("test-only-value", "test-model", mapper, http, mock(com.smallbiz.reconciliation.account.AiAccessPolicy.class));

    @Test void directProviderInvocationFailsClosedBeforeHttpEvenWithAKey() {
        var auth=mock(com.smallbiz.reconciliation.account.AuthService.class);
        var tenants=mock(com.smallbiz.reconciliation.account.TenantAccess.class);
        var policy=new com.smallbiz.reconciliation.account.AiAccessPolicy(auth,tenants,true);
        var protectedClient=new OpenAiCompletionClient("synthetic-key-present","test-model",mapper,http,policy);
        assertThrows(com.smallbiz.reconciliation.account.AccountException.class,()->protectedClient.complete(Map.of()));
        verifyNoInteractions(http,auth,tenants);
    }
    @Test void sendsOnlyToFixedHttpsEndpointAndParsesValidResponse() throws Exception {
        respond(200,"{\"choices\":[]}");
        assertTrue(client.complete(Map.of("model","test-model")).path("choices").isArray());
        var request=ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.openai.com/v1/chat/completions",request.getValue().uri().toString());
        assertEquals(35,request.getValue().timeout().orElseThrow().toSeconds());
    }
    @Test void missingKeyDoesNotAttemptHttpRequest() {
        var disabled=new OpenAiCompletionClient("","test-model",mapper,http,mock(com.smallbiz.reconciliation.account.AiAccessPolicy.class));
        assertEquals("AI_NOT_CONFIGURED",assertThrows(AiReviewException.class,()->disabled.complete(Map.of())).code());
        verifyNoInteractions(http);
    }
    @Test void authFailureDoesNotExposeProviderBody() throws Exception {
        respond(401,"sensitive upstream detail");
        var e=assertThrows(AiReviewException.class,()->client.complete(Map.of()));
        assertEquals("AI_AUTH_FAILED",e.code());
        assertFalse(e.getMessage().contains("sensitive"));
        assertNull(e.getCause());
    }
    @Test void rateLimitIsRetryableAndHasDistinctCode() throws Exception {
        respond(429,"provider detail");
        var e=assertThrows(AiReviewException.class,()->client.complete(Map.of()));
        assertEquals("AI_RATE_LIMITED",e.code()); assertEquals(429,e.status());
    }
    @Test void timeoutIsReportedWithoutRawException() throws Exception {
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("upstream detail")));
        var e=assertThrows(AiReviewException.class,()->client.complete(Map.of()));
        assertEquals("AI_TIMEOUT",e.code()); assertEquals(504,e.status()); assertNull(e.getCause());
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        respond(200,"x".repeat(1_048_577));
        assertEquals("AI_INVALID_RESPONSE",assertThrows(AiReviewException.class,()->client.complete(Map.of())).code());
    }
    @Test void malformedJsonIsRejectedWithoutRawProviderText() throws Exception {
        respond(200,"not-json provider text");
        assertEquals("AI_INVALID_RESPONSE",assertThrows(AiReviewException.class,()->client.complete(Map.of())).code());
    }
    @Test void stalledBodyIsCancelledAndReportedAsTimeout() {
        CompletableFuture<HttpResponse<Object>> pending = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        var bounded = new OpenAiCompletionClient("test-only-value", "test-model", mapper, http, Duration.ofMillis(20), mock(com.smallbiz.reconciliation.account.AiAccessPolicy.class));
        assertEquals("AI_TIMEOUT", assertThrows(AiReviewException.class, () -> bounded.complete(Map.of())).code());
        assertTrue(pending.isCancelled());
    }
    @Test void responseSubscriberCancelsAsSoonAsSizeLimitIsExceeded() {
        var body = new OpenAiCompletionClient.BoundedBody();
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[1_048_577])));
        verify(subscription).cancel();
        assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    @SuppressWarnings("unchecked")
    private void respond(int status,String body) throws Exception {
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));
    }
}
