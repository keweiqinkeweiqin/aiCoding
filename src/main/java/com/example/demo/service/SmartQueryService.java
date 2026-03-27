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
        List<Long> articleIds = vectorSearchService.semanticSearch(userQuestion, 15);
        List<NewsArticle> relatedArticles = articleIds.stream()
                .map(newsArticleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (relatedArticles.isEmpty()) {
            return new QueryResult("\u6682\u65e0\u76f8\u5173\u65b0\u95fb\u6570\u636e\uff0c\u8bf7\u5148\u91c7\u96c6\u65b0\u95fb\u3002", List.of(), 0);
        }

        String newsContext = relatedArticles.stream()
                .map(a -> {
                    String body = a.getSummary() != null && !a.getSummary().isBlank()
                            ? a.getSummary()
                            : (a.getContent() != null && a.getContent().length() > 200
                                    ? a.getContent().substring(0, 200) + "..."
                                    : a.getContent());
                    return String.format("[%s|%s] %s \u2014 %s",
                            a.getSourceName(),
                            a.getCredibilityLevel() != null ? a.getCredibilityLevel() : "unknown",
                            a.getTitle(),
                            body);
                })
                .collect(Collectors.joining("\n"));

        String systemPrompt = "\u4f60\u662f\u300c\u534e\u5c14\u8857\u4e4b\u773c\u300dAI\u6295\u7814\u52a9\u624b\uff0c\u4e13\u6ce8AI\u4e0e\u79d1\u6280\u6295\u8d44\u9886\u57df\u3002\n"
                + "\u57fa\u4e8e\u63d0\u4f9b\u7684\u65b0\u95fb\u6570\u636e\u56de\u7b54\u7528\u6237\u95ee\u9898\u3002\u8981\u6c42\uff1a\n"
                + "1. \u6807\u6ce8\u4fe1\u606f\u6765\u6e90\u53ca\u53ef\u4fe1\u5ea6\n"
                + "2. \u533a\u5206\u4e8b\u5b9e\u4e0e\u89c2\u70b9\n"
                + "3. \u7ed9\u51fa\u6295\u8d44\u76f8\u5173\u7684\u5206\u6790\u548c\u5efa\u8bae\n"
                + "4. \u5982\u679c\u4fe1\u606f\u4e0d\u8db3\uff0c\u660e\u786e\u8bf4\u660e";

        String userPrompt = String.format(
                "## \u76f8\u5173\u65b0\u95fb\uff08\u5171%d\u6761\uff0c\u6309\u8bed\u4e49\u76f8\u5173\u5ea6\u6392\u5e8f\uff09\n\n%s\n\n## \u7528\u6237\u95ee\u9898\n%s\n\n\u8bf7\u57fa\u4e8e\u4ee5\u4e0a\u65b0\u95fb\u6570\u636e\u8fdb\u884c\u5206\u6790\u548c\u56de\u7b54\u3002",
                relatedArticles.size(), newsContext, userQuestion);

        try {
            String answer = callLlm(systemPrompt, userPrompt);
            return new QueryResult(answer, relatedArticles, relatedArticles.size());
        } catch (Exception e) {
            log.error("LLM query failed: {}", e.getMessage());
            return new QueryResult("AI\u5206\u6790\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528: " + e.getMessage(), relatedArticles, relatedArticles.size());
        }
    }

    private String callLlm(String systemPrompt, String userPrompt) throws Exception {
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
