package ru.netology.currencyparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.executors.cpu")
public class CpuExecutorProperties {
    private final int cores = Runtime.getRuntime().availableProcessors();
    private int coreSize = Math.max(2, cores - 1);
    private int maxSize  = Math.max(coreSize, cores * 2);
    private int queueCapacity = 200;
    private String threadNamePrefix = "CPU-";
}
