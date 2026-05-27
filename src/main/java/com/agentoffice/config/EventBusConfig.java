package com.agentoffice.config;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * Guava AsyncEventBus 配置：4 守护线程的异步事件总线，用于 Agent 任务生命周期事件的发布与消费。
 */
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
