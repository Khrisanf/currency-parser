package ru.netology.currencyparser.concurrency;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "demo.threads", name = "enabled", havingValue = "true")
public class ThreadDemoRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // TODO: создать и запустить два потока с понятными именами
        // new Thread(new CounterWorker(), "CounterWorker").start();
        // new LoggerThread("LoggerThread").start();
        // ThreadUtils.printAllActiveThreads();
    }
}
