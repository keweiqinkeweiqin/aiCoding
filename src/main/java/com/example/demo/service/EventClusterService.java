package com.example.demo.service;

import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件聚类服务：将报道同一事件的多条新闻聚合为一条情报(Intelligence)
 * 核心算法：SimHash 标题指纹，汉明距离 ≤ 10 判定为同一事件
 */
@Service
public class EventClusterService {

    private static final Logger log = LoggerFactory.getLogger(EventClusterService.class);
    private static final int CLUSTER_HAMMING_THRESHOLD = 10;

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final DeduplicationService deduplicationService;

    public EventClusterService(IntelligenceRepository intelligenceRepository,
                               IntelligenceArticleRepository intelligenceArticleRepository,
                               NewsArticleRepository newsArticleRepository,
                               DeduplicationService deduplicationService) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.deduplicationService = deduplicationService;
    }

    /**
     * 对最近采集的新闻执行聚类，生成/更新情报
     */
    public ClusterResult clusterRecent() {
        // 找出所有未归类的新闻
        List<NewsArticle> allRecent = newsArticleRepository
                .findByCollectedAtAfterOrderByCollectedAtDesc(LocalDateTime.now().minusHours(48));
        List<NewsArticle> unclustered = allRecent.stream()
                .filter(a -> !intelligenceArticleRepository.existsByArticleId(a.getId()))
                .toList();

        if (unclustered.isEmpty()) {
            return new ClusterResult(0, 0);
        }

        // 加载最近48h的情报及其 SimHash
        List<Intelligence> recentIntels = intelligenceRepository
                .findByCreatedAtAfterOrderByLatestArticleTimeDesc(LocalDateTime.now().minusHours(48));

        int created = 0, merged = 0;

        for (NewsArticle article : unclustered) {
            long titleHash = deduplicationService.simHash(article.getTitle());
            Intelligence matched = findMatchingIntelligence(titleHash, recentIntels);

            if (matched != null) {
                // 关联到已有情报
                intelligenceArticleRepository.save(
                        new IntelligenceArticle(matched.getId(), article.getId(), false));
                updateIntelligenceMetadata(matched);
                merged++;
            } else {
                // 创建新情报
                Intelligence intel = createFromArticle(article, titleHash);
                intelligenceRepository.save(intel);
                intelligenceArticleRepository.save(
                        new IntelligenceArticle(intel.getId(), article.getId(), true));
                recentIntels.add(intel); // 加入列表供后续匹配
                created++;
            }
        }

        log.info("事件聚类完成: created={}, merged={}, unclustered={}", created, merged, unclustered.size());
        return new ClusterResult(created, merged);
    }

    private Intelligence findMatchingIntelligence(long titleHash, List<Intelligence> candidates) {
        for (Intelligence intel : candidates) {
            if (intel.getTitleSimhash() != null
                    && deduplicationService.hammingDistance(titleHash, intel.getTitleSimhash()) <= CLUSTER_HAMMING_THRESHOLD) {
                return intel;
            }
        }
        return null;
    }

    private Intelligence createFromArticle(NewsArticle article, long titleHash) {
        Intelligence intel = new Intelligence();
        intel.setTitle(article.getTitle());
        intel.setSummary(article.getSummary());
        intel.setPrimarySource(article.getSourceName());
        intel.setCredibilityLevel(article.getCredibilityLevel());
        intel.setCredibilityScore(article.getCredibilityScore());
        intel.setSourceCount(1);
        intel.setSentiment(article.getSentiment());
        intel.setSentimentScore(article.getSentimentScore());
        intel.setRelatedStocks(article.getRelatedStocks());
        intel.setTags(article.getTags());
        intel.setTitleSimhash(titleHash);
        intel.setLatestArticleTime(article.getCollectedAt());
        intel.setPriority(computePriority(article.getCredibilityScore(), 1));
        return intel;
    }

    /** 更新情报的聚合元数据 */
    private void updateIntelligenceMetadata(Intelligence intel) {
        List<IntelligenceArticle> links = intelligenceArticleRepository
                .findByIntelligenceIdOrderByIsPrimaryDesc();
        List<IntelligenceArticle> myLinks = links.stream()
                .filter(l -> l.getIntelligenceId().equals(intel.getId()))
                .toList();

        List<Long> articleIds = myLinks.stream().map(IntelligenceArticle::getArticleId).toList();
        List<NewsArticle> articles = newsArticleRepository.findAllById(articleIds);

        if (articles.isEmpty()) return;

        // 来源数 = 不同来源名的数量
        long sourceCount = articles.stream()
                .map(NewsArticle::getSourceName).distinct().count();
        intel.setSourceCount((int) sourceCount);

        // 置信度取最高
        articles.stream()
                .filter(a -> a.getCredibilityScore() != null)
                .max(Comparator.comparingDouble(NewsArticle::getCredibilityScore))
                .ifPresent(best -> {
                    intel.setCredibilityScore(best.getCredibilityScore());
                    intel.setCredibilityLevel(best.getCredibilityLevel());
                    intel.setPrimarySource(best.getSourceName());
                });

        // 情感多数投票
        Map<String, Long> sentimentCounts = articles.stream()
                .filter(a -> a.getSentiment() != null)
                .collect(Collectors.groupingBy(NewsArticle::getSentiment, Collectors.counting()));
        sentimentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> intel.setSentiment(e.getKey()));

        // 合并标签和关联股票
        intel.setTags(mergeField(articles, NewsArticle::getTags));
        intel.setRelatedStocks(mergeField(articles, NewsArticle::getRelatedStocks));

        // 最新时间
        articles.stream()
                .map(NewsArticle::getCollectedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(intel::setLatestArticleTime);

        intel.setPriority(computePriority(intel.getCredibilityScore(), intel.getSourceCount()));
        intelligenceRepository.save(intel);
    }

    private String mergeField(List<NewsArticle> articles,
                              java.util.function.Function<NewsArticle, String> getter) {
        return articles.stream()
                .map(getter)
                .filter(s -> s != null && !s.isBlank())
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    static String computePriority(Double credibilityScore, int sourceCount) {
        if (credibilityScore != null && credibilityScore >= 0.8 && sourceCount >= 2) return "high";
        if (credibilityScore != null && credibilityScore >= 0.5) return "medium";
        return "low";
    }

    public record ClusterResult(int created, int merged) {}
}
