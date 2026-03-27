package com.example.demo.config;

import com.example.demo.collector.MarketDataCollector;
import com.example.demo.service.NewsCollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    private final NewsCollectorService newsCollectorService;
    private final MarketDataCollector marketDataCollector;

    public SchedulerConfig(NewsCollectorService newsCollectorService,
                           MarketDataCollector marketDataCollector) {
        this.newsCollectorService = newsCollectorService;
        this.marketDataCollector = marketDataCollector;
    }

    /** 新闻采集 - 每30分钟 */
    @Scheduled(fixedRate = 1800000, initialDelay = 5000)
    public void collectNews() {
        log.info("定时任务: 开始采集新闻...");
        newsCollectorService.collectAll();
    }

    /** 行情采集 - 每5分钟 */
    @Scheduled(fixedRate = 300000, initialDelay = 10000)
    public void collectMarketData() {
        log.info("定时任务: 开始采集行情...");
        marketDataCollector.collectAll();
    }
}
