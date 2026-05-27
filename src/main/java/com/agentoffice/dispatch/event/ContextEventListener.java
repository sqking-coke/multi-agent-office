package com.agentoffice.dispatch.event;

import com.agentoffice.agent.event.AgentEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audit-logging for global context changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextEventListener {

    private final EventBus eventBus;

    @PostConstruct
    public void init() {
        eventBus.register(this);
        log.info("ContextEventListener registered on EventBus");
    }

    @Subscribe
    public void onContextUpdated(AgentEvent.ContextUpdated event) {
        log.info("[{}] Global context updated: key={}", event.getTraceId(), event.getKey());
    }
}
