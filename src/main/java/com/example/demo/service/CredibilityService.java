package com.example.demo.service;

import com.example.demo.model.NewsArticle;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 多维度置信度评估服务
 * 综合来源权威性、LLM评估、时效性、交叉验证四个维度
 * 输出 0.0-1.0 的综合置信度分数 + 各维度明细
 */
@Service
public class CredibilityService {

    private static final Logger log = LoggerFactory.getLogger(CredibilityService.class);
    private final NewsArticleRepository newsArticleRepository;
    private final DeduplicationService deduplicationService;

    // 来源权威性基础分
    private static final Map<String, Double> SOURCE_SCORES = Map.of(
            "财联社", 0.95,
            "财联社电报", 0.90,
            "东方财富研报", 0.92,
            "华尔街见闻", 0.85,
            "36Kr", 0.80,
            "金十数据", 0.82,
            "NewsAPI", 0.75
    );

    // 权重
    private static final double W_SOURCE = 0.30;      // 来源权威性
    private static final double W_LLM = 0.25;         // LLM情感置信度
    private static final double W_FRESHNESS = 0.20;   // 时效性
    private static final double W_CROSS = 0.25;       // 交叉验证

    public CredibilityService(NewsArticleRepository newsArticleRepository,
                               DeduplicationService deduplicationService) {
        this.newsArticleRepository = newsArticleRepository;
        this.deduplicationService = deduplicationService;
    }

    /**
     * 综合评估一条新闻的置信度
     */
    public CredibilityResult assess(NewsArticle article) {
        double sourceScore = assessSource(article);
        double llmScore = assessLlmConfidence(article);
        double freshnessScore = assessFreshness(article);
        double crossScore = assessCrossValidation(article);

        double overall = sourceScore * W_SOURCE
                + llmScore * W_LLM
                + freshnessScore * W_FRESHNESS
                + crossScore * W_CROSS;

        String level;
        if (overall >= 0.8) level = "authoritative";
        else if (overall >= 0.5) level = "normal";
        else level = "questionable";

        return new CredibilityResult(
                Math.round(overall * 100) / 100.0,
                level,
                Math.round(sourceScore * 100) / 100.0,
                Math.round(llmScore * 100) / 100.0,
                Math.round(freshnessScore * 100) / 100.0,
                Math.round(crossScore * 100) / 100.0
        );
    }

    /** 维度1: 来源权威性 (0-1) */
    private double assessSource(NewsArticle article) {
        return SOURCE_SCORES.getOrDefault(article.getSourceName(), 0.6);
    }

    /** 维度2: LLM情感分析置信度 (0-1) — sentimentScore越极端说明LLM越确定 */
    private double assessLlmConfidence(NewsArticle article) {
        if (article.getSentimentScore() == null) return 0.5;
        // sentimentScore 0.0-1.0, 越接近0或1说明LLM越确定
        double deviation = Math.abs(article.getSentimentScore() - 0.5);
        return 0.5 + deviation; // 范围 0.5-1.0
    }

    /** 维度3: 时效性 (0-1) — 越新越高 */
    private double assessFreshness(NewsArticle article) {
        LocalDateTime collected = article.getCollectedAt();
        if (collected == null) return 0.5;
        long hoursAgo = Duration.between(collected, LocalDateTime.now()).toHours();
        if (hoursAgo <= 1) return 1.0;
        if (hoursAgo <= 6) return 0.9;
        if (hoursAgo <= 24) return 0.7;
        if (hoursAgo <= 72) return 0.5;
        return 0.3;
    }

    /** 维度4: 交叉验证 (0-1) — 同一事件被多少个不同来源报道 */
    private double assessCrossValidation(NewsArticle article) {
        if (article.getTitle() == null) return 0.3;

        // 查找最近48小时内标题SimHash相近的文章
        List<NewsArticle> recent = newsArticleRepository.findByCollectedAtAfterOrderByCollectedAtDesc(
                LocalDateTime.now().minusHours(48));

        long titleHash = deduplicationService.simHash(article.getTitle());
        long crossSourceCount = recent.stream()
                .filter(a -> !a.getId().equals(article.getId()))
                .filter(a -> !a.getSourceName().equals(article.getSourceName()))
                .filter(a -> deduplicationService.hammingDistance(
                        titleHash, deduplicationService.simHash(a.getTitle())) <= 10)
                .map(NewsArticle::getSourceName)
                .distinct()
                .count();

        if (crossSourceCount >= 3) return 1.0;
        if (crossSourceCount >= 2) return 0.85;
        if (crossSourceCount >= 1) return 0.7;
        return 0.4; // 单一来源
    }

    public record CredibilityResult(
            double overallScore,
            String level,           // authoritative / normal / questionable
            double sourceScore,     // 来源权威性
            double llmScore,        // LLM置信度
            double freshnessScore,  // 时效性
            double crossScore       // 交叉验证
    ) {}
}
