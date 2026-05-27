package com.agentoffice.dispatch.event;

import com.agentoffice.agent.event.AgentEvent;
import com.agentoffice.dispatch.GlobalContext;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles task lifecycle events: logging, metrics, context updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private final EventBus eventBus;
    private final GlobalContext globalContext;

    @PostConstruct
    public void init() {
        eventBus.register(this);
        log.info("TaskEventListener registered on EventBus");
    }

    @Subscribe
    public void onTaskDecomposed(AgentEvent.TaskDecomposed event) {
        log.info("[{}] Task decomposed into {} sub-tasks, mode={}",
                event.getTraceId(), event.getSubTaskContents().size(), event.getCoopMode());
    }

    @Subscribe
    public void onTaskDispatched(AgentEvent.TaskDispatched event) {
        log.info("[{}] Sub-task dispatched to agent {}", event.getTraceId(), event.getAgentCode());
    }

    @Subscribe
    public void onTaskCompleted(AgentEvent.TaskCompleted event) {
        log.info("[{}] Sub-task {} completed by {}: {}",
                event.getTraceId(), event.getItemId(), event.getAgentCode(), event.getResultSummary());
        // Store result in global context for downstream agents
        globalContext.put(event.getTraceId(), "result:" + event.getAgentCode(), event.getResultSummary());
    }

    @Subscribe
    public void onTaskFailed(AgentEvent.TaskFailed event) {
        log.error("[{}] Sub-task {} failed on {}: {}",
                event.getTraceId(), event.getItemId(), event.getAgentCode(), event.getErrorMsg());
    }
}
