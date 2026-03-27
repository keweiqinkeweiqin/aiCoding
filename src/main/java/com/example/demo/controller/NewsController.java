package com.example.demo.controller;

import com.example.demo.collector.MarketDataCollector;
import com.example.demo.model.MarketData;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.MarketDataRepository;
import com.example.demo.repository.NewsArticleRepository;
import com.example.demo.service.NewsCollectorService;
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

    public NewsController(NewsCollectorService newsCollectorService,
                          MarketDataCollector marketDataCollector,
                          NewsArticleRepository newsArticleRepository,
                          MarketDataRepository marketDataRepository) {
        this.newsCollectorService = newsCollectorService;
        this.marketDataCollector = marketDataCollector;
        this.newsArticleRepository = newsArticleRepository;
        this.marketDataRepository = marketDataRepository;
    }

    /** 手动触发新闻采集 */
    @PostMapping("/news/collect")
    public ResponseEntity<Map<String, Object>> collectNews() {
        var result = newsCollectorService.collectAll();
        return ResponseEntity.ok(Map.of(
                "collected", result.collected(),
                "deduplicated", result.deduplicated(),
                "stored", result.stored()
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
}
