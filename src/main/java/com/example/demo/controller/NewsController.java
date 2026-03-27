package com.example.demo.controller;

import com.example.demo.collector.MarketDataCollector;
import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.MarketData;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.MarketDataRepository;
import com.example.demo.repository.NewsArticleRepository;
import com.example.demo.service.NewsCollectorService;
import com.example.demo.service.SmartQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NewsController {

    private final NewsCollectorService newsCollectorService;
    private final MarketDataCollector marketDataCollector;
    private final NewsArticleRepository newsArticleRepository;
    private final MarketDataRepository marketDataRepository;
    private final SmartQueryService smartQueryService;
    private final VectorSearchService vectorSearchService;

    public NewsController(NewsCollectorService newsCollectorService,
                          MarketDataCollector marketDataCollector,
                          NewsArticleRepository newsArticleRepository,
                          MarketDataRepository marketDataRepository,
                          SmartQueryService smartQueryService,
                          VectorSearchService vectorSearchService) {
        this.newsCollectorService = newsCollectorService;
        this.marketDataCollector = marketDataCollector;
        this.newsArticleRepository = newsArticleRepository;
        this.marketDataRepository = marketDataRepository;
        this.smartQueryService = smartQueryService;
        this.vectorSearchService = vectorSearchService;
    }

    /** 手动触发新闻采集 */
    @PostMapping("/news/collect")
    public ResponseEntity<Map<String, Object>> collectNews() {
        var result = newsCollectorService.collectAll();
        return ResponseEntity.ok(Map.of(
                "collected", result.collected(),
                "deduplicated", result.deduplicated(),
                "stored", result.stored(),
                "sources", result.sources().stream().map(s -> Map.of(
                        "name", s.name(),
                        "collected", s.collected(),
                        "deduplicated", s.deduplicated(),
                        "stored", s.stored()
                )).toList()
        ));
    }

    /** 查询新闻列表 */
    @GetMapping("/news")
    public List<NewsArticle> listNews(
            @RequestParam(defaultValue = "24") int hours) {
        return newsArticleRepository.findByCollectedAtAfterOrderByCollectedAtDesc(
                LocalDateTime.now().minusHours(hours));
    }

    /** 手动触发行情采集 */
    @PostMapping("/market/collect")
    public ResponseEntity<Map<String, Object>> collectMarket() {
        var result = marketDataCollector.collectAll();
        return ResponseEntity.ok(Map.of(
                "collected", result.collected(),
                "stored", result.stored()
        ));
    }

    /** 查询行情列表 */
    @GetMapping("/market")
    public List<MarketData> listMarket() {
        return marketDataRepository.findAll();
    }

    /** 智能问答：语义检索 + LLM推理 */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> smartQuery(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question不能为空"));
        }
        var result = smartQueryService.query(question);
        return ResponseEntity.ok(Map.of(
                "answer", result.answer(),
                "matchedCount", result.matchedCount(),
                "relatedNews", result.relatedNews().stream().map(n -> Map.of(
                        "id", n.getId(),
                        "title", n.getTitle(),
                        "sourceName", n.getSourceName(),
                        "credibilityLevel", n.getCredibilityLevel() != null ? n.getCredibilityLevel() : "unknown",
                        "sourceUrl", n.getSourceUrl() != null ? n.getSourceUrl() : ""
                )).toList()
        ));
    }

    /** 系统状态 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "totalNews", newsArticleRepository.count(),
                "totalMarket", marketDataRepository.count(),
                "vectorCacheSize", vectorSearchService.cacheSize()
        );
    }

    /** 实时日志（供前端轮询） */
    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(defaultValue = "50") int count) {
        return Map.of(
                "logs", com.example.demo.config.InMemoryLogAppender.getRecentLogs(count),
                "total", com.example.demo.config.InMemoryLogAppender.totalSize()
        );
    }
}
