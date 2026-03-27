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

    public InsightController(IntelligenceRepository intelligenceRepository,
                             MarketDataRepository marketDataRepository,
                             NewsArticleRepository newsArticleRepository) {
        this.intelligenceRepository = intelligenceRepository;
        this.marketDataRepository = marketDataRepository;
        this.newsArticleRepository = newsArticleRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> insight(
            @RequestParam(defaultValue = "7") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Intelligence> intels = intelligenceRepository
                .findByCreatedAtAfterOrderByLatestArticleTimeDesc(since);
        List<MarketData> markets = marketDataRepository.findAll();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", buildOverview(intels, markets, days));
        data.put("sectors", buildSectors(intels, 5));
        data.put("events", buildEvents(intels, 10));
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
        if (sentimentIndex >= 75) sentimentLabel = "Extreme Greed";
        else if (sentimentIndex >= 55) sentimentLabel = "Greed";
        else if (sentimentIndex >= 45) sentimentLabel = "Neutral";
        else if (sentimentIndex >= 25) sentimentLabel = "Fear";
        else sentimentLabel = "Extreme Fear";

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

        // Trend data: sentiment index per day
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int d = days - 1; d >= 0; d--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(d).withHour(0).withMinute(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long dayTotal = intels.stream().filter(i -> i.getCreatedAt() != null
                    && !i.getCreatedAt().isBefore(dayStart) && i.getCreatedAt().isBefore(dayEnd)).count();
            long dayPos = intels.stream().filter(i -> "positive".equals(i.getSentiment())
                    && i.getCreatedAt() != null && !i.getCreatedAt().isBefore(dayStart)
                    && i.getCreatedAt().isBefore(dayEnd)).count();
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
                tag = tag.trim();
                if (!tag.isEmpty()) tagCounts.merge(tag, 1L, Long::sum);
            }
        }

        // Sort by count, take top N
        List<Map.Entry<String, Long>> sorted = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (var entry : sorted) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("rank", rank++);
            s.put("name", entry.getKey());
            s.put("intelCount", entry.getValue());
            // Heat score: count * 10, capped at 99
            s.put("heatScore", Math.min(99, entry.getValue() * 10));
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
