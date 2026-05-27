package com.agentoffice.llm;

import com.agentoffice.entity.LlmTokenDailyStat;
import com.agentoffice.mapper.LlmTokenDailyStatMapper;
import com.agentoffice.service.TokenBudgetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Facade for LLM interactions: caching, token tracking, provider routing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final LLMCache cache;
    private final LlmTokenDailyStatMapper statMapper;
    private final TokenBudgetService tokenBudgetService;

    @Value("${llm.default-provider:openai}")
    private String defaultProvider;

    @Value("${llm.cache.enabled:true}")
    private boolean cacheEnabled;

    private OpenAIProvider openAIProvider;
    private AnthropicProvider anthropicProvider;
    private OpenAIProvider deepseekProvider;

    @PostConstruct
    public void init() {
        String openaiKey = System.getenv().getOrDefault("OPENAI_API_KEY", "");
        String anthropicKey = System.getenv().getOrDefault("ANTHROPIC_API_KEY", "");
        String deepseekKey = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "");
        String openaiUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
        String anthropicUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1");
        String deepseekUrl = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1");

        this.openAIProvider = new OpenAIProvider(openaiKey, openaiUrl);
        this.anthropicProvider = new AnthropicProvider(anthropicKey, anthropicUrl);
        this.deepseekProvider = new OpenAIProvider(deepseekKey, deepseekUrl);
        log.info("LLMService initialized — default provider: {}", defaultProvider);
    }

    /** LLM 补全：Token 预算检查 → 缓存查询 → 提供商路由 → 结果缓存 → Token 统计。 */
    public String completion(String systemPrompt, String userPrompt, String model, String agentCode) {
        TokenBudgetService.BudgetStatus budgetStatus = tokenBudgetService.checkBudget(agentCode);
        if (budgetStatus == TokenBudgetService.BudgetStatus.BREAK_100) {
            log.warn("Token budget exceeded for agent {}, returning fallback response", agentCode);
            return "{\"error\": \"Token budget exceeded for today. Please try again tomorrow.\"}";
        }

        LLMProvider provider = resolveProvider(model);

        // Check cache
        if (cacheEnabled) {
            String key = cache.buildKey(systemPrompt, userPrompt, model != null ? model : "default");
            String cached = cache.get(key);
            if (cached != null) {
                recordTokenStat(agentCode, model, 0, 0, 0, true);
                return cached;
            }
        }

        String result = provider.completion(systemPrompt, userPrompt, model, Map.of());
        if (result != null && cacheEnabled) {
            String key = cache.buildKey(systemPrompt, userPrompt, model != null ? model : "default");
            cache.put(key, result);
        }

        // Estimate tokens (rough: ~4 chars per token)
        int promptTokens = (systemPrompt.length() + userPrompt.length()) / 4;
        int completionTokens = result != null ? result.length() / 4 : 0;
        recordTokenStat(agentCode, model, promptTokens, completionTokens, 1, false);

        return result;
    }

    public Flux<String> completionStream(String systemPrompt, String userPrompt, String model, String agentCode) {
        LLMProvider provider = resolveProvider(model);
        return provider.completionStream(systemPrompt, userPrompt, model, Map.of());
    }

    public List<Float> embed(String text, String model) {
        return resolveProvider(model).embed(text, model);
    }

    /** 根据模型名关键词路由到对应提供商，默认使用 openAIProvider。 */
    private LLMProvider resolveProvider(String model) {
        if (model != null && model.toLowerCase().contains("claude")) {
            return anthropicProvider;
        }
        if (model != null && model.toLowerCase().contains("deepseek")) {
            return deepseekProvider;
        }
        if ("anthropic".equalsIgnoreCase(defaultProvider)) {
            return anthropicProvider;
        }
        if ("deepseek".equalsIgnoreCase(defaultProvider)) {
            return deepseekProvider;
        }
        return openAIProvider;
    }

    /** 记录每日 LLM Token 消耗统计，新建或累加当日记录（按 agent + model + 日期唯一）。 */
    private void recordTokenStat(String agentCode, String model, int promptTokens,
                                  int completionTokens, int callCount, boolean cacheHit) {
        try {
            LocalDate today = LocalDate.now();
            LlmTokenDailyStat stat = statMapper.selectOne(new LambdaQueryWrapper<LlmTokenDailyStat>()
                    .eq(LlmTokenDailyStat::getStatDate, today)
                    .eq(LlmTokenDailyStat::getAgentCode, agentCode)
                    .eq(LlmTokenDailyStat::getModel, model));

            if (stat == null) {
                stat = new LlmTokenDailyStat();
                stat.setStatDate(today);
                stat.setAgentCode(agentCode);
                stat.setModel(model);
                stat.setPromptTokens((long) promptTokens);
                stat.setCompletionTokens((long) completionTokens);
                stat.setTotalTokens((long) (promptTokens + completionTokens));
                stat.setCallCount(callCount);
                stat.setCacheHitCount(cacheHit ? 1 : 0);
                stat.setEstimatedCost(estimateCost(model, promptTokens, completionTokens));
                statMapper.insert(stat);
            } else {
                stat.setPromptTokens(stat.getPromptTokens() + promptTokens);
                stat.setCompletionTokens(stat.getCompletionTokens() + completionTokens);
                stat.setTotalTokens(stat.getTotalTokens() + promptTokens + completionTokens);
                stat.setCallCount(stat.getCallCount() + callCount);
                if (cacheHit) stat.setCacheHitCount(stat.getCacheHitCount() + 1);
                stat.setEstimatedCost(estimateCost(model,
                        stat.getPromptTokens().intValue(),
                        stat.getCompletionTokens().intValue()));
                statMapper.updateById(stat);
            }
        } catch (Exception e) {
            log.debug("Failed to record token stat: {}", e.getMessage());
        }
    }

    /** 粗略估算 LLM 调用成本（USD），按各模型每千 Token 定价计算。 */
    private BigDecimal estimateCost(String model, int promptTokens, int completionTokens) {
        // Rough pricing estimates (per 1K tokens, USD)
        double promptPrice = 0.03;
        double completionPrice = 0.06;
        if (model != null && model.contains("claude")) {
            promptPrice = 0.015;
            completionPrice = 0.075;
        }
        if (model != null && model.contains("deepseek")) {
            promptPrice = 0.00027;
            completionPrice = 0.0011;
        }
        double cost = (promptTokens / 1000.0) * promptPrice + (completionTokens / 1000.0) * completionPrice;
        return BigDecimal.valueOf(Math.round(cost * 10000.0) / 10000.0);
    }
}
