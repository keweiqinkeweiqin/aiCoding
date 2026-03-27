package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auth: phone login, no password.
 * Auto-register if phone not exists.
 * Just stores user info + profile.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Login by phone (auto-register if new)
     * POST /api/auth/login  {"phone":"13800138000"}
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "phone required"));
        }

        var existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                    "userId", user.getId(),
                    "phone", user.getPhone(),
                    "nickname", user.getNickname() != null ? user.getNickname() : "",
                    "isNew", false)));
        }

        // Auto-register
        User user = new User();
        user.setPhone(phone);
        user.setNickname(body.getOrDefault("nickname", "user_" + phone.substring(phone.length() - 4)));
        user.setLastLoginAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "userId", saved.getId(),
                "phone", saved.getPhone(),
                "nickname", saved.getNickname() != null ? saved.getNickname() : "",
                "isNew", true)));
    }

    /** Update nickname */
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMe(
            @RequestParam(defaultValue = "0") Long userId,
            @RequestBody Map<String, String> body) {
        var user = userRepository.findById(userId);
        if (user.isEmpty()) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "user not found"));
        User u = user.get();
        if (body.containsKey("nickname")) u.setNickname(body.get("nickname"));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("code", 200, "message", "updated"));
    }

    /** Get current user */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId == 0) return ResponseEntity.status(401).body(Map.of("code", 401, "message", "not logged in"));
        var user = userRepository.findById(userId);
        if (user.isEmpty()) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "user not found"));
        User u = user.get();
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "userId", u.getId(),
                "phone", u.getPhone(),
                "nickname", u.getNickname() != null ? u.getNickname() : "")));
    }
}
