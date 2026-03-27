package com.example.demo.collector;

import com.example.demo.model.MarketData;
import com.example.demo.repository.MarketDataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Tushare A-share market data collector.
 * API: https://api.tushare.pro (POST with token)
 */
@Component
public class TushareCollector {

    private static final Logger log = LoggerFactory.getLogger(TushareCollector.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final MarketDataRepository marketDataRepository;

    @Value("${collector.tushare.token:}")
    private String token;

    // AI concept stocks (Tushare format: code.SZ/SH)
    private static final List<String> TS_CODES = List.of(
            "002230.SZ", "688256.SZ", "688041.SZ", "603019.SH",
            "688111.SZ", "300496.SZ", "300033.SZ", "688981.SH",
            "002049.SZ", "688008.SH", "603501.SH", "002415.SZ",
            "300124.SZ", "002594.SZ", "601127.SH"
    );

    public TushareCollector(MarketDataRepository marketDataRepository) {
        this.marketDataRepository = marketDataRepository;
    }

    public int collectAll() {
        if (token == null || token.isBlank()) {
            log.debug("Tushare token not configured, skip");
            return 0;
        }
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String tsCodes = String.join(",", TS_CODES);
        int stored = 0;

        try {
            // daily_basic: PE, turnover etc
            Map<String, Object> body = Map.of(
                    "api_name", "daily",
                    "token", token,
                    "params", Map.of("ts_code", tsCodes, "trade_date", today),
                    "fields", "ts_code,trade_date,open,high,low,close,pct_chg,vol,amount"
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tushare.pro"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            if (root.path("code").asInt() != 0) {
                log.warn("Tushare error: {}", root.path("msg").asText());
                return 0;
            }

            JsonNode data = root.path("data");
            JsonNode fields = data.path("fields");
            JsonNode items = data.path("items");

            // Build field index
            int idxCode = -1, idxClose = -1, idxPctChg = -1, idxVol = -1;
            for (int i = 0; i < fields.size(); i++) {
                String f = fields.get(i).asText();
                if ("ts_code".equals(f)) idxCode = i;
                else if ("close".equals(f)) idxClose = i;
                else if ("pct_chg".equals(f)) idxPctChg = i;
                else if ("vol".equals(f)) idxVol = i;
            }

            for (JsonNode row : items) {
                String tsCode = row.get(idxCode).asText();
                String stockCode = tsCode.substring(0, 6);
                MarketData md = new MarketData();
                md.setStockCode(stockCode);
                md.setStockName(tsCode); // will be overwritten if name available
                if (idxClose >= 0) md.setCurrentPrice(row.get(idxClose).asDouble());
                if (idxPctChg >= 0) md.setChangePercent(row.get(idxPctChg).asDouble());
                if (idxVol >= 0) md.setVolume(row.get(idxVol).asDouble());
                md.setSector("AI");
                md.setDataTime(LocalDateTime.now());
                marketDataRepository.save(md);
                stored++;
            }
            log.info("Tushare collected {} stocks", stored);
        } catch (Exception e) {
            log.error("Tushare collect failed: {}", e.getMessage());
        }
        return stored;
    }
}
