package com.agentoffice.dispatch.strategy;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.dispatch.GlobalContext;
import com.agentoffice.dispatch.TaskDecomposer.DecomposeResult;
import com.agentoffice.dispatch.TaskDecomposer.DecomposedTask;
import com.agentoffice.entity.AgentTaskItem;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * Defines how sub-tasks are executed — the execution plan.
 */
public interface CollaborationStrategy {

    String getMode();

    /**
     * Execute the decomposed tasks according to the strategy.
     * Returns results keyed by sub-task index.
     */
    Map<Integer, BizAgent.AgentResult> execute(
            DecomposeResult decomposeResult,
            String traceId,
            Long masterTaskId,
            AgentRegistry registry,
            GlobalContext context,
            ExecutorService executor,
            Map<String, Object> baseParams);
}
