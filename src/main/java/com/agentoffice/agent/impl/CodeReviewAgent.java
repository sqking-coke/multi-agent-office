package com.agentoffice.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.llm.LLMService;
import com.agentoffice.prompt.PromptResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewAgent implements BizAgent {

    private final AgentRegistry registry;
    private final LLMService llmService;
    private final PromptResolver promptResolver;

    private static final String SYSTEM_PROMPT = """
            你是一个资深代码审查专家。请审查以下代码，关注：
            - 代码规范（命名、格式、注释）
            - 潜在语义BUG（空指针、边界条件、并发问题）
            - 性能问题（不必要的循环、内存泄漏风险）
            - 安全漏洞（注入风险、敏感信息泄露）
            输出JSON格式：
            {
              "score": <0-100>,
              "language": "<编程语言>",
              "issues": [
                {"severity": "error|warning|info", "line": <行号>, "category": "规范|BUG|性能|安全", "description": "<问题描述>", "suggestion": "<修复建议>"}
              ],
              "summary": "<总体评价>"
            }
            """;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("code_review_agent")
                .agentName("代码审查Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("code_review", "bug_detection", "security_scan"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                        .preconditions(List.of())
                        .build())
                .model("deepseek-v4-flash")
                .temperature(0.2)
                .maxTokens(4096)
                .dailyTokenBudget(200000)
                .retryMax(3)
                .timeoutSeconds(300)
                .priority(10)
                .maxConcurrency(3)
                .build();
        registry.register(this, config);
        log.info("CodeReviewAgent registered");
    }

    @Override
    public String getAgentCode() {
        return "code_review_agent";
    }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("code_review", "bug_detection", "security_scan"))
                .inputFormats(List.of("text/plain", "application/json"))
                .outputFormats(List.of("application/json"))
                .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                .preconditions(List.of())
                .build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            // Detect language from context or content
            String language = detectLanguage(content);
            Map<String, Object> taskParams = Map.of("content", content, "language", language);
            String resolvedSystemPrompt = promptResolver.resolve(
                    getAgentCode(), SYSTEM_PROMPT, taskParams, globalContext);
            String prompt = buildPrompt(content, language);
            String llmResult = llmService.completion(resolvedSystemPrompt, prompt, "deepseek-v4-flash", getAgentCode());

            if (llmResult == null) {
                return AgentResult.fail("LLM call returned null");
            }

            JSONObject result = parseResult(llmResult);
            result.put("language", language);
            result.put("reviewedAt", java.time.LocalDateTime.now().toString());

            int score = result.getIntValue("score");
            JSONArray issues = result.getJSONArray("issues");
            int errorCount = 0, warnCount = 0;
            if (issues != null) {
                for (int i = 0; i < issues.size(); i++) {
                    String sev = issues.getJSONObject(i).getString("severity");
                    if ("error".equals(sev)) errorCount++;
                    else if ("warning".equals(sev)) warnCount++;
                }
            }

            String summary = String.format("评分: %d/100, 错误: %d, 警告: %d", score, errorCount, warnCount);
            long cost = System.currentTimeMillis() - start;

            return AgentResult.builder()
                    .success(true)
                    .summary(summary)
                    .data(result)
                    .costTimeMs(cost)
                    .build();
        } catch (Exception e) {
            log.error("CodeReviewAgent execution failed", e);
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

    private String detectLanguage(String content) {
        if (content == null) return "unknown";
        if (content.contains("public class") || content.contains("import java")) return "java";
        if (content.contains("def ") || content.contains("import ")) return "python";
        if (content.contains("function ") || content.contains("const ")) return "javascript";
        if (content.contains("package ") && content.contains("func ")) return "go";
        return "unknown";
    }

    private String buildPrompt(String content, String language) {
        return "语言：" + language + "\n\n代码：\n" + content;
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
            fallback.put("score", 0);
            fallback.put("issues", new JSONArray());
            fallback.put("summary", "解析失败: " + llmResult);
            return fallback;
        }
    }
}
