package com.example.demo.collector;

import com.example.demo.model.MarketData;
import com.example.demo.repository.MarketDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MarketDataCollector {

    private static final Logger log = LoggerFactory.getLogger(MarketDataCollector.class);
    private final RestTemplate restTemplate;
    private final MarketDataRepository marketDataRepository;
    private final TushareCollector tushareCollector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${collector.market.licence:}")
    private String licence;

    // AI + 科技概念股（扩充版）
    private static final Map<String, String> AI_STOCKS = new java.util.LinkedHashMap<>() {{
        // AI 核心
        put("002230", "科大讯飞");
        put("688256", "寒武纪");
        put("688041", "海光信息");
        put("603019", "中科曙光");
        put("688111", "金山办公");
        put("300496", "中科创达");
        put("300033", "同花顺");
        // 算力/芯片
        put("688981", "中芯国际");
        put("002049", "紫光国微");
        put("688008", "澜起科技");
        put("603501", "韦尔股份");
        // 安防/机器人
        put("002415", "海康威视");
        put("002236", "大华股份");
        put("300124", "汇川技术");
        // 互联网/云计算
        put("600941", "中国移动");
        put("601138", "工业富联");
        put("688561", "奇安信");
        // 新能源车智能化
        put("002594", "比亚迪");
        put("601127", "赛力斯");
    }};

    public MarketDataCollector(RestTemplate restTemplate, MarketDataRepository marketDataRepository,
                               TushareCollector tushareCollector) {
        this.restTemplate = restTemplate;
        this.marketDataRepository = marketDataRepository;
        this.tushareCollector = tushareCollector;
    }

    public CollectResult collectAll() {
        if (licence == null || licence.isBlank()) {
            log.warn("麦蕊智数licence未配置，跳过行情采集");
            return new CollectResult(0, 0, 0);
        }

        int collected = 0;
        int stored = 0;
        for (var entry : AI_STOCKS.entrySet()) {
            try {
                String url = "https://api.mairui.club/hsrl/ssjy/" + entry.getKey() + "/" + licence;
                String json = restTemplate.getForObject(url, String.class);
                if (json == null) continue;

                JsonNode node = objectMapper.readTree(json);
                MarketData data = new MarketData();
                data.setStockCode(entry.getKey());
                data.setStockName(entry.getValue());
                data.setCurrentPrice(node.has("p") ? node.get("p").asDouble() : null);
                data.setChangePercent(node.has("pc") ? node.get("pc").asDouble() : null);
                data.setVolume(node.has("v") ? node.get("v").asDouble() : null);
                data.setTurnoverRate(node.has("hs") ? node.get("hs").asDouble() : null);
                data.setPeRatio(node.has("pe") ? node.get("pe").asDouble() : null);
                data.setSector("AI概念");

                if (node.has("t")) {
                    try {
                        data.setDataTime(LocalDateTime.parse(node.get("t").asText(),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    } catch (Exception ignored) {}
                }

                marketDataRepository.save(data);
                collected++;
                stored++;
                Thread.sleep(200); // 避免限流
            } catch (Exception e) {
                log.warn("采集{}行情失败: {}", entry.getKey(), e.getMessage());
            }
        }
        log.info("Mairui collected: {}, stored: {}", collected, stored);

        // Also collect from Tushare
        try {
            int tushareStored = tushareCollector.collectAll();
            collected += tushareStored;
            stored += tushareStored;
            log.info("Tushare collected: {}", tushareStored);
        } catch (Exception e) {
            log.warn("Tushare failed: {}", e.getMessage());
        }

        return new CollectResult(collected, 0, stored);
    }

    public record CollectResult(int collected, int deduplicated, int stored) {}
}
