package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auth: phone + password login.
 * If phone not registered, auto-register on first login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Login (auto-register if phone not exists)
     * POST /api/auth/login
     * Body: {"phone":"13800138000","password":"xxx"}
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");

        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "phone and password required"));
        }

        String hash = hashPassword(password);
        var existing = userRepository.findByPhone(phone);

        if (existing.isPresent()) {
            // Login: verify password
            User user = existing.get();
            if (!hash.equals(user.getPasswordHash())) {
                return ResponseEntity.status(401).body(Map.of(
                        "code", 401, "message", "wrong password"));
            }
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "login success",
                    "data", Map.of(
                            "userId", user.getId(),
                            "phone", user.getPhone(),
                            "nickname", user.getNickname() != null ? user.getNickname() : "",
                            "isNew", false
                    )
            ));
        } else {
            // Auto-register
            User user = new User();
            user.setPhone(phone);
            user.setPasswordHash(hash);
            user.setNickname("user_" + phone.substring(phone.length() - 4));
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "register and login success",
                    "data", Map.of(
                            "userId", user.getId(),
                            "phone", user.getPhone(),
                            "nickname", user.getNickname(),
                            "isNew", true
                    )
            ));
        }
    }

    /** Get current user info */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        if (userId == 0) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "not logged in"));
        }
        var user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "user not found"));
        }
        User u = user.get();
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "userId", u.getId(),
                "phone", u.getPhone(),
                "nickname", u.getNickname() != null ? u.getNickname() : ""
        )));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("hash failed", e);
        }
    }
}
