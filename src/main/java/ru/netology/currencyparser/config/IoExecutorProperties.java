package ru.netology.currencyparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.executors.io")
public class IoExecutorProperties {
    private final int cores = Runtime.getRuntime().availableProcessors();
    private int coreSize = Math.min(32, Math.max(4, cores * 2));
    private int maxSize  = Math.min(64, Math.max(coreSize, cores * 4));
    private int queueCapacity = 200;
    private String threadNamePrefix = "IO-";
}
