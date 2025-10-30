package ru.netology.currencyparser.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties({IoExecutorProperties.class, CpuExecutorProperties.class})
public class ConcurrencyConfig {

    @Bean("ioPool")
    public Executor ioPool(IoExecutorProperties p) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(p.getCoreSize());
        ex.setMaxPoolSize(p.getMaxSize());
        ex.setQueueCapacity(p.getQueueCapacity());
        ex.setThreadNamePrefix(p.getThreadNamePrefix());
        ex.initialize();
        return ex;
    }

    @Bean("cpuPool")
    public Executor cpuPool(CpuExecutorProperties p) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(p.getCoreSize());
        ex.setMaxPoolSize(p.getMaxSize());
        ex.setQueueCapacity(p.getQueueCapacity());
        ex.setThreadNamePrefix(p.getThreadNamePrefix());
        ex.initialize();
        return ex;
    }
}
