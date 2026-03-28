package com.example.demo.service;

import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceService.class);

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ChatClient chatClient;

    public IntelligenceService(IntelligenceRepository intelligenceRepository,
                               IntelligenceArticleRepository intelligenceArticleRepository,
                               NewsArticleRepository newsArticleRepository,
                               ChatClient.Builder chatClientBuilder) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.chatClient = chatClientBuilder.build();
    }

    /** 分页查询情报列表 */
    public Page<Intelligence> listIntelligences(int hours, int page, int size) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return intelligenceRepository.findByCreatedAtAfterOrderByLatestArticleTimeDesc(
                since, PageRequest.of(page, size));
    }

    /** 清空所有情报的 content 缓存 */
    public int clearAllContent() {
        List<Intelligence> all = intelligenceRepository.findAll();
        int count = 0;
        for (Intelligence intel : all) {
            if (intel.getContent() != null && !intel.getContent().isBlank()) {
                intel.setContent(null);
                intelligenceRepository.save(intel);
                count++;
            }
        }
        log.info("Cleared content for {} intelligences", count);
        return count;
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

    /** Generate content: LLM synthesis for multi-source, fallback to concatenation */
    private String generateContentFromArticles(Intelligence intel, List<NewsArticle> articles) {
        if (articles.isEmpty()) return "";

        // Try LLM synthesis for multi-source intelligences
        if (articles.size() >= 2) {
            try {
                // 限制最多取5条新闻，每条摘要最多200字，控制总 prompt 长度
                String articlesText = articles.stream().limit(5).map(a -> {
                    String body = a.getSummary() != null ? a.getSummary() : "";
                    if (body.isBlank() && a.getContent() != null) {
                        body = a.getContent().length() > 200 ? a.getContent().substring(0, 200) + "..." : a.getContent();
                    } else if (body.length() > 200) {
                        body = body.substring(0, 200) + "...";
                    }
                    return "【" + a.getSourceName() + "】" + a.getTitle() + "\n" + body;
                }).collect(Collectors.joining("\n\n"));

                String prompt = "你是投研分析师。基于以下 " + Math.min(articles.size(), 5) + " 条新闻，撰写中文投研分析。\n\n"
                        + "要求：1)事件概述2-3句 2)综合各来源差异信息，标注来源 3)市场影响分析 4)2-3个关注要点用·开头\n"
                        + "格式：段落间空行，400-600字，纯文本无Markdown\n\n"
                        + "【新闻素材】\n" + articlesText;

                String content = chatClient.prompt().user(prompt).call().content();
                if (content != null && !content.isBlank()) {
                    // 清理可能的 Markdown 标记
                    content = content.replaceAll("(?m)^#{1,4}\\s*", "")
                                     .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                                     .replaceAll("\\*([^*]+)\\*", "$1")
                                     .trim();
                    log.info("LLM content generated for intel {} ({} chars)", intel.getId(), content.length());
                    return content;
                }
            } catch (Exception e) {
                log.warn("LLM content generation failed for intel {}: {}", intel.getId(), e.getMessage());
            }
        }

        // Single source: also try LLM to polish the content
        if (articles.size() == 1) {
            try {
                NewsArticle a = articles.get(0);
                String body = a.getContent() != null ? a.getContent() : (a.getSummary() != null ? a.getSummary() : "");
                if (body.length() > 400) body = body.substring(0, 400) + "...";
                if (!body.isBlank()) {
                    String prompt = "将以下新闻改写为投研快讯。要求：第一段概述核心事件2-3句，第二段分析市场影响。200-300字，纯文本无Markdown。\n\n"
                            + "【" + a.getSourceName() + "】" + a.getTitle() + "\n" + body;

                    String content = chatClient.prompt().user(prompt).call().content();
                    if (content != null && !content.isBlank()) {
                        content = content.replaceAll("(?m)^#{1,4}\\s*", "")
                                         .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                                         .replaceAll("\\*([^*]+)\\*", "$1")
                                         .trim();
                        log.info("LLM content polished for single-source intel {}", intel.getId());
                        return content;
                    }
                }
            } catch (Exception e) {
                log.warn("LLM polish failed for intel {}: {}", intel.getId(), e.getMessage());
            }
        }

        // Fallback: concatenate articles
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            if (articles.size() > 1) {
                sb.append("【").append(a.getSourceName() != null ? a.getSourceName() : "来源").append("】");
            }
            String body = a.getContent();
            if (body == null || body.isBlank()) body = a.getSummary();
            if (body == null || body.isBlank()) body = a.getTitle();
            sb.append(body);
            if (i < articles.size() - 1) sb.append("\n\n---\n\n");
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
