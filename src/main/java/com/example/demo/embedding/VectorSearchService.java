package com.example.demo.embedding;

import com.example.demo.model.Intelligence;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.NewsArticleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);
    private final ConcurrentHashMap<Long, float[]> vectorCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, float[]> intelVectorCache = new ConcurrentHashMap<>();
    private final EmbeddingClient embeddingClient;
    private final NewsArticleRepository newsArticleRepository;
    private final IntelligenceRepository intelligenceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VectorSearchService(EmbeddingClient embeddingClient,
                               NewsArticleRepository newsArticleRepository,
                               IntelligenceRepository intelligenceRepository) {
        this.embeddingClient = embeddingClient;
        this.newsArticleRepository = newsArticleRepository;
        this.intelligenceRepository = intelligenceRepository;
    }

    @PostConstruct
    public void loadFromDatabase() {
        // 加载新闻向量
        List<NewsArticle> articles = newsArticleRepository.findAll();
        int loaded = 0;
        for (NewsArticle article : articles) {
            if (article.getEmbeddingJson() != null && !article.getEmbeddingJson().isBlank()) {
                try {
                    List<Float> list = objectMapper.readValue(article.getEmbeddingJson(), new TypeReference<>() {});
                    float[] vec = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) vec[i] = list.get(i);
                    vectorCache.put(article.getId(), vec);
                    loaded++;
                } catch (Exception ignored) {}
            }
        }
        log.info("从数据库恢复了 {} 条新闻向量缓存", loaded);

        // 加载情报向量
        List<Intelligence> intels = intelligenceRepository.findAll();
        int intelLoaded = 0;
        for (Intelligence intel : intels) {
            if (intel.getEmbeddingJson() != null && !intel.getEmbeddingJson().isBlank()) {
                try {
                    List<Float> list = objectMapper.readValue(intel.getEmbeddingJson(), new TypeReference<>() {});
                    float[] vec = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) vec[i] = list.get(i);
                    intelVectorCache.put(intel.getId(), vec);
                    intelLoaded++;
                } catch (Exception ignored) {}
            }
        }
        log.info("从数据库恢复了 {} 条情报向量缓存", intelLoaded);
    }

    public void addVector(Long articleId, float[] vector) {
        vectorCache.put(articleId, vector);
    }

    /** 添加情报向量到缓存 */
    public void addIntelVector(Long intelId, float[] vector) {
        intelVectorCache.put(intelId, vector);
    }

    /** 语义搜索：返回最相似的topK篇文章ID */
    public List<Long> semanticSearch(String queryText, int topK) {
        return semanticSearchWithScores(queryText, topK).stream()
                .map(ScoredId::id)
                .collect(Collectors.toList());
    }

    /** 语义搜索：返回最相似的topK篇文章ID + 相似度分数 */
    public List<ScoredId> semanticSearchWithScores(String queryText, int topK) {
        float[] queryVec = embeddingClient.embed(queryText);
        if (queryVec.length == 0) return List.of();

        return vectorCache.entrySet().stream()
                .map(e -> new ScoredId(e.getKey(), cosineSimilarity(queryVec, e.getValue())))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /** 情报级别语义搜索：直接搜情报向量，返回 intelligenceId + 相似度分数 */
    public List<ScoredId> searchIntelligences(String queryText, int topK) {
        float[] queryVec = embeddingClient.embed(queryText);
        if (queryVec.length == 0) return List.of();

        return intelVectorCache.entrySet().stream()
                .map(e -> new ScoredId(e.getKey(), cosineSimilarity(queryVec, e.getValue())))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    public record ScoredId(Long id, double score) {}

    /** 语义去重：返回相似文章ID，null表示不重复 */
    public Long findSemanticDuplicate(String text, double threshold) {
        float[] vec = embeddingClient.embed(text);
        if (vec.length == 0) return null;

        for (var entry : vectorCache.entrySet()) {
            if (cosineSimilarity(vec, entry.getValue()) > threshold) {
                return entry.getKey();
            }
        }
        return null;
    }

    public int cacheSize() {
        return vectorCache.size() + intelVectorCache.size();
    }

    public int articleCacheSize() {
        return vectorCache.size();
    }

    public int intelCacheSize() {
        return intelVectorCache.size();
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
