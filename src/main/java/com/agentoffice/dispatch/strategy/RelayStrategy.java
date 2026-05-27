package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Relay: Agent A output is fed as input to Agent B, chain-style.
 */
@Slf4j
@Component
public class RelayStrategy implements CollaborationStrategy {

    @Override
    public String getMode() { return "RELAY"; }

    @Override
    public Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult result, String traceId, Long masterTaskId,
            AgentRegistry registry, GlobalContext context,
            java.util.concurrent.ExecutorService executor, Map<String, Object> baseParams) {

        List<DecomposedTask> tasks = result.getSubTasks();
        Map<Integer, BizAgent.AgentResult> results = new LinkedHashMap<>();
        String input = tasks.get(0).getContent();

        for (int i = 0; i < tasks.size(); i++) {
            DecomposedTask task = tasks.get(i);
            Optional<BizAgent> agentOpt = resolveAgent(task, registry);
            if (agentOpt.isEmpty()) {
                results.put(i, BizAgent.AgentResult.fail("No matching agent for: " + task.getRequiredCapability()));
                break;
            }
            BizAgent agent = agentOpt.get();
            log.info("[{}] Relay step {}/{} executing on agent {}", traceId, i + 1, tasks.size(), agent.getAgentCode());

            try {
                // Feed previous output as next input
                BizAgent.AgentResult r = agent.execute(traceId + "-" + i, input,
                        context.getAll(traceId));
                results.put(i, r);

                if (!r.isSuccess()) break;
                // Convert result to string for next agent
                input = r.getSummary() != null ? r.getSummary() : Objects.toString(r.getData(), "");
            } catch (Exception e) {
                log.error("[{}] Relay step {} exception", traceId, i + 1, e);
                results.put(i, BizAgent.AgentResult.fail(e.getMessage()));
                break;
            }
        }
        return results;
    }

    private Optional<BizAgent> resolveAgent(DecomposedTask task, AgentRegistry registry) {
        if (task.getRequiredCapability() != null) {
            return registry.findBestForCapability(task.getRequiredCapability());
        }
        return registry.getAllAgents().stream().findFirst();
    }
}
