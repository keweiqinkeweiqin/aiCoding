package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.model.MarketData;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.MarketDataRepository;
import com.example.demo.repository.NewsArticleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/insight")
public class InsightController {

    private final IntelligenceRepository intelligenceRepository;
    private final MarketDataRepository marketDataRepository;
    private final NewsArticleRepository newsArticleRepository;

    // 常见英文标签 → 中文映射（兼容历史数据）
    private static final Map<String, String> TAG_CN_MAP = Map.ofEntries(
            Map.entry("chip", "芯片"), Map.entry("Chip", "芯片"),
            Map.entry("semiconductor", "半导体"), Map.entry("Semiconductor", "半导体"),
            Map.entry("robot", "机器人"), Map.entry("Robot", "机器人"),
            Map.entry("cloud", "云计算"), Map.entry("Cloud", "云计算"),
            Map.entry("autonomous driving", "自动驾驶"),
            Map.entry("EV", "新能源车"), Map.entry("ev", "新能源车"),
            Map.entry("battery", "电池"), Map.entry("Battery", "电池"),
            Map.entry("quantum", "量子计算"), Map.entry("Quantum", "量子计算"),
            Map.entry("biotech", "生物科技"), Map.entry("Biotech", "生物科技"),
            Map.entry("fintech", "金融科技"), Map.entry("Fintech", "金融科技"),
            Map.entry("cybersecurity", "网络安全"),
            Map.entry("metaverse", "元宇宙"), Map.entry("Metaverse", "元宇宙"),
            Map.entry("GPU", "GPU"), Map.entry("LLM", "大模型"),
            Map.entry("large model", "大模型"),
            Map.entry("data center", "数据中心"),
            Map.entry("trade", "贸易"), Map.entry("tariff", "关税"),
            Map.entry("regulation", "监管"), Map.entry("IPO", "IPO"),
            Map.entry("earnings", "财报"), Map.entry("merger", "并购")
    );

    private static String translateTag(String tag) {
        return TAG_CN_MAP.getOrDefault(tag, tag);
    }

    public InsightController(IntelligenceRepository intelligenceRepository,
                             MarketDataRepository marketDataRepository,
                             NewsArticleRepository newsArticleRepository) {
        this.intelligenceRepository = intelligenceRepository;
        this.marketDataRepository = marketDataRepository;
        this.newsArticleRepository = newsArticleRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> insight(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "20") int eventLimit) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Intelligence> intels = intelligenceRepository
                .findByLatestArticleTimeAfterOrderByLatestArticleTimeDesc(since);

        // fallback: 如果按 latestArticleTime 查不到数据，用 createdAt 兜底
        if (intels.isEmpty()) {
            intels = intelligenceRepository
                    .findByCreatedAtAfterOrderByLatestArticleTimeDesc(since);
        }

        List<MarketData> markets = marketDataRepository.findAll();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", buildOverview(intels, markets, days));
        data.put("sectors", buildSectors(intels, 5));
        data.put("events", buildEvents(intels, eventLimit));
        data.put("reports", buildReports(since, 10));

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    private Map<String, Object> buildOverview(List<Intelligence> intels, List<MarketData> markets, int days) {
        Map<String, Object> o = new LinkedHashMap<>();
        long total = intels.size();
        long pos = intels.stream().filter(i -> "positive".equals(i.getSentiment())).count();
        long neg = intels.stream().filter(i -> "negative".equals(i.getSentiment())).count();

        int sentimentIndex = total > 0 ? (int) Math.round((double) pos / total * 100) : 50;
        String sentimentLabel;
        if (sentimentIndex >= 70) sentimentLabel = "偏多";
        else if (sentimentIndex >= 40) sentimentLabel = "中性";
        else sentimentLabel = "偏空";

        o.put("sentimentIndex", sentimentIndex);
        o.put("sentimentLabel", sentimentLabel);
        o.put("totalIntelligences", total);
        o.put("positiveCount", pos);
        o.put("negativeCount", neg);

        // AI heat index: % of intels with AI-related tags
        long aiCount = intels.stream().filter(i -> {
            String tags = i.getTags();
            return tags != null && (tags.contains("AI") || tags.contains("chip") || tags.contains("LLM"));
        }).count();
        o.put("aiHeatPercent", total > 0 ? Math.round((double) aiCount / total * 100) : 0);

        // Market stats
        long up = markets.stream().filter(m -> m.getChangePercent() != null && m.getChangePercent() > 0).count();
        long down = markets.stream().filter(m -> m.getChangePercent() != null && m.getChangePercent() < 0).count();
        o.put("stockUp", up);
        o.put("stockDown", down);

        // Trend data: sentiment index per day (based on latestArticleTime)
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int d = days - 1; d >= 0; d--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(d).withHour(0).withMinute(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long dayTotal = intels.stream().filter(i -> i.getLatestArticleTime() != null
                    && !i.getLatestArticleTime().isBefore(dayStart) && i.getLatestArticleTime().isBefore(dayEnd)).count();
            long dayPos = intels.stream().filter(i -> "positive".equals(i.getSentiment())
                    && i.getLatestArticleTime() != null && !i.getLatestArticleTime().isBefore(dayStart)
                    && i.getLatestArticleTime().isBefore(dayEnd)).count();
            int dayIndex = dayTotal > 0 ? (int) Math.round((double) dayPos / dayTotal * 100) : 50;
            trend.add(Map.of("date", dayStart.toLocalDate().toString(), "value", dayIndex));
        }
        o.put("trendData", trend);
        return o;
    }

    private List<Map<String, Object>> buildSectors(List<Intelligence> intels, int limit) {
        // Count tags as sectors
        Map<String, Long> tagCounts = new LinkedHashMap<>();
        for (Intelligence i : intels) {
            if (i.getTags() == null) continue;
            for (String tag : i.getTags().split(",")) {
                tag = translateTag(tag.trim());
                if (!tag.isEmpty()) tagCounts.merge(tag, 1L, Long::sum);
            }
        }

        // Sort by count, take top N
        List<Map.Entry<String, Long>> sorted = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();

        // Use relative scoring: top tag gets 99, others scale proportionally
        long maxCount = sorted.isEmpty() ? 1 : sorted.get(0).getValue();

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (var entry : sorted) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("rank", rank++);
            s.put("name", entry.getKey());
            s.put("intelCount", entry.getValue());
            // Heat score: proportional to max, range 20-99
            long heatScore = Math.max(20, Math.round((double) entry.getValue() / maxCount * 99));
            s.put("heatScore", heatScore);
            result.add(s);
        }
        return result;
    }

    private List<Map<String, Object>> buildEvents(List<Intelligence> intels, int limit) {
        return intels.stream()
                .sorted(Comparator.comparing(Intelligence::getLatestArticleTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(i -> {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("id", i.getId());
                    e.put("time", i.getLatestArticleTime());
                    e.put("title", i.getTitle());
                    e.put("summary", i.getSummary());
                    e.put("sourceCount", i.getSourceCount());
                    // Impact tag based on priority + credibility
                    String impact;
                    if ("high".equals(i.getPriority())) impact = "major";
                    else if (i.getCredibilityScore() != null && i.getCredibilityScore() >= 0.7) impact = "moderate";
                    else if ("positive".equals(i.getSentiment())) impact = "positive";
                    else impact = "minor";
                    e.put("impactTag", impact);
                    e.put("sentiment", i.getSentiment());
                    e.put("tags", i.getTags());
                    return e;
                }).toList();
    }

    private List<Map<String, Object>> buildReports(LocalDateTime since, int limit) {
        // Filter news articles from report sources
        List<NewsArticle> all = newsArticleRepository
                .findByCollectedAtAfterOrderByCollectedAtDesc(since);
        return all.stream()
                .filter(a -> a.getSourceName() != null && (
                        a.getSourceName().contains("Research") || a.getSourceName().contains("Report")
                        || a.getSourceName().contains("EastMoney") || a.getSourceName().contains("report")))
                .limit(limit)
                .map(a -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", a.getId());
                    r.put("title", a.getTitle());
                    r.put("source", a.getSourceName());
                    r.put("sourceUrl", a.getSourceUrl());
                    r.put("publishedAt", a.getCollectedAt());
                    r.put("summary", a.getSummary());
                    return r;
                }).toList();
    }
}
