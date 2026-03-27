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
     * 搜索情报：语义搜索 + 关键词模糊匹配，合并去重，支持排序和分页
     */
    public SearchResult search(String keyword, int page, int size, String sortBy) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        // 记录热门搜索计数
        recordTrending(keyword.trim());

        // 1. 语义搜索 — 找到相关的 NewsArticle IDs
        Set<Long> matchedIntelligenceIds = new LinkedHashSet<>();

        try {
            List<Long> semanticArticleIds = vectorSearchService.semanticSearch(keyword, 50);
            // 通过 IntelligenceArticle 关联表找到对应的 Intelligence IDs
            for (Long articleId : semanticArticleIds) {
                // 查找该文章关联的情报
                List<IntelligenceArticle> links = intelligenceArticleRepository
                        .findByArticleId(articleId);
                for (IntelligenceArticle link : links) {
                    matchedIntelligenceIds.add(link.getIntelligenceId());
                }
            }
        } catch (Exception e) {
            log.warn("语义搜索失败，降级为纯关键词搜索: {}", e.getMessage());
        }

        // 2. 关键词模糊匹配
        List<Intelligence> keywordResults = intelligenceRepository.searchByKeyword(keyword.trim());
        for (Intelligence intel : keywordResults) {
            matchedIntelligenceIds.add(intel.getId());
        }

        if (matchedIntelligenceIds.isEmpty()) {
            return new SearchResult(List.of(), 0, 0, page);
        }

        // 3. 批量查询所有匹配的情报
        List<Intelligence> allResults = intelligenceRepository.findAllById(matchedIntelligenceIds);

        // 4. 排序
        sortResults(allResults, sortBy);

        // 5. 分页
        int total = allResults.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Intelligence> pageContent = allResults.subList(fromIndex, toIndex);

        return new SearchResult(pageContent, total, totalPages, page);
    }

    private void sortResults(List<Intelligence> results, String sortBy) {
        if ("credibility".equals(sortBy)) {
            results.sort((a, b) -> {
                double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
                double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
                return Double.compare(sb, sa);
            });
        } else {
            // 默认按时间倒序
            results.sort((a, b) -> {
                var ta = a.getLatestArticleTime();
                var tb = b.getLatestArticleTime();
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
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
