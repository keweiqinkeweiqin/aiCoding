package com.example.demo.service;

import com.example.demo.collector.RssCollector;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class NewsCollectorService {

    private static final Logger log = LoggerFactory.getLogger(NewsCollectorService.class);

    private final RssCollector rssCollector;
    private final DeduplicationService deduplicationService;
    private final NewsArticleRepository newsArticleRepository;

    // RSS源配置
    private static final List<SourceConfig> RSS_SOURCES = List.of(
            new SourceConfig("https://rsshub.chn.moe/36kr/information/web_news", "36Kr", "rss", "normal"),
            new SourceConfig("https://rsshub.chn.moe/cls/depth/1000", "财联社", "rss", "authoritative"),
            new SourceConfig("https://rsshub.chn.moe/wallstreetcn/news/global", "华尔街见闻", "rss", "normal")
    );

    public NewsCollectorService(RssCollector rssCollector,
                                 DeduplicationService deduplicationService,
                                 NewsArticleRepository newsArticleRepository) {
        this.rssCollector = rssCollector;
        this.deduplicationService = deduplicationService;
        this.newsArticleRepository = newsArticleRepository;
    }

    /**
     * 从所有RSS源采集新闻，去重后入库
     */
    public CollectResult collectAll() {
        int totalCollected = 0;
        int totalDeduplicated = 0;
        int totalStored = 0;

        for (SourceConfig source : RSS_SOURCES) {
            try {
                List<RssCollector.RssItem> items = rssCollector.collect(source.url, source.name);
                totalCollected += items.size();

                for (RssCollector.RssItem item : items) {
                    // 去重检查
                    String plainContent = stripHtml(item.content());
                    String dupReason = deduplicationService.checkDuplicate(
                            item.title(), item.link(), plainContent);

                    if (dupReason != null) {
                        totalDeduplicated++;
                        continue;
                    }

                    // 构建实体
                    NewsArticle article = new NewsArticle();
                    article.setTitle(item.title());
                    article.setContent(plainContent);
                    article.setSourceUrl(item.link());
                    article.setSourceName(source.name);
                    article.setSourceType(source.type);
                    article.setCredibilityLevel(source.credibility);
                    article.setContentHash(deduplicationService.computeContentHash(plainContent));

                    // 基础摘要（LLM结构化等key到了再加）
                    article.setSummary(plainContent.length() > 100
                            ? plainContent.substring(0, 100) + "..."
                            : plainContent);

                    newsArticleRepository.save(article);
                    totalStored++;
                }
            } catch (Exception e) {
                log.error("采集源 {} 失败: {}", source.name, e.getMessage());
            }
        }

        log.info("新闻采集完成: collected={}, deduplicated={}, stored={}",
                totalCollected, totalDeduplicated, totalStored);
        return new CollectResult(totalCollected, totalDeduplicated, totalStored);
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&[a-zA-Z]+;", " ").trim();
    }

    public record CollectResult(int collected, int deduplicated, int stored) {}
    private record SourceConfig(String url, String name, String type, String credibility) {}
}
