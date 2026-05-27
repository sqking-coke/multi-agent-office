package com.agentoffice.config;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.DispatchHub;
import com.agentoffice.dispatch.strategy.*;
import com.agentoffice.entity.AgentInfo;
import com.agentoffice.mapper.AgentInfoMapper;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 调度初始化配置：启动时注册全部协作策略到 DispatchHub，并从数据库加载已启用的 Agent 配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DispatchConfig {

    private final DispatchHub dispatchHub;
    private final AgentRegistry agentRegistry;
    private final AgentInfoMapper agentInfoMapper;
    private final ParallelStrategy parallelStrategy;
    private final SerialStrategy serialStrategy;
    private final RelayStrategy relayStrategy;
    private final ConditionalStrategy conditionalStrategy;
    private final LoopStrategy loopStrategy;

    @jakarta.annotation.PostConstruct
    public void init() {
        // Register collaboration strategies
        dispatchHub.registerStrategy(parallelStrategy);
        dispatchHub.registerStrategy(serialStrategy);
        dispatchHub.registerStrategy(relayStrategy);
        dispatchHub.registerStrategy(conditionalStrategy);
        dispatchHub.registerStrategy(loopStrategy);
        log.info("Registered 5 collaboration strategies");

        // Load agents from database
        List<AgentInfo> dbAgents = agentInfoMapper.selectList(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getStatus, 1));

        for (AgentInfo info : dbAgents) {
            try {
                BizAgent.AgentCapability capability = JSON.parseObject(
                        info.getAgentCapability(), BizAgent.AgentCapability.class);

                AgentConfig.AgentConfigBuilder configBuilder = AgentConfig.builder()
                        .agentCode(info.getAgentCode())
                        .agentName(info.getAgentName())
                        .capability(capability)
                        .priority(info.getPriority())
                        .maxConcurrency(info.getMaxConcurrency());

                if (info.getConfigJson() != null) {
                    var cfg = JSON.parseObject(info.getConfigJson());
                    if (cfg.containsKey("model")) configBuilder.model(cfg.getString("model"));
                    if (cfg.containsKey("temperature")) configBuilder.temperature(cfg.getDoubleValue("temperature"));
                    if (cfg.containsKey("maxTokens")) configBuilder.maxTokens(cfg.getIntValue("maxTokens"));
                    if (cfg.containsKey("dailyTokenBudget")) configBuilder.dailyTokenBudget(cfg.getLongValue("dailyTokenBudget"));
                    if (cfg.containsKey("retryMax")) configBuilder.retryMax(cfg.getIntValue("retryMax"));
                    if (cfg.containsKey("timeoutSeconds")) configBuilder.timeoutSeconds(cfg.getIntValue("timeoutSeconds"));
                }

                // AgentConfig is just metadata storage; actual BizAgent beans register themselves
                // See individual agent @Component classes that call agentRegistry.register()
                log.info("Loaded agent config from DB: {} ({})", info.getAgentCode(), info.getAgentName());
            } catch (Exception e) {
                log.warn("Failed to parse agent config for {}: {}", info.getAgentCode(), e.getMessage());
            }
        }
        log.info("DispatchHub initialized with {} agents in registry", agentRegistry.getAgentCount());
    }
}
