package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 串行协作策略：子任务按顺序依次执行，上一步结果通过 GlobalContext 传递给下一步，任一步失败即中止后续。
 */
@Slf4j
@Component
public class SerialStrategy implements CollaborationStrategy {

    @Override
    public String getMode() { return "SERIAL"; }

    @Override
    public Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult result, String traceId, Long masterTaskId,
            AgentRegistry registry, GlobalContext context,
            java.util.concurrent.ExecutorService executor, Map<String, Object> baseParams) {

        List<DecomposedTask> tasks = result.getSubTasks();
        Map<Integer, BizAgent.AgentResult> results = new LinkedHashMap<>();

        for (int i = 0; i < tasks.size(); i++) {
            DecomposedTask task = tasks.get(i);
            Optional<BizAgent> agentOpt = resolveAgent(task, registry);
            if (agentOpt.isEmpty()) {
                results.put(i, BizAgent.AgentResult.fail("No matching agent for: " + task.getRequiredCapability()));
                break;
            }
            BizAgent agent = agentOpt.get();
            log.info("[{}] Serial step {}/{} executing on agent {}", traceId, i + 1, tasks.size(), agent.getAgentCode());

            // Feed previous results into context
            Map<String, Object> ctx = context.getAll(traceId);
            if (i > 0 && results.containsKey(i - 1)) {
                ctx.put("previousResult", results.get(i - 1).getData());
            }

            try {
                BizAgent.AgentResult r = agent.execute(traceId + "-" + i, task.getContent(), ctx);
                results.put(i, r);
                if (!r.isSuccess()) {
                    log.warn("[{}] Serial step {} failed, aborting chain", traceId, i + 1);
                    break;
                }
            } catch (Exception e) {
                log.error("[{}] Serial step {} exception", traceId, i + 1, e);
                results.put(i, BizAgent.AgentResult.fail(e.getMessage()));
                break;
            }
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
