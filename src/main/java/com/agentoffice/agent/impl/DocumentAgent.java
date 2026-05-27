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
 * 文档处理 Agent：根据用户指令对文档内容进行总结、要点提取、改写或格式规整。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent implements BizAgent {

    private final AgentRegistry registry;
    private final LLMService llmService;
    private final PromptResolver promptResolver;

    private static final String SYSTEM_PROMPT = """
            你是一个专业文档处理助手。根据用户指令处理文档内容：
            - 如果要求"总结"，请提炼核心要点（3-7条）
            - 如果要求"提取要点"，列出关键信息
            - 如果要求"改写"，请润色优化语言
            - 如果要求"规整格式"，请规范化排版
            输出JSON格式：{"operation": "操作类型", "result": "处理结果", "keyPoints": ["要点1", "要点2"]}
            """;

    @PostConstruct
    public void init() {
        AgentConfig config = AgentConfig.builder()
                .agentCode("document_agent")
                .agentName("文档处理Agent")
                .capability(AgentCapability.builder()
                        .capabilities(List.of("doc_analysis", "doc_summary", "doc_rewrite"))
                        .inputFormats(List.of("text/plain", "text/markdown", "application/json"))
                        .outputFormats(List.of("application/json", "text/markdown"))
                        .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                        .preconditions(List.of())
                        .build())
                .model("deepseek-v4-flash")
                .temperature(0.4)
                .maxTokens(4096)
                .dailyTokenBudget(150000)
                .retryMax(3)
                .timeoutSeconds(300)
                .priority(15)
                .maxConcurrency(5)
                .build();
        registry.register(this, config);
        log.info("DocumentAgent registered");
    }

    @Override
    public String getAgentCode() {
        return "document_agent";
    }

    @Override
    public AgentCapability getCapability() {
        return AgentCapability.builder()
                .capabilities(List.of("doc_analysis", "doc_summary", "doc_rewrite"))
                .inputFormats(List.of("text/plain", "text/markdown", "application/json"))
                .outputFormats(List.of("application/json", "text/markdown"))
                .supportedModels(List.of("deepseek-v4-flash", "gpt-4", "claude-4"))
                .preconditions(List.of())
                .build();
    }

    @Override
    public AgentResult execute(String itemId, String content, Map<String, Object> globalContext) {
        long start = System.currentTimeMillis();
        try {
            // Infer operation from context
            String operation = inferOperation(globalContext);
            String resolvedSystemPrompt = promptResolver.resolve(
                    getAgentCode(), SYSTEM_PROMPT, Map.of("content", content), globalContext);
            String prompt = "操作：" + operation + "\n\n文档内容：\n" + content;
            String llmResult = llmService.completion(resolvedSystemPrompt, prompt, null, getAgentCode());

            if (llmResult == null) {
                return AgentResult.fail("LLM call returned null");
            }

            JSONObject result = parseResult(llmResult);
            result.put("operation", operation);

            long cost = System.currentTimeMillis() - start;
            return AgentResult.builder()
                    .success(true)
                    .summary("完成文档" + operation + "处理")
                    .data(result)
                    .costTimeMs(cost)
                    .build();
        } catch (Exception e) {
            log.error("DocumentAgent execution failed", e);
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

    /** 从全局上下文中推断文档处理操作类型，默认"总结"。 */
    private String inferOperation(Map<String, Object> globalContext) {
        String op = (String) globalContext.get("docOperation");
        if (op != null) return op;
        return "总结";
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
            fallback.put("operation", "总结");
            fallback.put("result", llmResult);
            return fallback;
        }
    }
}
