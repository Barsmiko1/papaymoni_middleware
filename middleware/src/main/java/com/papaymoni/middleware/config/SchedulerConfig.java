package com.papaymoni.middleware.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Value("${app.scheduler.enabled}")
    private boolean schedulerEnabled;

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix("PapayMoni-Scheduler-");
        return threadPoolTaskScheduler;
    }

    @Bean
    public SchedulerStatus schedulerStatus() {
        return new SchedulerStatus(schedulerEnabled);
    }

    public static class SchedulerStatus {
        private volatile boolean enabled;

        public SchedulerStatus(boolean initialState) {
            this.enabled = initialState;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

