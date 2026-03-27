package com.example.demo.service;

import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class IntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceService.class);

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final NewsArticleRepository newsArticleRepository;

    public IntelligenceService(IntelligenceRepository intelligenceRepository,
                               IntelligenceArticleRepository intelligenceArticleRepository,
                               NewsArticleRepository newsArticleRepository) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.newsArticleRepository = newsArticleRepository;
    }

    /** 分页查询情报列表 */
    public Page<Intelligence> listIntelligences(int hours, int page, int size) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return intelligenceRepository.findByCreatedAtAfterOrderByLatestArticleTimeDesc(
                since, PageRequest.of(page, size));
    }

    /** 获取情报详情，包含关联的所有原始新闻 */
    public IntelligenceDetail getDetail(Long id) {
        Intelligence intel = intelligenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intelligence not found: " + id));

        // 获取关联的新闻列表
        List<IntelligenceArticle> myLinks = intelligenceArticleRepository
                .findByIntelligenceIdOrderByIsPrimaryDesc(id);

        List<Long> articleIds = myLinks.stream()
                .map(IntelligenceArticle::getArticleId).toList();
        List<NewsArticle> articles = newsArticleRepository.findAllById(articleIds);

        // 按置信度降序排列
        articles.sort((a, b) -> {
            double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
            double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
            return Double.compare(sb, sa);
        });

        // 如果情报没有 content，从关联新闻生成
        if (intel.getContent() == null || intel.getContent().isBlank()) {
            String generated = generateContentFromArticles(intel, articles);
            intel.setContent(generated);
            intelligenceRepository.save(intel);
        }

        // 构建来源列表（所有关联文章）
        List<SourceInfo> sources = articles.stream().map(a -> new SourceInfo(
                a.getId(),
                a.getSourceName(),
                mapCredibilityTag(a.getCredibilityLevel()),
                a.getTitle(),
                a.getSourceUrl()
        )).toList();

        // 估算阅读时间
        int contentLength = intel.getContent() != null ? intel.getContent().length() : 0;
        int readMinutes = Math.max(1, contentLength / 500);
        String readTime = (readMinutes <= 1) ? "1 min" : readMinutes + " min";

        return new IntelligenceDetail(intel, sources, readTime, articles);
    }

    /** Generate content from linked articles (fallback when LLM unavailable) */
    private String generateContentFromArticles(Intelligence intel, List<NewsArticle> articles) {
        if (articles.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // Summary section
        if (intel.getSummary() != null && !intel.getSummary().isBlank()) {
            sb.append(intel.getSummary()).append("\n\n");
        }

        // Compile from all articles
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            if (articles.size() > 1) {
                sb.append("[").append(a.getSourceName() != null ? a.getSourceName() : "Source").append("] ");
            }
            // Use content, fallback to summary
            String body = a.getContent();
            if (body == null || body.isBlank()) body = a.getSummary();
            if (body == null || body.isBlank()) body = a.getTitle();

            sb.append(body);
            if (i < articles.size() - 1) sb.append("\n\n---\n\n");
        }

        // Related stocks
        if (intel.getRelatedStocks() != null && !intel.getRelatedStocks().isBlank()) {
            sb.append("\n\nRelated: ").append(intel.getRelatedStocks());
        }

        return sb.toString();
    }

    private String mapCredibilityTag(String level) {
        if (level == null) return "未知";
        if ("authoritative".equals(level)) return "权威";
        if ("normal".equals(level)) return "可信";
        if ("questionable".equals(level)) return "存疑";
        return "未知";
    }

    public record SourceInfo(
            Long articleId,
            String sourceName,
            String credibilityTag,
            String title,
            String sourceUrl
    ) {}

    public record IntelligenceDetail(
            Intelligence intelligence,
            List<SourceInfo> sources,
            String readTime,
            List<NewsArticle> articles
    ) {}
}
