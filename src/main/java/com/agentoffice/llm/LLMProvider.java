package com.agentoffice.llm;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Unified LLM provider interface — abstracts away provider-specific APIs.
 */
public interface LLMProvider {

    /** Synchronous completion. */
    String completion(String systemPrompt, String userPrompt, String model, Map<String, Object> params);

    /** Streaming completion (SSE). */
    Flux<String> completionStream(String systemPrompt, String userPrompt, String model, Map<String, Object> params);

    /** Text embedding for RAG. */
    List<Float> embed(String text, String model);

    /** Provider identifier. */
    String getProviderName();
}
