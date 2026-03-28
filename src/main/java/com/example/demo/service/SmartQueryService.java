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

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final HttpClient streamHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:GPT-5.4}")
    private String modelName;

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
                "model", modelName,
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

    /**
     * Streaming query: first emit related news metadata, then stream LLM answer token by token.
     * SSE events:
     *   event: meta     — JSON with matchedCount + relatedNews
     *   event: token    — partial text chunk
     *   event: done     — empty, signals completion
     *   event: error    — error message
     */
    public void streamQuery(String userQuestion, SseEmitter emitter) {
        try {
            // 1. Semantic search (same as sync)
            List<Long> articleIds = vectorSearchService.semanticSearch(userQuestion, 15);
            List<NewsArticle> relatedArticles = articleIds.stream()
                    .map(newsArticleRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();

            // 2. Emit metadata first
            Map<String, Object> meta = Map.of(
                    "matchedCount", relatedArticles.size(),
                    "relatedNews", relatedArticles.stream().map(n -> Map.of(
                            "id", n.getId(),
                            "title", n.getTitle(),
                            "sourceName", n.getSourceName() != null ? n.getSourceName() : "",
                            "credibilityLevel", n.getCredibilityLevel() != null ? n.getCredibilityLevel() : "unknown",
                            "sourceUrl", n.getSourceUrl() != null ? n.getSourceUrl() : ""
                    )).toList()
            );
            emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));

            if (relatedArticles.isEmpty()) {
                emitter.send(SseEmitter.event().name("token").data("暂无相关新闻数据，请先采集新闻。"));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
                return;
            }

            // 3. Build prompt (same as sync)
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
                                a.getTitle(), body);
                    })
                    .collect(Collectors.joining("\n"));

            String systemPrompt = "你是「华尔街之眼」AI投研助手，专注AI与科技投资领域。\n"
                    + "基于提供的新闻数据回答用户问题。\n\n"
                    + "输出格式要求（Markdown）：\n"
                    + "1. 先用 2-3 句话给出**核心结论**\n"
                    + "2. 用 ## 二级标题分段组织内容（如 ## 事件概述、## 市场影响、## 投资建议）\n"
                    + "3. 关键数据和观点用 **加粗** 标注\n"
                    + "4. 多条信息用有序或无序列表呈现\n"
                    + "5. 标注信息来源及可信度（如 [财联社|权威]）\n"
                    + "6. 区分事实与观点，给出投资相关的分析和建议\n"
                    + "7. 如果信息不足，明确说明";

            String userPrompt = String.format(
                    "## 相关新闻（共%d条，按语义相关度排序）\n\n%s\n\n## 用户问题\n%s\n\n请基于以上新闻数据进行分析和回答。",
                    relatedArticles.size(), newsContext, userQuestion);

            // 4. Call LLM with stream=true
            Map<String, Object> reqBody = Map.of(
                    "model", modelName,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.3,
                    "stream", true
            );

            String jsonBody = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<java.io.InputStream> response = streamHttpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            if (statusCode != 200) {
                // Read error body
                String errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                log.error("LLM stream returned HTTP {}: {}", statusCode, errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody);
                emitter.send(SseEmitter.event().name("token").data("LLM 服务返回错误 (HTTP " + statusCode + ")，请稍后重试。"));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
                return;
            }

            log.info("LLM stream connected, reading chunks...");
            boolean anyTokenSent = false;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode choices = chunk.path("choices");
                        if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                            log.debug("Stream chunk no choices: {}", data.length() > 200 ? data.substring(0, 200) : data);
                            continue;
                        }
                        JsonNode delta = choices.get(0).path("delta");
                        if (delta.isMissingNode()) {
                            log.debug("Stream chunk no delta: {}", data.length() > 200 ? data.substring(0, 200) : data);
                            continue;
                        }
                        // reasoning_content: model thinking process (Qwen/DeepSeek style)
                        String reasoning = delta.path("reasoning_content").asText("");
                        if (!reasoning.isEmpty()) {
                            emitter.send(SseEmitter.event().name("reasoning").data(reasoning));
                        }
                        // content: actual answer
                        String content = delta.path("content").asText("");
                        if (!content.isEmpty()) {
                            emitter.send(SseEmitter.event().name("token").data(content));
                            anyTokenSent = true;
                        }
                    } catch (Exception ex) {
                        log.warn("Stream chunk parse error: {}", ex.getMessage());
                    }
                }
            }

            if (!anyTokenSent) {
                log.warn("LLM stream finished but no token/reasoning was emitted");
                emitter.send(SseEmitter.event().name("token").data("AI 未返回有效内容，可能是模型名称配置有误或服务暂时不可用。"));
            }

            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (Exception e) {
            log.error("Stream query failed: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }
}
