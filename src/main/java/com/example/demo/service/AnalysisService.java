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
    private final UserHoldingRepository userHoldingRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:GPT-5.4}")
    private String modelName;

    public AnalysisService(IntelligenceRepository intelligenceRepository,
                           UserRepository userRepository,
                           UserProfileRepository userProfileRepository,
                           UserHoldingRepository userHoldingRepository,
                           AnalysisRecordRepository analysisRecordRepository,
                           RestTemplate restTemplate) {
        this.intelligenceRepository = intelligenceRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userHoldingRepository = userHoldingRepository;
        this.analysisRecordRepository = analysisRecordRepository;
        this.restTemplate = restTemplate;
    }

    // ==================== Task 2.1: Prompt Building ====================

    String buildSystemPrompt() {
        return "AI投研助手。严格返回JSON，无其他文本：\n"
                + "{\"analysis\":\"研判概述(150字内)\","
                + "\"impacts\":[{\"stock\":\"代码\",\"impact\":\"影响\",\"level\":\"重大影响|中等影响|正面影响\",\"volatility\":\"±X%\"}],"
                + "\"suggestion\":\"操作建议\","
                + "\"risks\":[\"风险1\",\"风险2\"],"
                + "\"userContext\":\"基于XX画像\"}";
    }

    String buildUserPrompt(Intelligence intel, UserProfile profile, List<UserHolding> holdings) {
        StringBuilder sb = new StringBuilder();
        sb.append("情报:").append(intel.getTitle());
        if (intel.getSummary() != null && !intel.getSummary().isBlank()) {
            String summary = intel.getSummary().length() > 150
                    ? intel.getSummary().substring(0, 150) + "..." : intel.getSummary();
            sb.append("\n摘要:").append(summary);
        }
        if (intel.getRelatedStocks() != null) sb.append("\n股票:").append(intel.getRelatedStocks());
        if (intel.getSentiment() != null) sb.append(" 情感:").append(intel.getSentiment());

        sb.append("\n画像:").append(profile.getInvestorType())
          .append("/").append(profile.getInvestmentCycle())
          .append(" 关注:").append(profile.getFocusAreas());

        if (holdings != null && !holdings.isEmpty()) {
            sb.append("\n持仓:");
            for (UserHolding h : holdings) {
                sb.append(h.getStockCode());
                if (h.getPercentage() != null) sb.append("(").append(h.getPercentage()).append("%)");
                sb.append(",");
            }
        }
        return sb.toString();
    }

    // ==================== Task 2.4: callLlm & parseLlmResponse ====================

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

        // Cache check: 24 hour validity
        Optional<AnalysisRecord> cached = analysisRecordRepository
                .findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc(userId, articleId);
        if (cached.isPresent() &&
                cached.get().getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
            try {
                return objectMapper.readValue(cached.get().getAnalysisText(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("缓存解析失败，重新生成: {}", e.getMessage());
            }
        }

        // Load profile
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(new UserProfile());
        List<UserHolding> holdings = userHoldingRepository.findByUserId(userId);

        // Build prompts and call LLM
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(intel, profile, holdings);

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
                List<UserHolding> holdings = userHoldingRepository.findByUserId(userId);

                String systemPrompt = buildSystemPrompt();
                String userPrompt = buildUserPrompt(intel, profile, holdings);
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
