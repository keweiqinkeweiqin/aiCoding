package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepository;

    // token -> userId 的简易映射（生产环境应使用 JWT 或 Redis）
    private final ConcurrentHashMap<String, Long> tokenStore = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 用户注册
     */
    public Map<String, Object> register(String username, String password, String nickname) {
        if (username == null || username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("用户名长度需在3~50之间");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能小于6位");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = generateToken(user);
        tokenStore.put(token, user.getId());

        return Map.of(
                "userId", user.getId(),
                "token", token,
                "nickname", user.getNickname(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "hasProfile", user.getHasProfile()
        );
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!hashPassword(password).equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = generateToken(user);
        tokenStore.put(token, user.getId());

        return Map.of(
                "userId", user.getId(),
                "token", token,
                "nickname", user.getNickname(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "hasProfile", user.getHasProfile()
        );
    }

    /**
     * 根据 Token 获取当前用户
     */
    public Optional<User> getUserByToken(String token) {
        Long userId = tokenStore.get(token);
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId);
    }

    /**
     * 从 Authorization header 解析 token
     */
    public Optional<User> resolveUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return getUserByToken(authHeader.substring(7));
    }

    /**
     * 生成简易 Token: Base64(userId:timestamp:username)
     */
    private String generateToken(User user) {
        String raw = user.getId() + ":" + System.currentTimeMillis() + ":" + user.getUsername();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 密码哈希（SHA-256，生产环境建议用 BCrypt）
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
}
