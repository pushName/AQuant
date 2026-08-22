package com.brotherc.aquant.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 智能分析单机线程池配置。 */
@Configuration
public class AnalysisExecutorConfig {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService analysisExecutor(@Value("${analysis.executor.threads:2}") int configuredThreads) {
        int threads = Math.max(1, Math.min(2, configuredThreads));
        return Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "analysis-job-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
