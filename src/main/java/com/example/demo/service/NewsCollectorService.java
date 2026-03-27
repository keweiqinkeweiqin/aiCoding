package com.example.demo.service;

import com.example.demo.collector.RssCollector;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.NewsArticleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NewsCollectorService {

    private static final Logger log = LoggerFactory.getLogger(NewsCollectorService.class);

    private final RssCollector rssCollector;
    private final DeduplicationService deduplicationService;
    private final NewsArticleRepository newsArticleRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchService vectorSearchService;
    private final CredibilityService credibilityService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<SourceConfig> RSS_SOURCES = List.of(
            new SourceConfig("https://rsshub.chn.moe/36kr/information/web_news", "36Kr", "rss", "normal"),
            new SourceConfig("https://rsshub.chn.moe/cls/depth/1000", "财联社", "rss", "authoritative"),
            new SourceConfig("https://rsshub.chn.moe/cls/telegraph", "财联社电报", "rss", "authoritative"),
            new SourceConfig("https://rsshub.chn.moe/wallstreetcn/news/global", "华尔街见闻", "rss", "normal"),
            new SourceConfig("https://rsshub.chn.moe/jin10", "金十数据", "rss", "normal"),
            new SourceConfig("https://rsshub.chn.moe/eastmoney/report/strategyreport", "东方财富研报", "rss", "authoritative")
    );

    private static final String LLM_EXTRACT_SYSTEM = """
            你是AI科技领域财经分析师。分析新闻并提取结构化信息，严格返回JSON，不要添加任何额外文字。
            """;

    private static final String LLM_EXTRACT_USER = """
            分析以下新闻，返回JSON：
            {"summary":"一句话摘要(不超过80字)","tags":"标签逗号分隔如AI,芯片","relatedStocks":"关联股票代码逗号分隔","sentiment":"positive/negative/neutral","sentimentScore":0.0到1.0}
            
            标题：%s
            来源：%s
            内容：%s
            """;

    public NewsCollectorService(RssCollector rssCollector,
                                 DeduplicationService deduplicationService,
                                 NewsArticleRepository newsArticleRepository,
                                 EmbeddingClient embeddingClient,
                                 VectorSearchService vectorSearchService,
                                 CredibilityService credibilityService,
                                 ChatClient.Builder chatClientBuilder) {
        this.rssCollector = rssCollector;
        this.deduplicationService = deduplicationService;
        this.newsArticleRepository = newsArticleRepository;
        this.embeddingClient = embeddingClient;
        this.vectorSearchService = vectorSearchService;
        this.credibilityService = credibilityService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 从所有RSS源采集新闻，去重后入库
     */
    public CollectResult collectAll() {
        int totalCollected = 0;
        int totalDeduplicated = 0;
        int totalStored = 0;
        java.util.List<SourceResult> sourceResults = new java.util.ArrayList<>();

        for (SourceConfig source : RSS_SOURCES) {
            int srcCollected = 0, srcDedup = 0, srcStored = 0;
            try {
                List<RssCollector.RssItem> items = rssCollector.collect(source.url, source.name);
                srcCollected = items.size();
                totalCollected += srcCollected;

                for (RssCollector.RssItem item : items) {
                    // 去重检查
                    String plainContent = stripHtml(item.content());
                    String dupReason = deduplicationService.checkDuplicate(
                            item.title(), item.link(), plainContent);

                    if (dupReason != null) {
                        totalDeduplicated++;
                        srcDedup++;
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

                    // LLM结构化提取
                    try {
                        String contentForLlm = plainContent.length() > 500 ? plainContent.substring(0, 500) : plainContent;
                        String prompt = String.format(LLM_EXTRACT_USER, item.title(), source.name, contentForLlm);
                        String llmResponse = chatClient.prompt()
                                .system(LLM_EXTRACT_SYSTEM)
                                .user(prompt)
                                .call()
                                .content();

                        // 提取JSON（兼容```json```包裹）
                        String json = llmResponse;
                        if (json.contains("```json")) {
                            json = json.substring(json.indexOf("```json") + 7);
                            json = json.substring(0, json.indexOf("```"));
                        } else if (json.contains("```")) {
                            json = json.substring(json.indexOf("```") + 3);
                            json = json.substring(0, json.indexOf("```"));
                        }

                        JsonNode node = objectMapper.readTree(json.trim());
                        if (node.has("summary")) article.setSummary(node.get("summary").asText());
                        if (node.has("tags")) article.setTags(node.get("tags").asText());
                        if (node.has("relatedStocks")) article.setRelatedStocks(node.get("relatedStocks").asText());
                        if (node.has("sentiment")) article.setSentiment(node.get("sentiment").asText());
                        if (node.has("sentimentScore")) article.setSentimentScore(node.get("sentimentScore").asDouble());
                        log.info("LLM结构化完成[{}]: sentiment={}, tags={}", item.title(),
                                article.getSentiment(), article.getTags());
                    } catch (Exception e) {
                        log.warn("LLM结构化失败[{}]: {}, 使用基础提取", item.title(), e.getMessage());
                        article.setSummary(plainContent.length() > 100 ? plainContent.substring(0, 100) + "..." : plainContent);
                    }

                    newsArticleRepository.save(article);

                    // 生成Embedding并缓存
                    try {
                        String textForEmbed = article.getTitle();
                        float[] vector = embeddingClient.embed(textForEmbed);
                        if (vector.length > 0) {
                            List<Float> floatList = new java.util.ArrayList<>();
                            for (float v : vector) floatList.add(v);
                            article.setEmbeddingJson(objectMapper.writeValueAsString(floatList));
                            newsArticleRepository.save(article);
                            vectorSearchService.addVector(article.getId(), vector);
                        }
                    } catch (Exception e) {
                        log.warn("Embedding生成失败[{}]: {}", article.getTitle(), e.getMessage());
                    }

                    totalStored++;
                    srcStored++;
                }
            } catch (Exception e) {
                log.error("采集源 {} 失败: {}", source.name, e.getMessage());
            }
            sourceResults.add(new SourceResult(source.name, srcCollected, srcDedup, srcStored));
        }

        // 采集完成后，对本批新入库的文章做置信度评估（需要交叉验证所以放最后）
        assessCredibilityForRecent();

        log.info("新闻采集完成: collected={}, deduplicated={}, stored={}",
                totalCollected, totalDeduplicated, totalStored);
        return new CollectResult(totalCollected, totalDeduplicated, totalStored, sourceResults);
    }

    /** 对最近1小时入库的文章做置信度评估 */
    private void assessCredibilityForRecent() {
        var recentArticles = newsArticleRepository.findByCollectedAtAfterOrderByCollectedAtDesc(
                LocalDateTime.now().minusHours(1));
        for (NewsArticle article : recentArticles) {
            try {
                var result = credibilityService.assess(article);
                article.setCredibilityScore(result.overallScore());
                article.setCredibilityLevel(result.level());
                article.setSourceCredibility(result.sourceScore());
                article.setLlmCredibility(result.llmScore());
                article.setFreshnessCredibility(result.freshnessScore());
                article.setCrossCredibility(result.crossScore());
                newsArticleRepository.save(article);
            } catch (Exception e) {
                log.warn("置信度评估失败[{}]: {}", article.getTitle(), e.getMessage());
            }
        }
        log.info("置信度评估完成: {} 条", recentArticles.size());
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&[a-zA-Z]+;", " ").trim();
    }

    public record CollectResult(int collected, int deduplicated, int stored, List<SourceResult> sources) {}
    public record SourceResult(String name, int collected, int deduplicated, int stored) {}
    private record SourceConfig(String url, String name, String type, String credibility) {}
}
