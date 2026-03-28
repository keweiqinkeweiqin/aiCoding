package com.example.demo.controller;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.Intelligence;
import com.example.demo.model.UserHolding;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.UserHoldingRepository;
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
    private final UserHoldingRepository userHoldingRepository;
    private final AnalysisService analysisService;
    private final VectorSearchService vectorSearchService;

    public IntelligenceController(IntelligenceService intelligenceService,
                                  EventClusterService eventClusterService,
                                  IntelligenceRepository intelligenceRepository,
                                  UserProfileRepository userProfileRepository,
                                  UserHoldingRepository userHoldingRepository,
                                  AnalysisService analysisService,
                                  VectorSearchService vectorSearchService) {
        this.intelligenceService = intelligenceService;
        this.eventClusterService = eventClusterService;
        this.intelligenceRepository = intelligenceRepository;
        this.userProfileRepository = userProfileRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.analysisService = analysisService;
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * Intelligence list.
     * scene=home (default): C端首页，每个优先级(high/medium/low)各返回一条，个性化排序
     * scene=admin: 控制面板，返回全量分页数据
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "home") String scene) {

        if ("admin".equals(scene)) {
            return listAdmin(hours, page, size, userId);
        }
        return listHome(hours, userId);
    }

    /** C端首页：画像相关性排序后取前3条 */
    private ResponseEntity<Map<String, Object>> listHome(int hours, Long userId) {
        var all = intelligenceRepository
                .findByCreatedAtAfterOrderByLatestArticleTimeDesc(
                        java.time.LocalDateTime.now().minusHours(hours));

        if (all.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", Map.of("content", List.of(),
                            "totalElements", 0, "totalPages", 0, "currentPage", 0)
            ));
        }

        // 个性化排序
        List<Intelligence> sorted = new ArrayList<>(all);
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            String fa = profile.getFocusAreas();
            String h = profile.getHoldings();
            if ((fa != null && !fa.isBlank()) || (h != null && !h.isBlank())) {
                Set<String> areas = fa != null ? Set.of(fa.toLowerCase().split(",")) : Set.of();
                Set<String> stocks = h != null ? Set.of(h.toUpperCase().split(",")) : Set.of();
                sorted.sort((a, b) -> {
                    int diff = Integer.compare(calcRelevance(b, areas, stocks), calcRelevance(a, areas, stocks));
                    if (diff != 0) return diff;
                    if (a.getLatestArticleTime() != null && b.getLatestArticleTime() != null)
                        return b.getLatestArticleTime().compareTo(a.getLatestArticleTime());
                    return 0;
                });
            }
        }

        // 取前3条
        var picked = sorted.stream().limit(3).toList();
        var content = picked.stream().map(this::toListItem).toList();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "content", content,
                        "totalElements", content.size(),
                        "totalPages", 1,
                        "currentPage", 0
                )
        ));
    }

    /** 控制面板：全量分页 */
    private ResponseEntity<Map<String, Object>> listAdmin(int hours, int page, int size, Long userId) {
        Page<Intelligence> result = intelligenceService.listIntelligences(hours, page, size);
        List<Intelligence> items = new ArrayList<>(result.getContent());

        // 个性化排序
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            String fa = profile.getFocusAreas();
            String h = profile.getHoldings();
            if ((fa != null && !fa.isBlank()) || (h != null && !h.isBlank())) {
                Set<String> areas = fa != null ? Set.of(fa.toLowerCase().split(",")) : Set.of();
                Set<String> stocks = h != null ? Set.of(h.toUpperCase().split(",")) : Set.of();
                items.sort((a, b) -> {
                    int diff = Integer.compare(calcRelevance(b, areas, stocks), calcRelevance(a, areas, stocks));
                    if (diff != 0) return diff;
                    if (a.getLatestArticleTime() != null && b.getLatestArticleTime() != null)
                        return b.getLatestArticleTime().compareTo(a.getLatestArticleTime());
                    return 0;
                });
            }
        }

        var content = items.stream().map(this::toListItem).toList();
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

    private Map<String, Object> toListItem(Intelligence intel) {
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

    /** 情报详情（纯DB，不调LLM，秒回） */
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

            // 相关情报（基于情报向量语义相似度）
            try {
                List<VectorSearchService.ScoredId> similar =
                        vectorSearchService.searchIntelligences(
                                intel.getTitle() + " " + (intel.getSummary() != null ? intel.getSummary() : ""), 6);
                List<Long> relatedIds = similar.stream()
                        .filter(s -> !s.id().equals(id))
                        .limit(5)
                        .map(VectorSearchService.ScoredId::id)
                        .toList();
                List<Intelligence> relatedIntels = intelligenceRepository.findAllById(relatedIds);
                // 保持相似度排序
                Map<Long, Integer> orderMap = new LinkedHashMap<>();
                for (int idx = 0; idx < relatedIds.size(); idx++) orderMap.put(relatedIds.get(idx), idx);
                relatedIntels.sort(Comparator.comparingInt(i -> orderMap.getOrDefault(i.getId(), 999)));
                var related = relatedIntels.stream()
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
            } catch (Exception e) {
                data.put("relatedIntelligences", List.of());
            }

            // personalizedAnalysis 不再内嵌，前端单独请求 /{id}/analysis
            data.put("personalizedAnalysis", null);

            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "code", 404, "message", e.getMessage()));
        }
    }

    /** 个性化分析（独立接口，调LLM） */
    @GetMapping("/{id}/analysis")
    public ResponseEntity<Map<String, Object>> personalizedAnalysis(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId <= 0) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of()));
        }
        try {
            // 确认情报存在
            intelligenceRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Intelligence not found: " + id));

            Map<String, Object> personalized = new LinkedHashMap<>();

            // 用户画像卡片
            UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                Map<String, Object> profileCard = new LinkedHashMap<>();
                profileCard.put("investorType", profile.getInvestorType());
                profileCard.put("investmentCycle", profile.getInvestmentCycle());
                profileCard.put("focusAreas", profile.getFocusAreas() != null
                        ? List.of(profile.getFocusAreas().split(",")) : List.of());
                var holdings = userHoldingRepository.findByUserId(userId);
                profileCard.put("holdings", holdings.stream().map(h -> {
                    Map<String, Object> hm = new LinkedHashMap<>();
                    hm.put("stockCode", h.getStockCode());
                    hm.put("stockName", h.getStockName());
                    hm.put("sector", h.getSector());
                    hm.put("percentage", h.getPercentage());
                    hm.put("costPrice", h.getCostPrice());
                    return hm;
                }).toList());
                personalized.put("userProfile", profileCard);
            } else {
                personalized.put("userProfile", null);
            }

            // LLM 分析
            Map<String, Object> analysis = analysisService.generateAnalysis(userId, id);
            personalized.put("analysis", analysis.get("analysis"));
            personalized.put("impacts", analysis.get("impacts"));
            personalized.put("suggestion", analysis.get("suggestion"));
            personalized.put("risks", analysis.get("risks"));
            personalized.put("userContext", analysis.get("userContext"));

            return ResponseEntity.ok(Map.of("code", 200, "data", personalized));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", e.getMessage()));
        } catch (Exception e) {
            log.warn("Analysis failed for intel {}: {}", id, e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("userProfile", null);
            fallback.put("analysis", null);
            fallback.put("impacts", List.of());
            fallback.put("suggestion", null);
            fallback.put("risks", List.of());
            return ResponseEntity.ok(Map.of("code", 200, "data", fallback));
        }
    }

    /** 相关情报推荐（基于情报向量语义相似度） */
    @GetMapping("/{id}/related")
    public ResponseEntity<?> related(@PathVariable Long id,
                                     @RequestParam(defaultValue = "5") int limit) {
        try {
            var detail = intelligenceService.getDetail(id);
            var intel = detail.intelligence();
            String queryText = intel.getTitle() + " " + (intel.getSummary() != null ? intel.getSummary() : "");
            List<VectorSearchService.ScoredId> similar =
                    vectorSearchService.searchIntelligences(queryText, limit + 1);
            List<Long> relatedIds = similar.stream()
                    .filter(s -> !s.id().equals(id))
                    .limit(limit)
                    .map(VectorSearchService.ScoredId::id)
                    .toList();
            List<Intelligence> relatedIntels = intelligenceRepository.findAllById(relatedIds);
            Map<Long, Integer> orderMap = new LinkedHashMap<>();
            for (int idx = 0; idx < relatedIds.size(); idx++) orderMap.put(relatedIds.get(idx), idx);
            relatedIntels.sort(Comparator.comparingInt(i -> orderMap.getOrDefault(i.getId(), 999)));
            var related = relatedIntels.stream()
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

    /** 清空所有情报的 content 缓存，下次访问详情时用最新 prompt 重新生成 */
    @PostMapping("/refresh-content")
    public ResponseEntity<Map<String, Object>> refreshContent() {
        int count = intelligenceService.clearAllContent();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of("cleared", count)
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
