package com.example.demo.service;

import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.embedding.VectorSearchService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Event clustering: group news articles about the same event into Intelligence.
 * Strategy: Embedding cosine similarity (primary) + SimHash (fallback)
 */
@Service
public class EventClusterService {

    private static final Logger log = LoggerFactory.getLogger(EventClusterService.class);
    private static final double EMBEDDING_SIMILARITY_THRESHOLD = 0.85;
    private static final int SIMHASH_HAMMING_THRESHOLD = 20;

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final DeduplicationService deduplicationService;
    private final EmbeddingClient embeddingClient;

    // Cache: intelligenceId -> title embedding
    private final ConcurrentHashMap<Long, float[]> intelEmbeddingCache = new ConcurrentHashMap<>();

    public EventClusterService(IntelligenceRepository intelligenceRepository,
                               IntelligenceArticleRepository intelligenceArticleRepository,
                               NewsArticleRepository newsArticleRepository,
                               DeduplicationService deduplicationService,
                               EmbeddingClient embeddingClient) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.deduplicationService = deduplicationService;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 对最近采集的新闻执行聚类，生成/更新情报
     */
    public ClusterResult clusterRecent() {
        // 重新从数据库读取（确保拿到置信度评估后的最新数据）
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

            // Try embedding similarity first (most accurate)
            Intelligence matched = findByEmbedding(article, recentIntels);

            // Fallback to SimHash
            if (matched == null) {
                matched = findBySimHash(titleHash, recentIntels);
            }

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
                // Cache embedding for future matching
                float[] vec = getArticleEmbedding(article);
                if (vec != null && vec.length > 0) {
                    intelEmbeddingCache.put(intel.getId(), vec);
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        List<Float> fl = new ArrayList<>();
                        for (float v : vec) fl.add(v);
                        intel.setEmbeddingJson(om.writeValueAsString(fl));
                        intelligenceRepository.save(intel);
                    } catch (Exception ignored) {}
                }
                intelligenceArticleRepository.save(
                        new IntelligenceArticle(intel.getId(), article.getId(), true));
                recentIntels.add(intel); // 加入列表供后续匹配
                created++;
            }
        }

        log.info("事件聚类完成: created={}, merged={}, unclustered={}", created, merged, unclustered.size());
        return new ClusterResult(created, merged);
    }

    /** Primary: match by embedding cosine similarity */
    private Intelligence findByEmbedding(NewsArticle article, List<Intelligence> candidates) {
        try {
            float[] articleVec = getArticleEmbedding(article);
            if (articleVec == null || articleVec.length == 0) return null;

            double bestSim = 0;
            Intelligence bestMatch = null;

            for (Intelligence intel : candidates) {
                float[] intelVec = getIntelEmbedding(intel);
                if (intelVec == null || intelVec.length == 0) continue;

                double sim = VectorSearchService.cosineSimilarity(articleVec, intelVec);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestMatch = intel;
                }
            }

            if (bestSim >= EMBEDDING_SIMILARITY_THRESHOLD) {
                log.debug("Embedding match: {} -> {} (sim={})", article.getTitle(), bestMatch.getTitle(), bestSim);
                return bestMatch;
            }
        } catch (Exception e) {
            log.warn("Embedding match failed, falling back to SimHash: {}", e.getMessage());
        }
        return null;
    }

    /** Fallback: match by SimHash hamming distance */
    private Intelligence findBySimHash(long titleHash, List<Intelligence> candidates) {
        for (Intelligence intel : candidates) {
            if (intel.getTitleSimhash() != null
                    && deduplicationService.hammingDistance(titleHash, intel.getTitleSimhash()) <= SIMHASH_HAMMING_THRESHOLD) {
                return intel;
            }
        }
        return null;
    }

    private float[] getArticleEmbedding(NewsArticle article) {
        // Use existing embedding if available
        if (article.getEmbeddingJson() != null && !article.getEmbeddingJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Float> list = om.readValue(article.getEmbeddingJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Float>>() {});
                float[] vec = new float[list.size()];
                for (int i = 0; i < list.size(); i++) vec[i] = list.get(i);
                return vec;
            } catch (Exception ignored) {}
        }
        // Generate on the fly
        return embeddingClient.embed(article.getTitle());
    }

    private float[] getIntelEmbedding(Intelligence intel) {
        // Check cache first
        float[] cached = intelEmbeddingCache.get(intel.getId());
        if (cached != null) return cached;

        // Try stored embedding
        if (intel.getEmbeddingJson() != null && !intel.getEmbeddingJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Float> list = om.readValue(intel.getEmbeddingJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Float>>() {});
                float[] vec = new float[list.size()];
                for (int i = 0; i < list.size(); i++) vec[i] = list.get(i);
                intelEmbeddingCache.put(intel.getId(), vec);
                return vec;
            } catch (Exception ignored) {}
        }

        // Generate and cache
        float[] vec = embeddingClient.embed(intel.getTitle());
        if (vec != null && vec.length > 0) {
            intelEmbeddingCache.put(intel.getId(), vec);
        }
        return vec;
    }

    private Intelligence createFromArticle(NewsArticle article, long titleHash) {
        Intelligence intel = new Intelligence();
        intel.setTitle(article.getTitle());
        intel.setSummary(article.getSummary());
        intel.setPrimarySource(article.getSourceName());

        // 如果文章没有置信度分数，基于来源给默认分
        Double score = article.getCredibilityScore();
        String level = article.getCredibilityLevel();
        if (score == null || score == 0) {
            if ("authoritative".equals(level)) { score = 0.75; }
            else if ("normal".equals(level)) { score = 0.55; }
            else { score = 0.4; }
        }
        intel.setCredibilityScore(score);
        intel.setCredibilityLevel(level != null ? level : "normal");

        intel.setSourceCount(1);
        intel.setSentiment(article.getSentiment());
        intel.setSentimentScore(article.getSentimentScore());
        intel.setRelatedStocks(article.getRelatedStocks());
        intel.setTags(article.getTags());
        intel.setTitleSimhash(titleHash);
        intel.setLatestArticleTime(article.getCollectedAt());
        intel.setPriority(computePriority(score, 1));
        return intel;
    }

    /** 更新情报的聚合元数据 */
    private void updateIntelligenceMetadata(Intelligence intel) {
        List<IntelligenceArticle> links = intelligenceArticleRepository
                .findByIntelligenceIdOrderByIsPrimaryDesc(intel.getId());
        List<IntelligenceArticle> myLinks = links;

        List<Long> articleIds = myLinks.stream().map(IntelligenceArticle::getArticleId).toList();
        List<NewsArticle> articles = newsArticleRepository.findAllById(articleIds);

        if (articles.isEmpty()) return;

        // 来源数 = 不同来源名的数量
        long sourceCount = articles.stream()
                .map(NewsArticle::getSourceName).distinct().count();
        intel.setSourceCount((int) sourceCount);

        // 置信度取最高，null 时基于来源给默认分
        articles.stream()
                .map(a -> {
                    Double s = a.getCredibilityScore();
                    if (s == null || s == 0) {
                        if ("authoritative".equals(a.getCredibilityLevel())) s = 0.75;
                        else if ("normal".equals(a.getCredibilityLevel())) s = 0.55;
                        else s = 0.4;
                    }
                    return Map.entry(a, s);
                })
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .ifPresent(best -> {
                    intel.setCredibilityScore(best.getValue());
                    intel.setCredibilityLevel(best.getKey().getCredibilityLevel() != null
                            ? best.getKey().getCredibilityLevel() : "normal");
                    intel.setPrimarySource(best.getKey().getSourceName());
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
