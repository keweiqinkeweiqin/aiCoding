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
    private final EventClusterService eventClusterService;
    private final ChatClient chatClient;
    private final com.example.demo.collector.TianApiCollector tianApiCollector;
    private final com.example.demo.collector.NewsApiCollector newsApiCollector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<SourceConfig> RSS_SOURCES = List.of(
        new SourceConfig("https://rsshub.chn.moe/36kr/information/web_news", "36Kr资讯", "rss", "normal"),
        new SourceConfig("https://rsshub.chn.moe/36kr/newsflashes", "36Kr快讯", "rss", "normal"),
        new SourceConfig("https://rsshub.chn.moe/cls/depth/1000", "财联社", "rss", "authoritative"),
        new SourceConfig("https://rsshub.chn.moe/cls/telegraph", "财联社电报", "rss", "authoritative"),
        new SourceConfig("https://rsshub.chn.moe/wallstreetcn/news/global", "华尔街见闻", "rss", "normal"),
        new SourceConfig("https://rsshub.chn.moe/jin10", "金十数据", "rss", "normal"),
        new SourceConfig("https://rsshub.chn.moe/gelonghui/live", "格隆汇", "rss", "normal"),
        new SourceConfig("https://rsshub.chn.moe/yicai/brief", "第一财经", "rss", "authoritative"),
        new SourceConfig("https://rsshub.chn.moe/eastmoney/report/strategyreport", "东方财富研报", "rss", "authoritative"),
        new SourceConfig("https://rsshub.chn.moe/ithome/tag/AI", "IT之家AI", "rss", "normal")
    );

    private static final String LLM_SYSTEM = "You are an AI tech finance analyst. Analyze news and extract structured info. Return strict JSON only.";
    private static final String LLM_USER = "Analyze news, return JSON: {\"summary\":\"max 80 chars\",\"tags\":\"AI,chip\",\"relatedStocks\":\"codes\",\"sentiment\":\"positive/negative/neutral\",\"sentimentScore\":0.0}\n\nTitle: %s\nSource: %s\nContent: %s";

    public NewsCollectorService(RssCollector rssCollector, DeduplicationService deduplicationService,
            NewsArticleRepository newsArticleRepository, EmbeddingClient embeddingClient,
            VectorSearchService vectorSearchService, CredibilityService credibilityService,
            EventClusterService eventClusterService, ChatClient.Builder chatClientBuilder,
            com.example.demo.collector.TianApiCollector tianApiCollector,
            com.example.demo.collector.NewsApiCollector newsApiCollector) {
        this.rssCollector = rssCollector;
        this.deduplicationService = deduplicationService;
        this.newsArticleRepository = newsArticleRepository;
        this.embeddingClient = embeddingClient;
        this.vectorSearchService = vectorSearchService;
        this.credibilityService = credibilityService;
        this.eventClusterService = eventClusterService;
        this.chatClient = chatClientBuilder.build();
        this.tianApiCollector = tianApiCollector;
        this.newsApiCollector = newsApiCollector;
    }

    public CollectResult collectAll() {
        int totalCollected = 0, totalDeduplicated = 0, totalStored = 0;
        java.util.List<SourceResult> sourceResults = new java.util.ArrayList<>();
        for (SourceConfig source : RSS_SOURCES) {
            int srcCollected = 0, srcDedup = 0, srcStored = 0;
            try {
                List<RssCollector.RssItem> items = rssCollector.collect(source.url, source.name);
                srcCollected = items.size();
                totalCollected += srcCollected;
                for (RssCollector.RssItem item : items) {
                    String plain = stripHtml(item.content());
                    String dup = deduplicationService.checkDuplicate(item.title(), item.link(), plain);
                    if (dup != null) { totalDeduplicated++; srcDedup++; continue; }
                    NewsArticle a = new NewsArticle();
                    a.setTitle(item.title()); a.setContent(plain); a.setSourceUrl(item.link());
                    a.setSourceName(source.name); a.setSourceType(source.type);
                    a.setCredibilityLevel(source.credibility);
                    a.setContentHash(deduplicationService.computeContentHash(plain));
                    try {
                        String c = plain.length() > 500 ? plain.substring(0, 500) : plain;
                        String p = String.format(LLM_USER, item.title(), source.name, c);
                        String r = chatClient.prompt().system(LLM_SYSTEM).user(p).call().content();
                        String j = r;
                        if (j.contains("```json")) { j = j.substring(j.indexOf("```json")+7); j = j.substring(0, j.indexOf("```")); }
                        else if (j.contains("```")) { j = j.substring(j.indexOf("```")+3); j = j.substring(0, j.indexOf("```")); }
                        JsonNode n = objectMapper.readTree(j.trim());
                        if (n.has("summary")) a.setSummary(n.get("summary").asText());
                        if (n.has("tags")) a.setTags(n.get("tags").asText());
                        if (n.has("relatedStocks")) a.setRelatedStocks(n.get("relatedStocks").asText());
                        if (n.has("sentiment")) a.setSentiment(n.get("sentiment").asText());
                        if (n.has("sentimentScore")) a.setSentimentScore(n.get("sentimentScore").asDouble());
                    } catch (Exception e) {
                        a.setSummary(plain.length() > 100 ? plain.substring(0, 100) + "..." : plain);
                    }
                    newsArticleRepository.save(a);
                    try {
                        float[] vec = embeddingClient.embed(a.getTitle());
                        if (vec.length > 0) {
                            java.util.List<Float> fl = new java.util.ArrayList<>();
                            for (float v : vec) fl.add(v);
                            a.setEmbeddingJson(objectMapper.writeValueAsString(fl));
                            newsArticleRepository.save(a);
                            vectorSearchService.addVector(a.getId(), vec);
                        }
                    } catch (Exception e) { log.warn("Embed fail: {}", e.getMessage()); }
                    totalStored++; srcStored++;
                }
            } catch (Exception e) { log.error("Source {} fail: {}", source.name, e.getMessage()); }
            sourceResults.add(new SourceResult(source.name, srcCollected, srcDedup, srcStored));
        }
        // TianAPI finance news
        try {
            var tianItems = tianApiCollector.collect();
            int tianStored = 0;
            for (var item : tianItems) {
                String plain = stripHtml(item.content());
                String dup = deduplicationService.checkDuplicate(item.title(), item.link(), plain);
                if (dup != null) { totalDeduplicated++; continue; }
                NewsArticle a = new NewsArticle();
                a.setTitle(item.title()); a.setContent(plain); a.setSourceUrl(item.link());
                a.setSourceName("天聚数据"); a.setSourceType("api");
                a.setCredibilityLevel("normal");
                a.setContentHash(deduplicationService.computeContentHash(plain));
                a.setSummary(plain.length() > 100 ? plain.substring(0, 100) + "..." : plain);
                newsArticleRepository.save(a);
                try {
                    float[] vec = embeddingClient.embed(a.getTitle());
                    if (vec.length > 0) {
                        java.util.List<Float> fl = new java.util.ArrayList<>();
                        for (float v : vec) fl.add(v);
                        a.setEmbeddingJson(objectMapper.writeValueAsString(fl));
                        newsArticleRepository.save(a);
                        vectorSearchService.addVector(a.getId(), vec);
                    }
                } catch (Exception e) { /* skip */ }
                totalStored++; tianStored++;
            }
            totalCollected += tianItems.size();
            sourceResults.add(new SourceResult("天聚数据", tianItems.size(), tianItems.size() - tianStored, tianStored));
        } catch (Exception e) { log.warn("TianAPI failed: {}", e.getMessage()); }

        // NewsAPI global tech news
        try {
            var newsApiItems = newsApiCollector.collect();
            int naStored = 0;
            for (var item : newsApiItems) {
                String plain = stripHtml(item.content());
                String dup = deduplicationService.checkDuplicate(item.title(), item.link(), plain);
                if (dup != null) { totalDeduplicated++; continue; }
                NewsArticle a = new NewsArticle();
                a.setTitle(item.title()); a.setContent(plain); a.setSourceUrl(item.link());
                a.setSourceName(item.sourceName()); a.setSourceType("api");
                a.setCredibilityLevel("normal");
                a.setContentHash(deduplicationService.computeContentHash(plain));
                a.setSummary(plain.length() > 100 ? plain.substring(0, 100) + "..." : plain);
                newsArticleRepository.save(a);
                try {
                    float[] vec = embeddingClient.embed(a.getTitle());
                    if (vec.length > 0) {
                        java.util.List<Float> fl = new java.util.ArrayList<>();
                        for (float v : vec) fl.add(v);
                        a.setEmbeddingJson(objectMapper.writeValueAsString(fl));
                        newsArticleRepository.save(a);
                        vectorSearchService.addVector(a.getId(), vec);
                    }
                } catch (Exception e) { /* skip */ }
                totalStored++; naStored++;
            }
            totalCollected += newsApiItems.size();
            sourceResults.add(new SourceResult("NewsAPI", newsApiItems.size(), newsApiItems.size() - naStored, naStored));
        } catch (Exception e) { log.warn("NewsAPI failed: {}", e.getMessage()); }

        assessCredibilityForRecent();
        try {
            var cr = eventClusterService.clusterRecent();
            log.info("Cluster: created={}, merged={}", cr.created(), cr.merged());
        } catch (Exception e) { log.error("Cluster fail: {}", e.getMessage()); }
        return new CollectResult(totalCollected, totalDeduplicated, totalStored, sourceResults);
    }

    private void assessCredibilityForRecent() {
        var recent = newsArticleRepository.findByCollectedAtAfterOrderByCollectedAtDesc(LocalDateTime.now().minusHours(1));
        for (NewsArticle article : recent) {
            try {
                var r = credibilityService.assess(article);
                article.setCredibilityScore(r.overallScore());
                article.setCredibilityLevel(r.level());
                article.setSourceCredibility(r.sourceScore());
                article.setLlmCredibility(r.llmScore());
                article.setFreshnessCredibility(r.freshnessScore());
                article.setCrossCredibility(r.crossScore());
                newsArticleRepository.save(article);
            } catch (Exception e) { log.warn("Credibility fail: {}", e.getMessage()); }
        }
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&[a-zA-Z]+;", " ").trim();
    }

    public record CollectResult(int collected, int deduplicated, int stored, List<SourceResult> sources) {}
    public record SourceResult(String name, int collected, int deduplicated, int stored) {}
    private record SourceConfig(String url, String name, String type, String credibility) {}
}
