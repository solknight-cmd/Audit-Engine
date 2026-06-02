package com.vault.audit_engine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // This tells Spring to run methods marked with @Async in a separate thread pool
}
