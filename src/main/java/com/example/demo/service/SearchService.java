package com.example.demo.service;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.Intelligence;
import com.example.demo.model.TrendingSearch;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.TrendingSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final double MIN_SIMILARITY = 0.35;

    // 中英文标签双向映射（与 IntelligenceController 保持一致）
    private static final Map<String, String> EN_TO_CN = Map.ofEntries(
            Map.entry("chip", "芯片"), Map.entry("semiconductor", "半导体"),
            Map.entry("robot", "机器人"), Map.entry("cloud", "云计算"),
            Map.entry("autonomous driving", "自动驾驶"),
            Map.entry("ev", "新能源车"), Map.entry("battery", "电池"),
            Map.entry("quantum", "量子计算"), Map.entry("biotech", "生物科技"),
            Map.entry("fintech", "金融科技"), Map.entry("cybersecurity", "网络安全"),
            Map.entry("metaverse", "元宇宙"), Map.entry("gpu", "GPU"),
            Map.entry("llm", "大模型"), Map.entry("large model", "大模型"),
            Map.entry("data center", "数据中心"),
            Map.entry("trade", "贸易"), Map.entry("tariff", "关税"),
            Map.entry("regulation", "监管"), Map.entry("ipo", "IPO"),
            Map.entry("earnings", "财报"), Map.entry("merger", "并购")
    );
    private static final Map<String, String> CN_TO_EN = new HashMap<>();
    static {
        EN_TO_CN.forEach((en, cn) -> CN_TO_EN.putIfAbsent(cn, en));
    }

    private final IntelligenceRepository intelligenceRepository;
    private final VectorSearchService vectorSearchService;
    private final TrendingSearchRepository trendingSearchRepository;

    public SearchService(IntelligenceRepository intelligenceRepository,
                         VectorSearchService vectorSearchService,
                         TrendingSearchRepository trendingSearchRepository) {
        this.intelligenceRepository = intelligenceRepository;
        this.vectorSearchService = vectorSearchService;
        this.trendingSearchRepository = trendingSearchRepository;
    }

    /**
     * 搜索情报：语义搜索 + 多策略关键词匹配 + 质量加权，合并去重。
     *
     * 改进点：
     * 1. 关键词分词搜索 — "AI芯片出口" 拆分为多个子词分别匹配
     * 2. 中英文双向匹配 — 搜"芯片"也能命中标签"chip"
     * 3. 关键词匹配按命中字段数加权 — 标题+标签都命中的分数更高
     * 4. relevance 排序融合质量信号 — 置信度、来源数、时效性微调排名
     */
    public SearchResult search(String keyword, int page, int size, String sortBy) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        String trimmed = keyword.trim();
        recordTrending(trimmed);

        Map<Long, Double> intelScores = new LinkedHashMap<>();

        // 1. 语义搜索 — 情报向量余弦相似度
        try {
            List<VectorSearchService.ScoredId> scored =
                    vectorSearchService.searchIntelligences(trimmed, 50);
            for (VectorSearchService.ScoredId s : scored) {
                if (s.score() >= MIN_SIMILARITY) {
                    intelScores.put(s.id(), s.score());
                }
            }
        } catch (Exception e) {
            log.warn("语义搜索失败，降级为纯关键词搜索: {}", e.getMessage());
        }

        // 2. 多策略关键词匹配
        Set<String> searchTerms = buildSearchTerms(trimmed);
        for (String term : searchTerms) {
            try {
                List<Intelligence> hits = intelligenceRepository.searchByKeyword(term);
                for (Intelligence intel : hits) {
                    double kwScore = calcKeywordScore(intel, trimmed, searchTerms);
                    // 取关键词分数和已有语义分数中的较高者
                    intelScores.merge(intel.getId(), kwScore, Math::max);
                }
            } catch (Exception e) {
                log.debug("关键词搜索失败 [{}]: {}", term, e.getMessage());
            }
        }

        if (intelScores.isEmpty()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        // 3. 批量查询
        List<Intelligence> allResults = new ArrayList<>(
                intelligenceRepository.findAllById(intelScores.keySet()));

        // 4. relevance 排序时融合质量信号
        if ("relevance".equals(sortBy) || sortBy == null) {
            for (Intelligence intel : allResults) {
                double base = intelScores.getOrDefault(intel.getId(), 0.0);
                double boosted = applyQualityBoost(intel, base);
                intelScores.put(intel.getId(), boosted);
            }
        }

        // 5. 排序
        sortResults(allResults, sortBy, intelScores);

        // 6. 分页
        int total = allResults.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Intelligence> pageContent = allResults.subList(fromIndex, toIndex);

        log.info("搜索 [{}]: 语义命中={}, 总结果={}, 搜索词扩展={}",
                trimmed, intelScores.size(), total, searchTerms);

        return new SearchResult(pageContent, total, totalPages, page);
    }

    /**
     * 构建扩展搜索词集合：原始词 + 分词 + 中英文翻译
     */
    private Set<String> buildSearchTerms(String keyword) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(keyword);

        String lower = keyword.toLowerCase();

        // 中英文双向翻译
        String translated = EN_TO_CN.get(lower);
        if (translated != null) terms.add(translated);
        translated = CN_TO_EN.get(keyword);
        if (translated != null) terms.add(translated);

        // 对较长的关键词做简单分词（按常见分隔符 + 中英文边界）
        if (keyword.length() >= 4) {
            // 按空格、逗号、顿号分割
            for (String part : keyword.split("[\\s,，、]+")) {
                String p = part.trim();
                if (p.length() >= 2) {
                    terms.add(p);
                    // 子词也做翻译
                    String subTrans = EN_TO_CN.get(p.toLowerCase());
                    if (subTrans != null) terms.add(subTrans);
                    subTrans = CN_TO_EN.get(p);
                    if (subTrans != null) terms.add(subTrans);
                }
            }
            // 中英文边界分割：如 "AI芯片" → "AI", "芯片"
            List<String> segments = splitCnEn(keyword);
            for (String seg : segments) {
                if (seg.length() >= 2) {
                    terms.add(seg);
                    String segTrans = EN_TO_CN.get(seg.toLowerCase());
                    if (segTrans != null) terms.add(segTrans);
                    segTrans = CN_TO_EN.get(seg);
                    if (segTrans != null) terms.add(segTrans);
                }
            }
        }

        return terms;
    }

    /** 在中文和英文字符边界处分割，如 "AI芯片出口" → ["AI", "芯片出口"] */
    private List<String> splitCnEn(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean lastChinese = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isChinese = Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
            if (buf.length() > 0 && isChinese != lastChinese) {
                parts.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(c);
            lastChinese = isChinese;
        }
        if (buf.length() > 0) parts.add(buf.toString());
        return parts;
    }

    /**
     * 计算关键词匹配分数：按命中字段数和匹配深度加权
     * 基础分 0.35，每多命中一个字段 +0.08，每多命中一个搜索词 +0.05
     */
    private double calcKeywordScore(Intelligence intel, String original, Set<String> terms) {
        double score = 0.35;
        String titleLower = intel.getTitle() != null ? intel.getTitle().toLowerCase() : "";
        String summaryLower = intel.getSummary() != null ? intel.getSummary().toLowerCase() : "";
        String tagsLower = intel.getTags() != null ? intel.getTags().toLowerCase() : "";
        String stocksLower = intel.getRelatedStocks() != null ? intel.getRelatedStocks().toLowerCase() : "";

        int fieldHits = 0;
        int termHits = 0;

        for (String term : terms) {
            String t = term.toLowerCase();
            boolean hit = false;
            if (titleLower.contains(t)) { fieldHits++; hit = true; }
            if (summaryLower.contains(t)) { fieldHits++; hit = true; }
            if (tagsLower.contains(t)) { fieldHits++; hit = true; }
            if (stocksLower.contains(t)) { fieldHits++; hit = true; }
            if (hit) termHits++;
        }

        // 标题精确包含原始关键词额外加分
        if (titleLower.contains(original.toLowerCase())) {
            score += 0.12;
        }

        score += fieldHits * 0.08;
        score += termHits * 0.05;

        // 上限 0.85（不超过高质量语义匹配）
        return Math.min(score, 0.85);
    }

    /**
     * 对 relevance 分数施加质量微调：
     * - 高置信度 +0.03
     * - 多来源交叉验证 +0.02
     * - 72h 内的新鲜情报 +0.02
     * 总加成不超过 0.07，不会颠覆语义相关性排序
     */
    private double applyQualityBoost(Intelligence intel, double baseScore) {
        double boost = 0;
        if (intel.getCredibilityScore() != null && intel.getCredibilityScore() >= 0.7) {
            boost += 0.03;
        }
        if (intel.getSourceCount() != null && intel.getSourceCount() >= 2) {
            boost += 0.02;
        }
        if (intel.getLatestArticleTime() != null) {
            long hoursAgo = Duration.between(intel.getLatestArticleTime(), LocalDateTime.now()).toHours();
            if (hoursAgo <= 72) boost += 0.02;
        }
        return baseScore + boost;
    }

    private void sortResults(List<Intelligence> results, String sortBy,
                             Map<Long, Double> relevanceScores) {
        if ("credibility".equals(sortBy)) {
            results.sort((a, b) -> {
                double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
                double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
                int cmp = Double.compare(sb, sa);
                if (cmp != 0) return cmp;
                var ta = a.getLatestArticleTime();
                var tb = b.getLatestArticleTime();
                if (ta != null && tb != null) return tb.compareTo(ta);
                return 0;
            });
        } else if ("time".equals(sortBy)) {
            results.sort((a, b) -> {
                var ta = a.getLatestArticleTime();
                var tb = b.getLatestArticleTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });
        } else {
            // 默认 relevance：按融合分数降序
            results.sort((a, b) -> {
                double sa = relevanceScores.getOrDefault(a.getId(), 0.0);
                double sb = relevanceScores.getOrDefault(b.getId(), 0.0);
                int cmp = Double.compare(sb, sa);
                if (cmp != 0) return cmp;
                var ta = a.getLatestArticleTime();
                var tb = b.getLatestArticleTime();
                if (ta != null && tb != null) return tb.compareTo(ta);
                return 0;
            });
        }
    }

    /**
     * 获取热门搜索 Top N
     */
    public List<TrendingItem> getTrending(int limit) {
        LocalDate today = LocalDate.now();
        List<TrendingSearch> trending = trendingSearchRepository
                .findByDateOrderBySearchCountDesc(today);

        return trending.stream()
                .limit(limit)
                .map(t -> new TrendingItem(t.getKeyword(), t.getSearchCount()))
                .toList();
    }

    @Transactional
    void recordTrending(String keyword) {
        try {
            LocalDate today = LocalDate.now();
            Optional<TrendingSearch> existing = trendingSearchRepository
                    .findByKeywordAndDate(keyword, today);
            if (existing.isPresent()) {
                TrendingSearch ts = existing.get();
                ts.setSearchCount(ts.getSearchCount() + 1);
                trendingSearchRepository.save(ts);
            } else {
                TrendingSearch ts = new TrendingSearch();
                ts.setKeyword(keyword);
                ts.setDate(today);
                ts.setSearchCount(1);
                trendingSearchRepository.save(ts);
            }
        } catch (Exception e) {
            log.warn("记录热门搜索失败: {}", e.getMessage());
        }
    }

    public record SearchResult(
            List<Intelligence> content,
            int totalElements,
            int totalPages,
            int currentPage
    ) {}

    public record TrendingItem(String keyword, int searchCount) {}
}
