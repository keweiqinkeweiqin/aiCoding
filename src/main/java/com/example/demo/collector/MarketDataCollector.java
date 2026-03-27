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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${collector.market.licence:}")
    private String licence;

    // AI概念股
    private static final Map<String, String> AI_STOCKS = Map.of(
            "002230", "科大讯飞",
            "300496", "中科创达",
            "688111", "金山办公",
            "300033", "同花顺",
            "002415", "海康威视",
            "603019", "中科曙光",
            "688256", "寒武纪",
            "688041", "海光信息"
    );

    public MarketDataCollector(RestTemplate restTemplate, MarketDataRepository marketDataRepository) {
        this.restTemplate = restTemplate;
        this.marketDataRepository = marketDataRepository;
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
        log.info("行情采集完成: collected={}, stored={}", collected, stored);
        return new CollectResult(collected, 0, stored);
    }

    public record CollectResult(int collected, int deduplicated, int stored) {}
}
