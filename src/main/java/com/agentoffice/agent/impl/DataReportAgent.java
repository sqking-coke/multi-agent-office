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

/**
 * 数据报告 Agent：对原始数据进行分析，汇总关键指标、识别趋势与异常、生成优化建议。
 * 输出结构化的 JSON 分析报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataReportAgent implements BizAgent {

    private final AgentRegistry registry;
    private final LLMService llmService;
    private final PromptResolver promptResolver;

    private static final String SYSTEM_PROMPT = """
            你是一个数据分析与报告生成专家。根据提供的数据生成分析报告：
            - 汇总关键指标和趋势
            - 识别异常和波动
            - 分析问题分布和根因
            - 给出优化建议
            输出JSON格式：
            {
              "summary": "<一句话总结>",
              "metrics": {"key1": value1, "key2": value2},
              "trends": [{"metric": "...", "direction": "up|down|stable", "change": "<变化幅度>"}],
              "anomalies": [{"metric": "...", "value": ..., "expected": ...}],
              "recommendations": ["建议1", "建议2"]
            }
            """;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("data_report_agent")
                .agentName("数据统计复盘Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("data_report", "data_summary", "trend_analysis"))
                        .inputFormats(List.of("text/plain", "application/json"))
                        .outputFormats(List.of("application/json"))
                        .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                        .preconditions(List.of())
                        .build())
                .model("deepseek-v4-flash")
                .temperature(0.2)
                .maxTokens(4096)
                .dailyTokenBudget(150000)
                .retryMax(3)
                .timeoutSeconds(300)
                .priority(20)
                .maxConcurrency(5)
                .build();
        registry.register(this, config);
        log.info("DataReportAgent registered");
    }

    @Override
    public String getAgentCode() {
        return "data_report_agent";
    }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("data_report", "data_summary", "trend_analysis"))
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
            String prompt = "原始数据：\n" + content;
            String llmResult = llmService.completion(resolvedSystemPrompt, prompt, null, getAgentCode());

            if (llmResult == null) {
                return AgentResult.fail("LLM call returned null");
            }

            JSONObject result = parseResult(llmResult);
            long cost = System.currentTimeMillis() - start;

            return AgentResult.builder()
                    .success(true)
                    .summary(result.getString("summary"))
                    .data(result)
                    .costTimeMs(cost)
                    .build();
        } catch (Exception e) {
            log.error("DataReportAgent execution failed", e);
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

    /** 从 LLM 原始输出中提取 JSON，兼容 Markdown 代码块包裹。 */
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
            fallback.put("summary", "数据解析完成");
            fallback.put("metrics", new JSONObject());
            fallback.put("recommendations", List.of());
            return fallback;
        }
    }
}
