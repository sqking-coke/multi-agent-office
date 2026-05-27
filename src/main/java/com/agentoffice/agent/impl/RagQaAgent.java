package com.agentoffice.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.entity.KbDocument;
import com.agentoffice.llm.LLMService;
import com.agentoffice.mapper.KbDocumentMapper;
import com.agentoffice.prompt.PromptResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagQaAgent implements BizAgent {

    private final AgentRegistry registry;
    private final LLMService llmService;
    private final KbDocumentMapper kbDocumentMapper;
    private final PromptResolver promptResolver;

    private static final String SYSTEM_PROMPT = """
            你是一个企业智能答疑助手。基于提供的知识库内容回答用户问题。
            - 如果知识库中有相关信息，请依据知识库内容回答，并在回答中引用来源
            - 如果知识库中信息不足，请诚实地说明，不要编造
            - 回答应简洁、准确、有结构
            输出JSON格式：
            {
              "answer": "<回答内容>",
              "confidence": <0-1>,
              "sources": ["<引用的知识来源>"],
              "relatedQuestions": ["<相关问题1>", "<相关问题2>"]
            }
            """;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("rag_qa_agent")
                .agentName("智能答疑RAG Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("rag_qa", "knowledge_search"))
                        .inputFormats(List.of("text/plain"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                        .preconditions(List.of("kb_loaded"))
                        .build())
                .model("deepseek-v4-flash")
                .temperature(0.3)
                .maxTokens(2048)
                .dailyTokenBudget(150000)
                .retryMax(3)
                .timeoutSeconds(300)
                .priority(15)
                .maxConcurrency(10)
                .build();
        registry.register(this, config);
        log.info("RagQaAgent registered");
    }

    @Override
    public String getAgentCode() {
        return "rag_qa_agent";
    }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("rag_qa", "knowledge_search"))
                .inputFormats(List.of("text/plain"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                .preconditions(List.of("kb_loaded"))
                .build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            // Retrieve relevant knowledge base documents
            String kbContext = retrieveKnowledge(content);

            String resolvedSystemPrompt = promptResolver.resolve(
                    getAgentCode(), SYSTEM_PROMPT, Map.of("content", content), globalContext);
            String prompt = "用户问题：" + content + "\n\n知识库相关内容：\n" + kbContext;
            String llmResult = llmService.completion(resolvedSystemPrompt, prompt, null, getAgentCode());

            if (llmResult == null) {
                return AgentResult.fail("LLM call returned null");
            }

            JSONObject result = parseResult(llmResult);
            long cost = System.currentTimeMillis() - start;

            return AgentResult.builder()
                    .success(true)
                    .summary(result.getString("answer"))
                    .data(result)
                    .costTimeMs(cost)
                    .build();
        } catch (Exception e) {
            log.error("RagQaAgent execution failed", e);
            return AgentResult.builder()
                    .success(false)
                    .errorMsg(e.getMessage())
                    .costTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    /**
     * Simple keyword-based knowledge retrieval.
     * In production, this would use vector similarity search (Milvus/Chroma).
     */
    private String retrieveKnowledge(String question) {
        List<KbDocument> docs = kbDocumentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getStatus, 1)
                        .last("LIMIT 5"));

        if (docs.isEmpty()) {
            return "（知识库为空，请基于通用知识回答）";
        }

        // Simple keyword filter
        List<KbDocument> relevant = docs.stream()
                .filter(d -> d.getDocContent() != null &&
                        containsAnyKeyword(d.getDocContent(), question))
                .limit(3)
                .collect(Collectors.toList());

        if (relevant.isEmpty()) {
            relevant = docs.subList(0, Math.min(3, docs.size()));
        }

        return relevant.stream()
                .map(d -> "【" + d.getDocName() + "】\n" + d.getDocContent())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private boolean containsAnyKeyword(String doc, String question) {
        // Simple keyword overlap
        String[] words = question.split("[\\s，,。.!！?？]+");
        for (String word : words) {
            if (word.length() >= 2 && doc.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject parseResult(String llmResult) {
        try {
            String json = llmResult.trim();
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf("{"));
                json = json.substring(0, json.lastIndexOf("}") + 1);
            }
            return JSON.parseObject(json);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            fallback.put("answer", llmResult);
            fallback.put("confidence", 0.5);
            fallback.put("sources", List.of());
            return fallback;
        }
    }
}
