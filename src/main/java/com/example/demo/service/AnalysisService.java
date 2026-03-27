package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final IntelligenceRepository intelligenceRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public AnalysisService(IntelligenceRepository intelligenceRepository,
                           UserRepository userRepository,
                           UserProfileRepository userProfileRepository,
                           AnalysisRecordRepository analysisRecordRepository,
                           RestTemplate restTemplate) {
        this.intelligenceRepository = intelligenceRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.restTemplate = restTemplate;
    }

    // ==================== Task 2.1: Prompt Building ====================

    String buildSystemPrompt() {
        return "你是「华尔街之眼」AI 投研助手，专注 AI 与科技投资领域的个性化研判分析。\n\n"
                + "你必须严格按以下 JSON 格式返回结果，不要包含任何其他文本：\n"
                + "{\n"
                + "  \"analysis\": \"情报研判文本，200字以内，概述核心影响\",\n"
                + "  \"impacts\": [\n"
                + "    {\n"
                + "      \"stock\": \"股票代码\",\n"
                + "      \"impact\": \"对该股票的影响分析\",\n"
                + "      \"level\": \"重大影响|中等影响|正面影响\",\n"
                + "      \"volatility\": \"预计波动幅度描述\",\n"
                + "      \"revenueImpact\": \"对营收的影响分析\",\n"
                + "      \"longTermImpact\": \"长期影响分析\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"suggestion\": \"基于用户画像的操作建议\",\n"
                + "  \"risks\": [\"风险提示1\", \"风险提示2\"],\n"
                + "  \"userContext\": \"说明该建议基于何种画像生成\"\n"
                + "}\n\n"
                + "分析维度要求：\n"
                + "- 影响等级定义：重大影响(直接影响核心业务)、中等影响(间接关联)、正面影响(利好因素)\n"
                + "- 波动预估：给出具体百分比区间\n"
                + "- 风险提示：至少给出2条具体风险\n"
                + "- 操作建议：结合用户投资者类型和风险偏好给出";
    }

    String buildUserPrompt(Intelligence intel, UserProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 情报信息\n");
        sb.append("- 标题：").append(intel.getTitle()).append("\n");
        sb.append("- 摘要：").append(intel.getSummary()).append("\n");
        sb.append("- 关联股票：").append(intel.getRelatedStocks()).append("\n");
        sb.append("- 情感倾向：").append(intel.getSentiment()).append("\n");
        sb.append("- 标签：").append(intel.getTags()).append("\n");
        sb.append("\n## 用户投资画像\n");
        sb.append("- 投资者类型：").append(profile.getInvestorType()).append("\n");
        sb.append("- 投资周期：").append(profile.getInvestmentCycle()).append("\n");
        sb.append("- 关注领域：").append(profile.getFocusAreas()).append("\n");
        sb.append("\n## 用户持仓\n");
        String holdings = profile.getHoldings();
        if (holdings != null && !holdings.isBlank()) {
            for (String name : holdings.split(",")) {
                sb.append("- ").append(name.trim()).append("\n");
            }
        } else {
            sb.append("- 暂无持仓\n");
        }
        sb.append("\n请基于以上信息生成个性化研判分析。");
        return sb.toString();
    }

    // ==================== Task 2.4: callLlm & parseLlmResponse ====================

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

    Map<String, Object> parseLlmResponse(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 直接解析失败，尝试正则提取: {}", e.getMessage());
        }
        // Try regex extraction
        try {
            Matcher matcher = Pattern.compile("\\{.*}", Pattern.DOTALL).matcher(raw);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("正则提取 JSON 也失败: {}", e.getMessage());
        }
        // Degraded response
        return Map.of(
                "analysis", "AI 分析服务暂时不可用",
                "impacts", List.of(),
                "suggestion", "暂无操作建议",
                "risks", List.of(),
                "userContext", ""
        );
    }

    // ==================== Task 2.6: generateAnalysis (sync) ====================

    public Map<String, Object> generateAnalysis(Long userId, Long articleId) {
        Intelligence intel = intelligenceRepository.findById(articleId)
                .orElseThrow(() -> new NoSuchElementException("情报不存在"));
        userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("用户不存在"));

        // Cache check: 1 hour validity
        Optional<AnalysisRecord> cached = analysisRecordRepository
                .findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc(userId, articleId);
        if (cached.isPresent() &&
                cached.get().getCreatedAt().isAfter(LocalDateTime.now().minusHours(1))) {
            try {
                return objectMapper.readValue(cached.get().getAnalysisText(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("缓存解析失败，重新生成: {}", e.getMessage());
            }
        }

        // Load profile
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(new UserProfile());

        // Build prompts and call LLM
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(intel, profile);

        Map<String, Object> result;
        try {
            String raw = callLlm(systemPrompt, userPrompt);
            result = parseLlmResponse(raw);
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage());
            return Map.of(
                    "analysis", "AI 分析服务暂时不可用",
                    "impacts", List.of(),
                    "suggestion", "暂无操作建议",
                    "risks", List.of(),
                    "userContext", ""
            );
        }

        // Persist
        try {
            AnalysisRecord record = new AnalysisRecord();
            record.setUserId(userId);
            record.setNewsArticleId(articleId);
            record.setAnalysisText(objectMapper.writeValueAsString(result));
            record.setInvestmentStyle(profile.getInvestorType());
            analysisRecordRepository.save(record);
        } catch (Exception e) {
            log.error("研判记录持久化失败: {}", e.getMessage());
        }

        return result;
    }

    // ==================== Task 2.8: streamAnalysis (SSE) ====================

    public void streamAnalysis(Long userId, Long articleId, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                Intelligence intel = intelligenceRepository.findById(articleId)
                        .orElseThrow(() -> new NoSuchElementException("情报不存在"));
                userRepository.findById(userId)
                        .orElseThrow(() -> new NoSuchElementException("用户不存在"));

                UserProfile profile = userProfileRepository.findByUserId(userId).orElse(new UserProfile());

                String systemPrompt = buildSystemPrompt();
                String userPrompt = buildUserPrompt(intel, profile);
                String raw = callLlm(systemPrompt, userPrompt);
                Map<String, Object> result = parseLlmResponse(raw);

                // Push sections in order
                String[] sections = {"analysis", "impacts", "suggestion", "risks"};
                for (String section : sections) {
                    Object value = result.get(section);
                    String content = (value instanceof String) ? (String) value
                            : objectMapper.writeValueAsString(value);
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of("section", section, "content", content))));
                }

                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();

                // Persist
                AnalysisRecord record = new AnalysisRecord();
                record.setUserId(userId);
                record.setNewsArticleId(articleId);
                record.setAnalysisText(objectMapper.writeValueAsString(result));
                record.setInvestmentStyle(profile.getInvestorType());
                analysisRecordRepository.save(record);

            } catch (Exception e) {
                log.error("SSE 研判失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of("error", "研判生成失败: " + e.getMessage()))));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    // ==================== Task 2.9: getHistory ====================

    public List<AnalysisRecord> getHistory(Long userId) {
        return analysisRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
