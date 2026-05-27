package com.agentoffice.agent.impl;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.llm.LLMService;
import com.agentoffice.prompt.PromptResolver;
import com.alibaba.fastjson2.JSON;
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
public class TicketAgent implements BizAgent {

    private final AgentRegistry registry;
    private final LLMService llmService;
    private final PromptResolver promptResolver;

    private static final String SYSTEM_PROMPT = """
            你是一个企业工单处理专家。请处理以下工单内容：
            - 对工单进行分类（技术类/业务类/行政类/其他）
            - 判断紧急程度（1-5，5最紧急）
            - 如果工单内容足够明确，给出处理建议
            - 如果工单包含具体问题，尝试直接解答
            请以JSON格式输出结果。
            """;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("ticket_agent")
                .agentName("工单处理Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("ticket_process"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                        .preconditions(List.of())
                        .build())
                .model("deepseek-v4-flash")
                .temperature(0.3)
                .maxTokens(2048)
                .dailyTokenBudget(100000)
                .retryMax(3)
                .timeoutSeconds(300)
                .priority(10)
                .maxConcurrency(5)
                .build();
        registry.register(this, config);
        log.info("TicketAgent registered");
    }

    @Override
    public String getAgentCode() {
        return "ticket_agent";
    }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("ticket_process"))
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
            String resolvedSystemPrompt = promptResolver.resolve(
                    getAgentCode(), SYSTEM_PROMPT, Map.of("content", content), globalContext);
            String prompt = buildPrompt(content, globalContext);
            String llmResult = llmService.completion(resolvedSystemPrompt, prompt, null, getAgentCode());

            if (llmResult == null) {
                return AgentResult.fail("LLM call returned null");
            }

            JSONObject result = parseResult(llmResult);
            result.put("originalContent", content);
            result.put("processedBy", getAgentCode());

            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder()
                    .success(true)
                    .summary(result.getString("category") + " - 紧急度: " + result.getString("urgency"))
                    .data(result)
                    .costTimeMs(cost)
                    .build();
        } catch (Exception e) {
            log.error("TicketAgent execution failed", e);
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

    private String buildPrompt(String content, Map<String, Object> globalContext) {
        return """
                工单内容：
                %s

                请分析以上工单，输出JSON格式：
                {"category": "分类", "urgency": 1-5, "suggestion": "处理建议", "autoReply": "自动回复内容"}
                """.formatted(content);
    }

    private JSONObject parseResult(String llmResult) {
        try {
            String json = llmResult.trim();
            // Handle possible markdown code blocks
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf("{"));
                json = json.substring(0, json.lastIndexOf("}") + 1);
            }
            return JSON.parseObject(json);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            fallback.put("category", "未能分类");
            fallback.put("urgency", 3);
            fallback.put("suggestion", llmResult);
            return fallback;
        }
    }
}
