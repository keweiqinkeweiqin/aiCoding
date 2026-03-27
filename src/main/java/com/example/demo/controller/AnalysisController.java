package com.example.demo.controller;

import com.example.demo.model.AnalysisRecord;
import com.example.demo.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestBody Map<String, Object> body) {
        if (userId == 0) {
            return ResponseEntity.status(401).body(Map.of("error", "not logged in", "code", 401));
        }
        Long articleId = ((Number) body.get("articleId")).longValue();
        try {
            Map<String, Object> result = analysisService.generateAnalysis(userId, articleId);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestParam Long articleId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        if (userId == 0) {
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"not logged in\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
        emitter.onTimeout(emitter::complete);
        analysisService.streamAnalysis(userId, articleId, emitter);
        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId == 0) {
            return ResponseEntity.status(401).body(Map.of("error", "not logged in", "code", 401));
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
