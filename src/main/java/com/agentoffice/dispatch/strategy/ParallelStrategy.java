package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 并行协作策略：所有子任务同时分发给各自 Agent 执行，通过 CountDownLatch 等待全部完成。
 */
@Slf4j
@Component
public class ParallelStrategy implements CollaborationStrategy {

    @Override
    public String getMode() { return "PARALLEL"; }

    @Override
    public Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult result, String traceId, Long masterTaskId,
            AgentRegistry registry, GlobalContext context,
            ExecutorService executor, Map<String, Object> baseParams) {

        List<DecomposedTask> tasks = result.getSubTasks();
        Map<Integer, BizAgent.AgentResult> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(tasks.size());

        for (int i = 0; i < tasks.size(); i++) {
            final int idx = i;
            DecomposedTask task = tasks.get(i);
            executor.submit(() -> {
                try {
                    Optional<BizAgent> agentOpt = resolveAgent(task, registry);
                    if (agentOpt.isEmpty()) {
                        results.put(idx, BizAgent.AgentResult.fail("No matching agent for: " + task.getRequiredCapability()));
                        return;
                    }
                    BizAgent agent = agentOpt.get();
                    log.info("[{}] Dispatching task {}/{} to agent {}", traceId, idx + 1, tasks.size(), agent.getAgentCode());
                    BizAgent.AgentResult r = agent.execute(
                            traceId + "-" + idx, task.getContent(),
                            context.getAll(traceId));
                    results.put(idx, r);
                } catch (Exception e) {
                    log.error("[{}] Task {}/{} failed", traceId, idx + 1, tasks.size(), e);
                    results.put(idx, BizAgent.AgentResult.fail(e.getMessage()));
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return results;
    }

    protected Optional<BizAgent> resolveAgent(DecomposedTask task, AgentRegistry registry) {
        if (task.getRequiredCapability() != null) {
            return registry.findBestForCapability(task.getRequiredCapability());
        }
        return registry.getAllAgents().stream().findFirst();
    }
}
