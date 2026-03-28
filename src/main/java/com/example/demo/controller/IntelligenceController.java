package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.UserProfileRepository;
import com.example.demo.service.AnalysisService;
import com.example.demo.service.EventClusterService;
import com.example.demo.service.IntelligenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/intelligences")
public class IntelligenceController {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceController.class);

    private final IntelligenceService intelligenceService;
    private final EventClusterService eventClusterService;
    private final IntelligenceRepository intelligenceRepository;
    private final UserProfileRepository userProfileRepository;
    private final AnalysisService analysisService;

    public IntelligenceController(IntelligenceService intelligenceService,
                                  EventClusterService eventClusterService,
                                  IntelligenceRepository intelligenceRepository,
                                  UserProfileRepository userProfileRepository,
                                  AnalysisService analysisService) {
        this.intelligenceService = intelligenceService;
        this.eventClusterService = eventClusterService;
        this.intelligenceRepository = intelligenceRepository;
        this.userProfileRepository = userProfileRepository;
        this.analysisService = analysisService;
    }

    /** Intelligence list with personalized sorting based on user profile */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long userId) {

        Page<Intelligence> result = intelligenceService.listIntelligences(hours, page, size);
        List<Intelligence> items = new java.util.ArrayList<>(result.getContent());

        // Load user profile for personalized sorting
        String focusAreas = null;
        String holdings = null;
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            focusAreas = profile.getFocusAreas();
            holdings = profile.getHoldings();
        }

        // Personalized sorting
        if ((focusAreas != null && !focusAreas.isBlank()) || (holdings != null && !holdings.isBlank())) {
            Set<String> areas = focusAreas != null ? Set.of(focusAreas.toLowerCase().split(",")) : Set.of();
            Set<String> stocks = holdings != null ? Set.of(holdings.toUpperCase().split(",")) : Set.of();
            items.sort((a, b) -> {
                int scoreA = calcRelevance(a, areas, stocks);
                int scoreB = calcRelevance(b, areas, stocks);
                if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
                if (a.getLatestArticleTime() != null && b.getLatestArticleTime() != null)
                    return b.getLatestArticleTime().compareTo(a.getLatestArticleTime());
                return 0;
            });
        }

        var content = items.stream().map(intel -> {
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

    /** 情报详情（含个性化分析） */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long userId) {
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

            // 相关情报（内嵌返回，不需要单独请求）
            var allRecent = intelligenceRepository
                    .findByCreatedAtAfterOrderByLatestArticleTimeDesc(
                            java.time.LocalDateTime.now().minusHours(72));
            var related = allRecent.stream()
                    .filter(i -> !i.getId().equals(id))
                    .limit(5)
                    .map(i -> {
                        Map<String, Object> ri = new LinkedHashMap<>();
                        ri.put("id", i.getId());
                        ri.put("title", i.getTitle());
                        ri.put("summary", i.getSummary());
                        ri.put("primarySource", i.getPrimarySource());
                        ri.put("sourceCount", i.getSourceCount());
                        ri.put("credibilityScore", i.getCredibilityScore());
                        ri.put("latestArticleTime", i.getLatestArticleTime());
                        return ri;
                    }).toList();
            data.put("relatedIntelligences", related);

            // 个性化影响分析（带超时保护的LLM调用）
            if (userId > 0) {
                Map<String, Object> personalized = new LinkedHashMap<>();

                UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
                if (profile != null) {
                    Map<String, Object> profileCard = new LinkedHashMap<>();
                    profileCard.put("investorType", profile.getInvestorType());
                    profileCard.put("investmentCycle", profile.getInvestmentCycle());
                    profileCard.put("focusAreas", profile.getFocusAreas() != null
                            ? List.of(profile.getFocusAreas().split(",")) : List.of());
                    profileCard.put("holdings", profile.getHoldings() != null
                            ? List.of(profile.getHoldings().split(",")) : List.of());
                    personalized.put("userProfile", profileCard);
                } else {
                    personalized.put("userProfile", null);
                }

                // LLM分析：带15秒超时保护
                final Long fUserId = userId;
                final Long fId = id;
                try {
                    var future = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            analysisService.generateAnalysis(fUserId, fId));
                    Map<String, Object> analysis = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                    personalized.put("analysis", analysis.get("analysis"));
                    personalized.put("impacts", analysis.get("impacts"));
                    personalized.put("suggestion", analysis.get("suggestion"));
                    personalized.put("risks", analysis.get("risks"));
                    personalized.put("userContext", analysis.get("userContext"));
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("LLM分析超时(15s) for intel {}", id);
                    personalized.put("analysis", "分析超时，请稍后重试");
                    personalized.put("impacts", List.of());
                    personalized.put("suggestion", null);
                    personalized.put("risks", List.of());
                } catch (Exception e) {
                    log.warn("Analysis failed for intel {}: {}", id, e.getMessage());
                    personalized.put("analysis", null);
                    personalized.put("impacts", List.of());
                    personalized.put("suggestion", null);
                    personalized.put("risks", List.of());
                }

                data.put("personalizedAnalysis", personalized);
            } else {
                data.put("personalizedAnalysis", null);
            }

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

    /**
     * Calculate relevance score for personalized sorting.
     * Higher score = more relevant to user's focus areas and holdings.
     */
    private int calcRelevance(Intelligence intel, Set<String> focusAreas, Set<String> holdings) {
        int score = 0;

        // Match tags against focus areas (+10 per match)
        String tags = intel.getTags();
        if (tags != null && !focusAreas.isEmpty()) {
            for (String tag : tags.toLowerCase().split(",")) {
                if (focusAreas.contains(tag.trim())) score += 10;
            }
        }

        // Match related stocks against holdings (+20 per match, stocks matter more)
        String stocks = intel.getRelatedStocks();
        if (stocks != null && !holdings.isEmpty()) {
            for (String stock : stocks.toUpperCase().split(",")) {
                if (holdings.contains(stock.trim())) score += 20;
            }
        }

        // Boost high priority
        if ("high".equals(intel.getPriority())) score += 5;

        // Boost multi-source (more credible)
        if (intel.getSourceCount() != null && intel.getSourceCount() > 1) score += 3;

        return score;
    }
}
