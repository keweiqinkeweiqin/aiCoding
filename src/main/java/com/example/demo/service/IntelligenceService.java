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
                .orElseThrow(() -> new NoSuchElementException("情报不存在: " + id));

        // 获取关联的新闻列表
        List<IntelligenceArticle> links = intelligenceArticleRepository
                .findByIntelligenceIdOrderByIsPrimaryDesc(id);
        List<IntelligenceArticle> myLinks = links;

        List<Long> articleIds = myLinks.stream()
                .map(IntelligenceArticle::getArticleId).toList();
        List<NewsArticle> articles = newsArticleRepository.findAllById(articleIds);

        // 按置信度降序排列
        articles.sort((a, b) -> {
            double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
            double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
            return Double.compare(sb, sa);
        });

        // 构建来源列表
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
        String readTime = "约" + readMinutes + "分钟";

        return new IntelligenceDetail(intel, sources, readTime);
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
            String readTime
    ) {}
}
