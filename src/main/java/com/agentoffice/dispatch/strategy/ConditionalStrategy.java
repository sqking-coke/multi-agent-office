package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Conditional branching: result of Agent A determines whether to execute Agent B or Agent C.
 */
@Slf4j
@Component
public class ConditionalStrategy implements CollaborationStrategy {

    @Override
    public String getMode() { return "CONDITION"; }

    @Override
    public Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult result, String traceId, Long masterTaskId,
            AgentRegistry registry, GlobalContext context,
            java.util.concurrent.ExecutorService executor, Map<String, Object> baseParams) {

        List<DecomposedTask> tasks = result.getSubTasks();
        Map<Integer, BizAgent.AgentResult> results = new LinkedHashMap<>();

        // Execute first agent (the condition evaluator)
        if (tasks.isEmpty()) return results;

        DecomposedTask firstTask = tasks.get(0);
        Optional<BizAgent> firstAgent = registry.getAllAgents().stream().findFirst();
        if (firstAgent.isEmpty()) {
            results.put(0, BizAgent.AgentResult.fail("No agent available"));
            return results;
        }

        BizAgent.AgentResult conditionResult = firstAgent.get().execute(
                traceId + "-0", firstTask.getContent(), context.getAll(traceId));
        results.put(0, conditionResult);

        if (!conditionResult.isSuccess()) return results;

        // Evaluate condition
        boolean conditionMet = evaluateCondition(conditionResult, baseParams);
        int targetIndex = conditionMet ? 1 : 2;

        if (targetIndex < tasks.size()) {
            DecomposedTask targetTask = tasks.get(targetIndex);
            Optional<BizAgent> targetAgent = resolveAgent(targetTask, registry);
            if (targetAgent.isPresent()) {
                BizAgent.AgentResult r = targetAgent.get().execute(
                        traceId + "-" + targetIndex,
                        targetTask.getContent(),
                        context.getAll(traceId));
                results.put(targetIndex, r);
            }
        }
        return results;
    }

    private boolean evaluateCondition(BizAgent.AgentResult result, Map<String, Object> params) {
        if (result.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            if (data.containsKey("score")) {
                double score = Double.parseDouble(data.get("score").toString());
                return score >= 70;
            }
        }
        return result.isSuccess();
    }

    private Optional<BizAgent> resolveAgent(DecomposedTask task, AgentRegistry registry) {
        if (task.getRequiredCapability() != null) {
            return registry.findBestForCapability(task.getRequiredCapability());
        }
        return registry.getAllAgents().stream().findFirst();
    }
}
