package com.agentoffice.config;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
public class EventBusConfig {

    @Bean
    public EventBus eventBus() {
        return new AsyncEventBus("agent-event-bus",
                Executors.newFixedThreadPool(4, r -> {
                    Thread t = new Thread(r, "eventbus-worker");
                    t.setDaemon(true);
                    return t;
                }));
    }
}
