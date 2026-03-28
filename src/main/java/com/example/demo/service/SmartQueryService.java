package com.example.demo.service;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SmartQueryService {

    private static final Logger log = LoggerFactory.getLogger(SmartQueryService.class);
    private static final double MIN_SIMILARITY = 0.3; // 最低相关度阈值
    private final VectorSearchService vectorSearchService;
    private final NewsArticleRepository newsArticleRepository;
    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
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
                             IntelligenceRepository intelligenceRepository,
                             IntelligenceArticleRepository intelligenceArticleRepository,
                             RestTemplate restTemplate) {
        this.vectorSearchService = vectorSearchService;
        this.newsArticleRepository = newsArticleRepository;
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * 混合检索：语义搜索新闻 → 映射到情报 + 关键词搜索情报，合并去重，按相关度排序。
     * 返回情报列表（信息密度远高于原始新闻）。
     */
    private List<RankedIntelligence> hybridSearch(String question, int maxResults) {
        Map<Long, Double> intelScores = new LinkedHashMap<>();

        // 1. 语义搜索：直接搜情报向量，返回 intelligenceId + 相似度分数
        try {
            List<VectorSearchService.ScoredId> scored = vectorSearchService.searchIntelligences(question, 30);
            for (VectorSearchService.ScoredId s : scored) {
                if (s.score() < MIN_SIMILARITY) continue;
                intelScores.put(s.id(), s.score());
            }
        } catch (Exception e) {
            log.warn("语义搜索失败: {}", e.getMessage());
        }

        // 2. 关键词 fallback：搜情报标题/摘要/标签，基础分 0.35
        try {
            List<Intelligence> keywordResults = intelligenceRepository.searchByKeyword(question.trim());
            for (Intelligence intel : keywordResults) {
                intelScores.putIfAbsent(intel.getId(), 0.35);
            }
        } catch (Exception e) {
            log.warn("关键词搜索失败: {}", e.getMessage());
        }

        if (intelScores.isEmpty()) return List.of();

        // 3. 批量查询情报，按相关度排序
        List<Intelligence> intels = intelligenceRepository.findAllById(intelScores.keySet());
        return intels.stream()
                .map(i -> new RankedIntelligence(i, intelScores.getOrDefault(i.getId(), 0.0)))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxResults)
                .toList();
    }

    record RankedIntelligence(Intelligence intel, double score) {}

    /**
     * 将情报列表构建为 LLM 上下文（比原始新闻信息密度高得多）
     */
    private String buildIntelContext(List<RankedIntelligence> ranked) {
        return ranked.stream().map(r -> {
            Intelligence i = r.intel;
            String body = i.getSummary() != null && !i.getSummary().isBlank()
                    ? i.getSummary()
                    : (i.getContent() != null && i.getContent().length() > 300
                            ? i.getContent().substring(0, 300) + "..." : "");
            return String.format("[%s|%s|%d源|相关度%.0f%%] %s — %s",
                    i.getPrimarySource() != null ? i.getPrimarySource() : "未知",
                    i.getCredibilityLevel() != null ? i.getCredibilityLevel() : "unknown",
                    i.getSourceCount() != null ? i.getSourceCount() : 1,
                    r.score * 100,
                    i.getTitle(),
                    body);
        }).collect(Collectors.joining("\n\n"));
    }

    /** 构建 meta 数据（返回给前端展示的情报摘要） */
    private Map<String, Object> buildMeta(List<RankedIntelligence> ranked) {
        return Map.of(
                "matchedCount", ranked.size(),
                "relatedNews", ranked.stream().map(r -> {
                    Intelligence i = r.intel;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getId());
                    m.put("title", i.getTitle());
                    m.put("sourceName", i.getPrimarySource() != null ? i.getPrimarySource() : "");
                    m.put("credibilityLevel", i.getCredibilityLevel() != null ? i.getCredibilityLevel() : "unknown");
                    m.put("sourceCount", i.getSourceCount() != null ? i.getSourceCount() : 1);
                    m.put("sourceUrl", ""); // 情报无直接 URL
                    m.put("relevance", Math.round(r.score * 100));
                    return m;
                }).toList()
        );
    }

    public QueryResult query(String userQuestion) {
        List<RankedIntelligence> ranked = hybridSearch(userQuestion, 10);

        if (ranked.isEmpty()) {
            return new QueryResult("暂无相关情报数据，请先采集新闻。", List.of(), 0);
        }

        String context = buildIntelContext(ranked);

        String systemPrompt = "你是「华尔街之眼」AI投研助手，专注AI与科技投资领域。\n"
                + "基于提供的情报数据回答用户问题。要求：\n"
                + "1. 标注信息来源及可信度\n"
                + "2. 区分事实与观点\n"
                + "3. 给出投资相关的分析和建议\n"
                + "4. 如果信息不足，明确说明";

        String userPrompt = String.format(
                "## 相关情报（共%d条，按相关度排序，已聚合去重）\n\n%s\n\n## 用户问题\n%s\n\n请基于以上情报数据进行分析和回答。",
                ranked.size(), context, userQuestion);

        // 兼容旧的 relatedNews 字段（用情报的关联新闻填充）
        List<NewsArticle> relatedArticles = ranked.stream()
                .flatMap(r -> intelligenceArticleRepository
                        .findByIntelligenceIdOrderByIsPrimaryDesc(r.intel.getId()).stream()
                        .map(link -> newsArticleRepository.findById(link.getArticleId()).orElse(null)))
                .filter(Objects::nonNull)
                .distinct()
                .limit(15)
                .toList();

        try {
            String answer = callLlm(systemPrompt, userPrompt);
            return new QueryResult(answer, relatedArticles, ranked.size());
        } catch (Exception e) {
            log.error("LLM query failed: {}", e.getMessage());
            return new QueryResult("AI分析服务暂时不可用: " + e.getMessage(), relatedArticles, ranked.size());
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
    public void streamQuery(String userQuestion, List<Map<String, String>> history, SseEmitter emitter) {
        try {
            // 1. 混合检索：语义搜索 + 关键词 fallback → 情报级别
            List<RankedIntelligence> ranked = hybridSearch(userQuestion, 10);

            // 2. Emit metadata first
            emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(buildMeta(ranked))));

            if (ranked.isEmpty()) {
                emitter.send(SseEmitter.event().name("token").data("暂无相关情报数据，请先采集新闻。"));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
                return;
            }

            // 3. Build prompt with intelligence-level context
            String context = buildIntelContext(ranked);

            String systemPrompt = "你是「华尔街之眼」AI投研助手，专注AI与科技投资领域。\n"
                    + "基于提供的情报数据回答用户问题。每条情报已经过多来源聚合和去重。\n\n"
                    + "输出格式要求（Markdown）：\n"
                    + "1. 先用 2-3 句话给出**核心结论**\n"
                    + "2. 用 ## 二级标题分段组织内容（如 ## 事件概述、## 市场影响、## 投资建议）\n"
                    + "3. 关键数据和观点用 **加粗** 标注\n"
                    + "4. 多条信息用有序或无序列表呈现\n"
                    + "5. 标注信息来源及可信度（如 [财联社|权威]）\n"
                    + "6. 区分事实与观点，给出投资相关的分析和建议\n"
                    + "7. 如果信息不足，明确说明";

            String userPrompt = String.format(
                    "## 相关情报（共%d条，按相关度排序，已聚合去重）\n\n%s\n\n## 用户问题\n%s\n\n请基于以上情报数据进行分析和回答。",
                    ranked.size(), context, userQuestion);

            // 4. Build messages with history (keep last 5 rounds max)
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            // Append conversation history (last 5 rounds = 10 messages)
            if (history != null && !history.isEmpty()) {
                List<Map<String, String>> trimmed = history.size() > 10
                        ? history.subList(history.size() - 10, history.size()) : history;
                for (Map<String, String> msg : trimmed) {
                    String role = msg.getOrDefault("role", "");
                    String content = msg.getOrDefault("content", "");
                    if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                        // Truncate old assistant messages to save tokens
                        if ("assistant".equals(role) && content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }
                        messages.add(Map.of("role", role, "content", content));
                    }
                }
            }

            messages.add(Map.of("role", "user", "content", userPrompt));

            // 5. Call LLM with stream=true
            Map<String, Object> reqBody = Map.of(
                    "model", modelName,
                    "messages", messages,
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
            int chunkCount = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Log first 5 raw lines for debugging
                    if (chunkCount < 5) {
                        log.info("LLM raw line [{}]: {}", chunkCount, line.length() > 300 ? line.substring(0, 300) + "..." : line);
                    }
                    chunkCount++;
                    // Support both "data: " and "data:" (some LLM platforms omit the space)
                    String data;
                    if (line.startsWith("data: ")) {
                        data = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data = line.substring(5).trim();
                    } else {
                        continue;
                    }
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
                log.warn("LLM stream finished but no token/reasoning was emitted. Total lines read: {}", chunkCount);
                emitter.send(SseEmitter.event().name("token").data("AI 未返回有效内容，可能是模型名称配置有误或服务暂时不可用。"));
            } else {
                log.info("LLM stream completed. Total lines: {}", chunkCount);
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
