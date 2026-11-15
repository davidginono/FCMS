package com.foresight.fcms.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class BackgroundConfig {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler mailScheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("fcms-mail-");
        return s;
    }

    @Bean("backupScheduler")
    public ThreadPoolTaskScheduler backupScheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("fcms-backup-");
        s.setWaitForTasksToCompleteOnShutdown(false);
        return s;
    }
}
