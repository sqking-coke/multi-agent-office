package com.agentoffice.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
public class AnthropicProvider implements LLMProvider {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String completion(String systemPrompt, String userPrompt, String model, Map<String, Object> params) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model != null ? model : "claude-sonnet-4-6");
            body.put("system", systemPrompt);
            body.put("max_tokens", params.getOrDefault("maxTokens", 4096));

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            body.put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject resp = JSON.parseObject(response.body());
                JSONArray content = resp.getJSONArray("content");
                if (content != null && !content.isEmpty()) {
                    return content.getJSONObject(0).getString("text");
                }
            }
            log.error("Anthropic API error: {} - {}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("Anthropic completion failed", e);
            return null;
        }
    }

    @Override
    public Flux<String> completionStream(String systemPrompt, String userPrompt, String model, Map<String, Object> params) {
        return Flux.create(sink -> {
            String result = completion(systemPrompt, userPrompt, model, params);
            if (result != null) {
                sink.next(result);
            }
            sink.complete();
        });
    }

    @Override
    public List<Float> embed(String text, String model) {
        // Anthropic does not provide embedding API; return empty
        log.warn("Anthropic does not support embeddings API");
        return List.of();
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }
}
