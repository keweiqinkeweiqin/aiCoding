package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import com.example.demo.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    /**
     * 获取用户画像
     * GET /api/profile
     */
    @GetMapping
    public ResponseEntity<?> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        return ResponseEntity.ok(profileService.getProfile(userOpt.get().getId()));
    }

    /**
     * 保存用户画像
     * PUT /api/profile
     */
    @PutMapping
    public ResponseEntity<?> saveProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        try {
            return ResponseEntity.ok(profileService.saveProfile(userOpt.get().getId(), body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    /**
     * 添加持仓
     * POST /api/profile/holdings
     */
    @PostMapping("/holdings")
    public ResponseEntity<?> addHolding(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        try {
            return ResponseEntity.ok(profileService.addHolding(userOpt.get().getId(), body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    /**
     * 删除持仓
     * DELETE /api/profile/holdings/{stockCode}
     */
    @DeleteMapping("/holdings/{stockCode}")
    public ResponseEntity<?> removeHolding(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String stockCode) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        return ResponseEntity.ok(profileService.removeHolding(userOpt.get().getId(), stockCode));
    }

    /**
     * 获取可选关注领域
     * GET /api/profile/focus-options
     */
    @GetMapping("/focus-options")
    public ResponseEntity<?> getFocusOptions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        return ResponseEntity.ok(profileService.getFocusOptions(userOpt.get().getId()));
    }
}
