package com.smallbiz.reconciliation.aireview.ai;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiCompletionClient implements AiCompletionClient {
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/chat/completions");
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Duration timeout;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiCompletionClient(@Value("${AI_API_KEY:}") String apiKey,
                                  @Value("${AI_MODEL:gpt-4.1-mini}") String model,
                                  ObjectMapper mapper) {
        this(apiKey, model, mapper, proxyClient());
    }

    OpenAiCompletionClient(String apiKey, String model, ObjectMapper mapper, HttpClient http) {
        this(apiKey, model, mapper, http, Duration.ofSeconds(35));
    }

    OpenAiCompletionClient(String apiKey, String model, ObjectMapper mapper, HttpClient http, Duration timeout) {
        this.timeout = timeout;
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
        this.http = http;
    }

    private static HttpClient proxyClient() {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null || proxy.isBlank()) proxy = System.getenv("https_proxy");
        if (proxy != null && !proxy.isBlank()) {
            URI uri = URI.create(proxy);
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 8080 : uri.getPort())));
        }
        return builder.build();
    }

    @Override public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    @Override public String model() { return model; }

    @Override public JsonNode complete(Map<String, Object> payload) {
        if (!configured()) throw new AiReviewException("AI_NOT_CONFIGURED", 503,
                "AI 검토를 준비하고 있습니다. 잠시 후 다시 시도해 주세요.");
        try {
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            // Await the complete bounded body, not just headers. A stalled body must release the review slot.
            var pending = http.sendAsync(request, ignored -> new BoundedBody());
            HttpResponse<byte[]> response;
            try { response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException e) { pending.cancel(true); throw new HttpTimeoutException("body timeout"); }
            catch (InterruptedException e) { pending.cancel(true); throw e; }
            {
                if (response.statusCode() == 401 || response.statusCode() == 403)
                    throw new AiReviewException("AI_AUTH_FAILED", 503, "AI 서비스 연결을 확인하고 있습니다. 잠시 후 다시 시도해 주세요.");
                if (response.statusCode() == 429)
                    throw new AiReviewException("AI_RATE_LIMITED", 429, "AI 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new AiReviewException("AI_UPSTREAM_ERROR", 502, "AI 검토를 완료하지 못했습니다. 다시 시도해 주세요.");
                byte[] bytes = response.body();
                if (bytes.length > MAX_RESPONSE_BYTES)
                    throw new AiReviewException("AI_INVALID_RESPONSE", 502, "AI 응답을 확인하지 못했습니다. 다시 시도해 주세요.");
                return mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (HttpTimeoutException e) {
            throw new AiReviewException("AI_TIMEOUT", 504, "AI 검토 시간이 길어지고 있습니다. 다시 시도해 주세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiReviewException("AI_INTERRUPTED", 503, "AI 검토가 중단되었습니다. 다시 시도해 주세요.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AiReviewException safe) throw safe;
            if (e.getCause() instanceof HttpTimeoutException)
                throw new AiReviewException("AI_TIMEOUT", 504, "AI 검토 시간이 길어지고 있습니다. 다시 시도해 주세요.");
            throw new AiReviewException("AI_CONNECTION_FAILED", 502, "AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (AiReviewException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiReviewException("AI_INVALID_RESPONSE", 502, "AI 응답을 확인하지 못했습니다. 다시 시도해 주세요.");
        }
    }
    /** Cancel oversized bodies while receiving, before allocating unbounded provider data. */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if ((long) bytes.size() + item.remaining() > MAX_RESPONSE_BYTES) {
                    subscription.cancel();
                    body.completeExceptionally(new AiReviewException("AI_INVALID_RESPONSE", 502,
                            "AI 응답을 확인하지 못했습니다. 다시 시도해 주세요."));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }

}
