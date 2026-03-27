package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.service.EventClusterService;
import com.example.demo.service.IntelligenceService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/intelligences")
public class IntelligenceController {

    private final IntelligenceService intelligenceService;
    private final EventClusterService eventClusterService;
    private final IntelligenceRepository intelligenceRepository;

    public IntelligenceController(IntelligenceService intelligenceService,
                                  EventClusterService eventClusterService,
                                  IntelligenceRepository intelligenceRepository) {
        this.intelligenceService = intelligenceService;
        this.eventClusterService = eventClusterService;
        this.intelligenceRepository = intelligenceRepository;
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

    /** 相关情报推荐 */
    @GetMapping("/{id}/related")
    public ResponseEntity<?> related(@PathVariable Long id,
                                     @RequestParam(defaultValue = "5") int limit) {
        try {
            var detail = intelligenceService.getDetail(id);
            var intel = detail.intelligence();
            // Simple: return recent intelligences excluding self, sorted by time
            var all = intelligenceRepository
                    .findByCreatedAtAfterOrderByLatestArticleTimeDesc(
                            java.time.LocalDateTime.now().minusHours(72));
            var related = all.stream()
                    .filter(i -> !i.getId().equals(id))
                    .limit(limit)
                    .map(i -> {
                        Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("id", i.getId());
                        item.put("title", i.getTitle());
                        item.put("summary", i.getSummary());
                        item.put("primarySource", i.getPrimarySource());
                        item.put("credibilityLevel", i.getCredibilityLevel());
                        item.put("tags", i.getTags());
                        item.put("latestArticleTime", i.getLatestArticleTime());
                        return item;
                    }).toList();
            return ResponseEntity.ok(Map.of("code", 200, "data", related));
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok(Map.of("code", 200, "data", List.of()));
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
