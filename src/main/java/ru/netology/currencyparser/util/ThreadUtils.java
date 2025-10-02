package ru.netology.currencyparser.util;

import java.util.Map;

public final class ThreadUtils {
    private ThreadUtils() {}

    public static void printAllActiveThreads() {
        // TODO: безопасно распечатать все активные потоки
        // Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        // map.keySet().forEach(t -> System.out.printf("Thread: %s (%s)%n", t.getName(), t.getState()));
    }
}
