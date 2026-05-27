package com.agentoffice.agent;

import lombok.Builder;
import lombok.Data;

/** Agent 运行时配置参数对象：模型、温度、Token 预算、重试策略、并发限制等。 */
@Data
@Builder
public class AgentConfig {
    private String agentCode;
    private String agentName;
    private BizAgent.AgentCapability capability;
    private String model;
    private double temperature;
    private int maxTokens;
    private long dailyTokenBudget;
    private int retryMax;
    private long retryBackoffMs;
    private int timeoutSeconds;
    private int priority;
    private int maxConcurrency;
}
