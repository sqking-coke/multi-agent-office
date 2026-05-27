package com.agentoffice.agent.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@ToString
@AllArgsConstructor
public class AgentEvent {
    private final String eventType;
    private final String traceId;
    private final Long taskId;
    private final LocalDateTime timestamp;

    public static AgentEvent of(String type, String traceId, Long taskId) {
        return new AgentEvent(type, traceId, taskId, LocalDateTime.now());
    }

    @Getter
    @ToString(callSuper = true)
    public static class TaskDecomposed extends AgentEvent {
        private final List<String> subTaskContents;
        private final String coopMode;

        public TaskDecomposed(String traceId, Long taskId, List<String> subTaskContents, String coopMode) {
            super("TASK_DECOMPOSED", traceId, taskId, LocalDateTime.now());
            this.subTaskContents = subTaskContents;
            this.coopMode = coopMode;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class TaskDispatched extends AgentEvent {
        private final String agentCode;

        public TaskDispatched(String traceId, Long taskId, String agentCode) {
            super("TASK_DISPATCHED", traceId, taskId, LocalDateTime.now());
            this.agentCode = agentCode;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class TaskCompleted extends AgentEvent {
        private final Long itemId;
        private final String agentCode;
        private final String resultSummary;

        public TaskCompleted(String traceId, Long taskId, Long itemId, String agentCode, String resultSummary) {
            super("TASK_COMPLETED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
            this.resultSummary = resultSummary;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class TaskFailed extends AgentEvent {
        private final Long itemId;
        private final String agentCode;
        private final String errorMsg;

        public TaskFailed(String traceId, Long taskId, Long itemId, String agentCode, String errorMsg) {
            super("TASK_FAILED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
            this.errorMsg = errorMsg;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ContextUpdated extends AgentEvent {
        private final String key;

        public ContextUpdated(String traceId, Long taskId, String key) {
            super("CONTEXT_UPDATED", traceId, taskId, LocalDateTime.now());
            this.key = key;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ApprovalRequired extends AgentEvent {
        private final Long itemId;
        private final String agentCode;

        public ApprovalRequired(String traceId, Long taskId, Long itemId, String agentCode) {
            super("APPROVAL_REQUIRED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
        }
    }

    @Getter
    @ToString(callSuper = true)
    public static class ApprovalResolved extends AgentEvent {
        private final Long itemId;
        private final boolean approved;

        public ApprovalResolved(String traceId, Long taskId, Long itemId, boolean approved) {
            super("APPROVAL_RESOLVED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.approved = approved;
        }
    }
}
