package com.example.demo.controller;

import com.example.demo.model.AnalysisRecord;
import com.example.demo.service.AnalysisService;
import com.example.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AuthService authService;

    public AnalysisController(AnalysisService analysisService, AuthService authService) {
        this.analysisService = analysisService;
        this.authService = authService;
    }

    /** POST /api/analysis/generate — 同步生成 AI 研判 */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录", "code", 401));
        }
        Long userId = ((Number) body.get("userId")).longValue();
        Long articleId = ((Number) body.get("articleId")).longValue();
        try {
            Map<String, Object> result = analysisService.generateAnalysis(userId, articleId);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    /** GET /api/analysis/stream — SSE 流式生成 AI 研判 */
    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam Long userId,
            @RequestParam Long articleId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"未登录\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
        emitter.onTimeout(emitter::complete);
        analysisService.streamAnalysis(userId, articleId, emitter);
        return emitter;
    }

    /** GET /api/analysis/history — 研判历史记录 */
    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam Long userId) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录", "code", 401));
        }
        List<AnalysisRecord> records = analysisService.getHistory(userId);
        var result = records.stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("userId", r.getUserId());
            item.put("newsArticleId", r.getNewsArticleId());
            item.put("analysisText", r.getAnalysisText());
            item.put("investmentStyle", r.getInvestmentStyle());
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
