package com.example.demo.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 内存日志缓存，保留最近500条，供前端实时读取
 */
public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_SIZE = 500;
    private static final ConcurrentLinkedDeque<String> LOGS = new ConcurrentLinkedDeque<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent event) {
        String time = FMT.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String line = String.format("%s %-5s %s - %s",
                time, event.getLevel(), event.getLoggerName(), event.getFormattedMessage());
        LOGS.addLast(line);
        while (LOGS.size() > MAX_SIZE) LOGS.pollFirst();
    }

    public static java.util.List<String> getRecentLogs(int count) {
        var list = new java.util.ArrayList<>(LOGS);
        int from = Math.max(0, list.size() - count);
        return list.subList(from, list.size());
    }

    public static int totalSize() {
        return LOGS.size();
    }
}
