package com.agentoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    @Bean("taskExecutor")
    public ThreadPoolExecutor taskExecutor(
            @Value("${agent.thread-pool.core-size:10}") int coreSize,
            @Value("${agent.thread-pool.max-size:50}") int maxSize,
            @Value("${agent.thread-pool.queue-capacity:200}") int queueCapacity,
            @Value("${agent.thread-pool.keep-alive-seconds:60}") int keepAlive) {
        return new ThreadPoolExecutor(
                coreSize, maxSize, keepAlive, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> new Thread(r, "agent-task"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
