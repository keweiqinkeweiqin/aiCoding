package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * POST /api/search — 搜索情报
     * 支持语义搜索 + 关键词模糊匹配，合并去重
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.getOrDefault("keyword", "");
        int page = body.containsKey("page") ? ((Number) body.get("page")).intValue() : 0;
        int size = body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        String sortBy = (String) body.getOrDefault("sortBy", "relevance");

        SearchService.SearchResult result = searchService.search(keyword, page, size, sortBy);

        var content = result.content().stream().map(this::toMap).toList();

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "content", content,
                        "totalElements", result.totalElements(),
                        "totalPages", result.totalPages(),
                        "currentPage", result.currentPage()
                )
        ));
    }

    /**
     * GET /api/search/trending — 热门搜索 Top N
     */
    @GetMapping("/trending")
    public ResponseEntity<Map<String, Object>> trending(
            @RequestParam(defaultValue = "5") int limit) {
        var items = searchService.getTrending(limit);

        var data = items.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("keyword", t.keyword());
            m.put("searchCount", t.searchCount());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    private Map<String, Object> toMap(Intelligence intel) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", intel.getId());
        item.put("priority", intel.getPriority());
        item.put("title", intel.getTitle());
        item.put("summary", intel.getSummary());
        item.put("primarySource", intel.getPrimarySource());
        item.put("credibilityLevel", intel.getCredibilityLevel());
        item.put("credibilityScore", intel.getCredibilityScore());
        item.put("sourceCount", intel.getSourceCount());
        item.put("sentiment", intel.getSentiment());
        item.put("sentimentScore", intel.getSentimentScore());
        item.put("relatedStocks", intel.getRelatedStocks());
        item.put("tags", intel.getTags());
        item.put("latestArticleTime", intel.getLatestArticleTime());
        item.put("createdAt", intel.getCreatedAt());
        return item;
    }
}
