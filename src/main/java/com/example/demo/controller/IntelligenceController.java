package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.service.EventClusterService;
import com.example.demo.service.IntelligenceService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/intelligences")
public class IntelligenceController {

    private final IntelligenceService intelligenceService;
    private final EventClusterService eventClusterService;

    public IntelligenceController(IntelligenceService intelligenceService,
                                  EventClusterService eventClusterService) {
        this.intelligenceService = intelligenceService;
        this.eventClusterService = eventClusterService;
    }

    /** 情报列表（分页） */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Intelligence> result = intelligenceService.listIntelligences(hours, page, size);

        var content = result.getContent().stream().map(intel -> {
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
        }).toList();

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "content", content,
                        "totalElements", result.getTotalElements(),
                        "totalPages", result.getTotalPages(),
                        "currentPage", result.getNumber()
                )
        ));
    }

    /** 情报详情 */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        try {
            var detail = intelligenceService.getDetail(id);
            var intel = detail.intelligence();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", intel.getId());
            data.put("priority", intel.getPriority());
            data.put("credibilityLevel", intel.getCredibilityLevel());
            data.put("credibilityScore", intel.getCredibilityScore());
            data.put("sourceCount", intel.getSourceCount());
            data.put("title", intel.getTitle());
            data.put("summary", intel.getSummary());
            data.put("content", intel.getContent());
            data.put("primarySource", intel.getPrimarySource());
            data.put("sentiment", intel.getSentiment());
            data.put("sentimentScore", intel.getSentimentScore());
            data.put("relatedStocks", intel.getRelatedStocks());
            data.put("tags", intel.getTags());
            data.put("latestArticleTime", intel.getLatestArticleTime());
            data.put("readTime", detail.readTime());

            // 信息来源列表
            var sources = detail.sources().stream().map(s -> {
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("articleId", s.articleId());
                src.put("sourceName", s.sourceName());
                src.put("credibilityTag", s.credibilityTag());
                src.put("title", s.title());
                src.put("sourceUrl", s.sourceUrl() != null ? s.sourceUrl() : "");
                return src;
            }).toList();
            data.put("sources", sources);

            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404, "message", e.getMessage()));
        }
    }

    /** 手动触发事件聚类（调试用） */
    @PostMapping("/cluster")
    public ResponseEntity<Map<String, Object>> cluster() {
        var result = eventClusterService.clusterRecent();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "created", result.created(),
                        "merged", result.merged()
                )
        ));
    }
}
