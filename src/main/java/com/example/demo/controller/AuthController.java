package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String nickname = body.get("nickname");
            var result = authService.register(username, password, nickname);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            var result = authService.login(username, password);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        var userOpt = authService.resolveUser(authHeader);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录或Token无效", "code", 401));
        }
        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "hasProfile", user.getHasProfile(),
                "createdAt", user.getCreatedAt().toString(),
                "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : ""
        ));
    }
}
