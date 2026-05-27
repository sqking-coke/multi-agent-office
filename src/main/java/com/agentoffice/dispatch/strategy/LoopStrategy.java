package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Loop iteration: execute agents in a loop until a quality threshold is met or max rounds reached.
 */
@Slf4j
@Component
public class LoopStrategy implements CollaborationStrategy {

    private static final int MAX_ROUNDS = 5;
    private static final double QUALITY_THRESHOLD = 80.0;

    @Override
    public String getMode() { return "LOOP"; }

    @Override
    public Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult result, String traceId, Long masterTaskId,
            AgentRegistry registry, GlobalContext context,
            java.util.concurrent.ExecutorService executor, Map<String, Object> baseParams) {

        List<DecomposedTask> tasks = result.getSubTasks();
        Map<Integer, BizAgent.AgentResult> allResults = new LinkedHashMap<>();
        if (tasks.size() < 2) return allResults;

        // First agent: reviewer, Second agent: fixer
        DecomposedTask reviewTask = tasks.get(0);
        DecomposedTask fixTask = tasks.get(1);

        String content = reviewTask.getContent();
        int resultIdx = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            log.info("[{}] Loop round {}/{}", traceId, round + 1, MAX_ROUNDS);

            // Review
            Optional<BizAgent> reviewer = resolveAgent(reviewTask, registry);
            if (reviewer.isEmpty()) break;
            BizAgent.AgentResult reviewResult = reviewer.get().execute(
                    traceId + "-review-" + round, content, context.getAll(traceId));
            allResults.put(resultIdx++, reviewResult);

            if (!reviewResult.isSuccess()) break;

            // Check quality
            double score = extractScore(reviewResult);
            if (score >= QUALITY_THRESHOLD) {
                log.info("[{}] Loop converged at round {} with score {}", traceId, round + 1, score);
                break;
            }

            // Fix
            Optional<BizAgent> fixer = resolveAgent(fixTask, registry);
            if (fixer.isEmpty()) break;

            Map<String, Object> fixCtx = context.getAll(traceId);
            fixCtx.put("reviewResult", reviewResult.getData());
            BizAgent.AgentResult fixResult = fixer.get().execute(
                    traceId + "-fix-" + round,
                    content + "\n\nReview feedback:\n" + reviewResult.getSummary(),
                    fixCtx);
            allResults.put(resultIdx++, fixResult);

            if (!fixResult.isSuccess()) break;
            content = fixResult.getSummary() != null ? fixResult.getSummary() : content;

            // Check diminishing returns
            if (round > 0 && score > 0) {
                double prevScore = extractScore(allResults.get(resultIdx - 3));
                if (Math.abs(score - prevScore) < 1.0) {
                    log.info("[{}] Loop stopped: diminishing returns", traceId);
                    break;
                }
            }
        }
        return allResults;
    }

    private double extractScore(BizAgent.AgentResult result) {
        if (result.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            Object score = data.get("score");
            if (score instanceof Number) return ((Number) score).doubleValue();
        }
        return 0;
    }

    private Optional<BizAgent> resolveAgent(DecomposedTask task, AgentRegistry registry) {
        if (task.getRequiredCapability() != null) {
            return registry.findBestForCapability(task.getRequiredCapability());
        }
        return registry.getAllAgents().stream().findFirst();
    }
}
