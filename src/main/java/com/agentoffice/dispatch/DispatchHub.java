package com.agentoffice.dispatch;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.event.AgentEvent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.strategy.*;
import com.agentoffice.dispatch.TaskDecomposer.*;
import com.agentoffice.entity.AgentTask;
import com.agentoffice.entity.AgentTaskItem;
import com.agentoffice.mapper.AgentTaskItemMapper;
import com.agentoffice.mapper.AgentTaskMapper;
import com.alibaba.fastjson2.JSON;
import com.google.common.eventbus.EventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchHub {

    private final AgentRegistry registry;
    private final TaskDecomposer decomposer;
    private final GlobalContext globalContext;
    private final EventBus eventBus;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskItemMapper itemMapper;

    private final Map<String, CollaborationStrategy> strategies = new HashMap<>();

    private final ExecutorService agentExecutor = new ThreadPoolExecutor(
            10, 50, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            r -> new Thread(r, "agent-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public void registerStrategy(CollaborationStrategy strategy) {
        strategies.put(strategy.getMode(), strategy);
        log.info("Registered collaboration strategy: {}", strategy.getMode());
    }

    /**
     * Main entry point: submit a complex task and orchestrate multi-agent execution.
     */
    public AgentTask submitTask(String taskName, String taskContent, Long submitUserId, Long tenantId) {
        // Generate trace ID
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // Create master task record
        AgentTask task = new AgentTask();
        task.setTaskNo("AT" + System.currentTimeMillis());
        task.setTaskName(taskName);
        task.setTaskContent(taskContent);
        task.setTaskStatus(0);
        task.setSubmitUserId(submitUserId);
        task.setTenantId(tenantId);
        task.setTraceId(traceId);
        taskMapper.insert(task);

        // Init global context
        globalContext.initTask(traceId);
        globalContext.put(traceId, "taskName", taskName);
        globalContext.put(traceId, "taskContent", taskContent);

        try {
            // Step 1: Decompose
            DecomposeResult decomposeResult = decomposer.decompose(taskName, taskContent, tenantId);
            task.setCoopMode(decomposeResult.getCoopMode());
            taskMapper.updateById(task);

            eventBus.post(new AgentEvent.TaskDecomposed(traceId, task.getId(),
                    decomposeResult.getSubTasks().stream().map(DecomposedTask::getName).collect(Collectors.toList()),
                    decomposeResult.getCoopMode()));

            // Step 2: Create sub-task items
            List<AgentTaskItem> items = createSubTaskItems(task.getId(), decomposeResult, tenantId, traceId);

            // Step 3: Execute according to strategy
            CollaborationStrategy strategy = strategies.getOrDefault(
                    decomposeResult.getCoopMode(), strategies.get("SERIAL"));

            log.info("[{}] Executing task '{}' with strategy {} ({} sub-tasks)",
                    traceId, taskName, strategy.getMode(), decomposeResult.getSubTasks().size());

            Map<Integer, BizAgent.AgentResult> results = strategy.execute(
                    decomposeResult, traceId, task.getId(),
                    registry, globalContext, agentExecutor,
                    Map.of("taskName", taskName, "tenantId", tenantId));

            // Step 4: Update sub-task items with results
            updateSubTaskResults(items, results, decomposeResult, traceId);


            // Step 5: Aggregate
            String summary = aggregateResults(taskName, results, decomposeResult);
            task.setResultSummary(summary);
            task.setTaskStatus(determineFinalStatus(results));
            task.setFinishTime(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("[{}] Task '{}' completed with status {}", traceId, taskName, task.getTaskStatus());

        } catch (Exception e) {
            log.error("[{}] Task '{}' failed", traceId, taskName, e);
            task.setTaskStatus(2);
            task.setResultSummary("Execution error: " + e.getMessage());
            task.setFinishTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }

        return task;
    }

    private List<AgentTaskItem> createSubTaskItems(Long taskId, DecomposeResult result, Long tenantId, String traceId) {
        List<AgentTaskItem> items = new ArrayList<>();
        Set<String> dispatchedAgents = new HashSet<>();
        for (int i = 0; i < result.getSubTasks().size(); i++) {
            DecomposedTask dt = result.getSubTasks().get(i);
            AgentTaskItem item = new AgentTaskItem();
            item.setTaskId(taskId);
            item.setAgentCode(dt.getRequiredCapability());
            item.setItemContent(dt.getContent());
            item.setStatus(0); // PENDING
            item.setRetryCount(0);
            item.setMaxRetry(3);
            item.setTenantId(tenantId);
            itemMapper.insert(item);
            items.add(item);

            String agentCode = dt.getRequiredCapability() != null ? dt.getRequiredCapability() : "default";
            if (dispatchedAgents.add(agentCode)) {
                eventBus.post(new AgentEvent.TaskDispatched(traceId, taskId, agentCode));
            }
        }
        return items;
    }

    private void updateSubTaskResults(List<AgentTaskItem> items,
                                       Map<Integer, BizAgent.AgentResult> results,
                                       DecomposeResult decomposeResult, String traceId) {
        for (int i = 0; i < items.size(); i++) {
            AgentTaskItem item = items.get(i);
            BizAgent.AgentResult r = results.get(i);
            if (r != null) {
                item.setItemResult(JSON.toJSONString(r));
                item.setStatus(r.isSuccess() ? 2 : 3);
                item.setErrorMsg(r.getErrorMsg());
                item.setCostTime((int) r.getCostTimeMs());
                item.setFinishTime(LocalDateTime.now());
                itemMapper.updateById(item);

                String agentCode = item.getAgentCode() != null ? item.getAgentCode() : "unknown";
                if (r.isSuccess()) {
                    eventBus.post(new AgentEvent.TaskCompleted(traceId, item.getTaskId(),
                            item.getId(), agentCode, r.getSummary()));
                } else {
                    eventBus.post(new AgentEvent.TaskFailed(traceId, item.getTaskId(),
                            item.getId(), agentCode, r.getErrorMsg()));
                }
            } else {
                item.setStatus(4); // SKIPPED
                itemMapper.updateById(item);
            }
        }
    }

    private String aggregateResults(String taskName,
                                     Map<Integer, BizAgent.AgentResult> results,
                                     DecomposeResult decomposeResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(taskName).append(" - 协同执行报告\n\n");
        sb.append("**协同模式**: ").append(decomposeResult.getCoopMode()).append("\n");
        sb.append("**拆解来源**: ").append(decomposeResult.getSource()).append("\n\n");

        long totalSuccess = results.values().stream().filter(BizAgent.AgentResult::isSuccess).count();
        sb.append("**执行结果**: ").append(totalSuccess).append("/").append(results.size()).append(" 成功\n\n");

        for (int i = 0; i < decomposeResult.getSubTasks().size(); i++) {
            var dt = decomposeResult.getSubTasks().get(i);
            sb.append("### ").append(i + 1).append(". ").append(dt.getName()).append("\n");
            BizAgent.AgentResult r = results.get(i);
            if (r != null) {
                sb.append("- 状态: ").append(r.isSuccess() ? "成功" : "失败").append("\n");
                sb.append("- 摘要: ").append(r.getSummary()).append("\n");
                if (r.getErrorMsg() != null) {
                    sb.append("- 错误: ").append(r.getErrorMsg()).append("\n");
                }
            } else {
                sb.append("- 状态: 未执行\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private int determineFinalStatus(Map<Integer, BizAgent.AgentResult> results) {
        boolean allSuccess = results.values().stream().allMatch(BizAgent.AgentResult::isSuccess);
        boolean anyFailed = results.values().stream().anyMatch(r -> !r.isSuccess());
        return allSuccess ? 1 : (anyFailed && results.values().stream().noneMatch(BizAgent.AgentResult::isSuccess) ? 2 : 1);
    }

    public void cancelTask(Long taskId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task != null && task.getTaskStatus() == 0) {
            task.setTaskStatus(3);
            task.setFinishTime(LocalDateTime.now());
            taskMapper.updateById(task);
            log.info("Task {} cancelled", taskId);
        }
    }

    public ExecutorService getAgentExecutor() {
        return agentExecutor;
    }
}
