package com.agentoffice.dispatch.event;

import com.agentoffice.agent.event.AgentEvent;
import com.agentoffice.monitor.AlertService;
import com.agentoffice.service.ApprovalService;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles approval workflow events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEventListener {

    private final EventBus eventBus;
    private final ApprovalService approvalService;
    private final AlertService alertService;

    @PostConstruct
    public void init() {
        eventBus.register(this);
        log.info("ApprovalEventListener registered on EventBus");
    }

    @Subscribe
    public void onApprovalRequired(AgentEvent.ApprovalRequired event) {
        log.info("[{}] Approval required for task item {} (agent={})",
                event.getTraceId(), event.getItemId(), event.getAgentCode());

        // Create approval record — approverId is 0 as placeholder (real impl would resolve from task context)
        approvalService.createRecord(event.getItemId(), 0L,
                "Task item requires manual approval");

        alertService.sendAlert("审批待处理",
                "子任务 " + event.getItemId() + " 需要人工审批\nAgent: " + event.getAgentCode(),
                "P2");
    }

    @Subscribe
    public void onApprovalResolved(AgentEvent.ApprovalResolved event) {
        log.info("[{}] Approval resolved for task item {}: {}",
                event.getTraceId(), event.getItemId(), event.isApproved() ? "approved" : "rejected");

        if (!event.isApproved()) {
            alertService.sendAlert("审批已驳回",
                    "子任务 " + event.getItemId() + " 已被驳回",
                    "P2");
        }
    }
}
