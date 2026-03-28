package com.example.demo.service;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.TrendingSearch;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.TrendingSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final VectorSearchService vectorSearchService;
    private final TrendingSearchRepository trendingSearchRepository;

    public SearchService(IntelligenceRepository intelligenceRepository,
                         IntelligenceArticleRepository intelligenceArticleRepository,
                         VectorSearchService vectorSearchService,
                         TrendingSearchRepository trendingSearchRepository) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.vectorSearchService = vectorSearchService;
        this.trendingSearchRepository = trendingSearchRepository;
    }

    /**
     * 搜索情报：语义搜索 + 关键词模糊匹配，合并去重，支持排序和分页。
     * relevance 排序基于语义相似度分数（情报取其关联文章的最高相似度）。
     */
    public SearchResult search(String keyword, int page, int size, String sortBy) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        recordTrending(keyword.trim());

        // 1. 语义搜索 — 带分数，映射到情报
        // key=intelligenceId, value=该情报下所有关联文章的最高相似度
        Map<Long, Double> intelRelevanceScores = new LinkedHashMap<>();

        try {
            List<VectorSearchService.ScoredId> scored =
                    vectorSearchService.semanticSearchWithScores(keyword, 50);
            for (VectorSearchService.ScoredId s : scored) {
                List<IntelligenceArticle> links =
                        intelligenceArticleRepository.findByArticleId(s.id());
                for (IntelligenceArticle link : links) {
                    intelRelevanceScores.merge(link.getIntelligenceId(), s.score(), Math::max);
                }
            }
        } catch (Exception e) {
            log.warn("语义搜索失败，降级为纯关键词搜索: {}", e.getMessage());
        }

        // 2. 关键词模糊匹配（给一个基础相关性分数 0.3）
        List<Intelligence> keywordResults = intelligenceRepository.searchByKeyword(keyword.trim());
        for (Intelligence intel : keywordResults) {
            // 只在语义搜索没命中时补充，不覆盖更高的语义分数
            intelRelevanceScores.putIfAbsent(intel.getId(), 0.3);
        }

        if (intelRelevanceScores.isEmpty()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        // 3. 批量查询所有匹配的情报
        List<Intelligence> allResults = intelligenceRepository
                .findAllById(intelRelevanceScores.keySet());

        // 4. 排序
        sortResults(allResults, sortBy, intelRelevanceScores);

        // 5. 分页
        int total = allResults.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Intelligence> pageContent = allResults.subList(fromIndex, toIndex);

        return new SearchResult(pageContent, total, totalPages, page);
    }

    private void sortResults(List<Intelligence> results, String sortBy,
                             Map<Long, Double> relevanceScores) {
        if ("credibility".equals(sortBy)) {
            results.sort((a, b) -> {
                double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
                double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
                return Double.compare(sb, sa);
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
            // 默认 relevance：按语义相似度降序
            results.sort((a, b) -> {
                double sa = relevanceScores.getOrDefault(a.getId(), 0.0);
                double sb = relevanceScores.getOrDefault(b.getId(), 0.0);
                int cmp = Double.compare(sb, sa);
                if (cmp != 0) return cmp;
                // 同分按时间倒序
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
