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

/**
 * OpenAI 兼容 API 适配器，支持 Chat Completions、Streaming 和 Embeddings。
 * 同时用于 DeepSeek 等兼容 OpenAI 接口格式的模型。
 */
@Slf4j
public class OpenAIProvider implements LLMProvider {

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public OpenAIProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String completion(String systemPrompt, String userPrompt, String model, Map<String, Object> params) {
        try {
            JSONObject body = buildRequestBody(systemPrompt, userPrompt, model, params);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject resp = JSON.parseObject(response.body());
                JSONArray choices = resp.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            }
            log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("OpenAI completion failed", e);
            return null;
        }
    }

    @Override
    public Flux<String> completionStream(String systemPrompt, String userPrompt, String model, Map<String, Object> params) {
        // SSE streaming via WebClient would be used in production;
        // here we fall back to non-streaming wrapped in a Flux
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
        try {
            JSONObject body = new JSONObject();
            body.put("model", model != null ? model : "text-embedding-ada-002");
            body.put("input", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject resp = JSON.parseObject(response.body());
                return resp.getJSONArray("data").getJSONObject(0)
                        .getJSONArray("embedding")
                        .toList(Float.class);
            }
            return List.of();
        } catch (Exception e) {
            log.error("OpenAI embed failed", e);
            return List.of();
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    private JSONObject buildRequestBody(String systemPrompt, String userPrompt, String model, Map<String, Object> params) {
        JSONObject body = new JSONObject();
        body.put("model", model != null ? model : "gpt-4");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        body.put("messages", messages);

        body.put("temperature", params.getOrDefault("temperature", 0.3));
        body.put("max_tokens", params.getOrDefault("maxTokens", 4096));

        return body;
    }
}
