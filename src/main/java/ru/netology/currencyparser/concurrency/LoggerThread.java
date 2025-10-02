package ru.netology.currencyparser.concurrency;

public class LoggerThread extends Thread {
    public LoggerThread(String name) { super(name); }

    @Override
    public void run() {
        // TODO: вывод имени потока и порядкового номера/сообщения
    }
}
