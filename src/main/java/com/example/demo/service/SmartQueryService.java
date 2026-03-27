package com.example.demo.service;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.NewsArticleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能问答：用户输入 → Embedding语义检索相关新闻 → LLM推理生成分析
 * 使用 Qwen3.5-flash 做问答（比GPT-5.4快很多）
 */
@Service
public class SmartQueryService {

    private static final Logger log = LoggerFactory.getLogger(SmartQueryService.class);
    private final VectorSearchService vectorSearchService;
    private final NewsArticleRepository newsArticleRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public SmartQueryService(VectorSearchService vectorSearchService,
                             NewsArticleRepository newsArticleRepository,
                             RestTemplate restTemplate) {
        this.vectorSearchService = vectorSearchService;
        this.newsArticleRepository = newsArticleRepository;
        this.restTemplate = restTemplate;
    }

    public QueryResult query(String userQuestion) {
        // 1. 语义检索最相关的15篇新闻
        List<Long> articleIds = vectorSearchService.semanticSearch(userQuestion, 15);
        List<NewsArticle> relatedArticles = articleIds.stream()
                .map(newsArticleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (relatedArticles.isEmpty()) {
            return new QueryResult("暂无相关新闻数据，请先采集新闻。", List.of(), 0);
        }

        // 2. 组装上下文（优先用摘要，节省token让更多文章进入上下文）
        String newsContext = relatedArticles.stream()
                .map(a -> {
                    String body = a.getSummary() != null && !a.getSummary().isBlank()
                            ? a.getSummary()
                            : (a.getContent() != null && a.getContent().length() > 200
                                    ? a.getContent().substring(0, 200) + "..."
                                    : a.getContent());
                    return String.format("[%s|%s] %s — %s",
                            a.getSourceName(),
                            a.getCredibilityLevel() != null ? a.getCredibilityLevel() : "unknown",
                            a.getTitle(),
                            body);
                })
                .collect(Collectors.joining("\n"));

        // 3. 调LLM推理
        String systemPrompt = """
                你是「华尔街之眼」AI投研助手，专注AI与科技投资领域。
                基于提供的新闻数据回答用户问题。要求：
                1. 标注信息来源及可信度
                2. 区分事实与观点
                3. 给出投资相关的分析和建议
                4. 如果信息不足，明确说明
                """;

        String userPrompt = String.format("""
                ## 相关新闻（共%d条，按语义相关度排序）
                
                %s
                
                ## 用户问题
                %s
                
                请基于以上新闻数据进行分析和回答。
                """, relatedArticles.size(), newsContext, userQuestion);

        try {
            String answer = callLlm(systemPrompt, userPrompt);
            return new QueryResult(answer, relatedArticles, relatedArticles.size());
        } catch (Exception e) {
            log.error("LLM推理失败: {}", e.getMessage());
            return new QueryResult("AI分析服务暂时不可用: " + e.getMessage(), relatedArticles, relatedArticles.size());
        }
    }

    /** 使用 Qwen3.5-flash 做问答（快） */
    private String callLlm(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = Map.of(
                "model", "Qwen3.5-flash",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.3
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/v1/chat/completions", request, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.get("choices").get(0).get("message").get("content").asText();
    }

    public record QueryResult(String answer, List<NewsArticle> relatedNews, int matchedCount) {}
}
