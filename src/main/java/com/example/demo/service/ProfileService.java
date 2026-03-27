package com.example.demo.service;

import com.example.demo.model.UserHolding;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserHoldingRepository;
import com.example.demo.repository.UserProfileRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProfileService {

    private final UserProfileRepository profileRepository;
    private final UserHoldingRepository holdingRepository;
    private final UserRepository userRepository;

    // 预定义的关注领域选项
    private static final List<Map<String, String>> FOCUS_OPTIONS = List.of(
            Map.of("id", "ai_chip", "name", "AI芯片"),
            Map.of("id", "cloud", "name", "云计算"),
            Map.of("id", "semiconductor", "name", "半导体"),
            Map.of("id", "llm", "name", "大模型"),
            Map.of("id", "aigc", "name", "AIGC应用"),
            Map.of("id", "autonomous", "name", "自动驾驶"),
            Map.of("id", "robot", "name", "机器人"),
            Map.of("id", "quantum", "name", "量子计算"),
            Map.of("id", "biotech", "name", "生物科技"),
            Map.of("id", "new_energy", "name", "新能源")
    );

    public ProfileService(UserProfileRepository profileRepository,
                           UserHoldingRepository holdingRepository,
                           UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
    }

    /**
     * 获取用户画像
     */
    public Map<String, Object> getProfile(Long userId) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);
        List<UserHolding> holdings = holdingRepository.findByUserId(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        if (profile != null) {
            result.put("investorType", profile.getInvestorType());
            result.put("investmentCycle", profile.getInvestmentCycle());
            result.put("focusAreas", profile.getFocusAreas() != null
                    ? Arrays.asList(profile.getFocusAreas().split(",")) : List.of());
        } else {
            result.put("investorType", null);
            result.put("investmentCycle", null);
            result.put("focusAreas", List.of());
        }

        List<Map<String, Object>> holdingList = holdings.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            m.put("positionRatio", h.getPositionRatio());
            m.put("costPrice", h.getCostPrice());
            return m;
        }).toList();
        result.put("holdings", holdingList);

        return Map.of("code", 200, "data", result);
    }

    /**
     * 保存用户画像
     */
    @Transactional
    public Map<String, Object> saveProfile(Long userId, Map<String, Object> body) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(new UserProfile());
        profile.setUserId(userId);

        if (body.containsKey("investorType")) {
            profile.setInvestorType((String) body.get("investorType"));
        }
        if (body.containsKey("investmentCycle")) {
            profile.setInvestmentCycle((String) body.get("investmentCycle"));
        }
        if (body.containsKey("focusAreas")) {
            @SuppressWarnings("unchecked")
            List<String> areas = (List<String>) body.get("focusAreas");
            profile.setFocusAreas(areas != null ? String.join(",", areas) : "");
        }
        profileRepository.save(profile);

        // 更新 user.hasProfile
        userRepository.findById(userId).ifPresent(user -> {
            user.setHasProfile(true);
            userRepository.save(user);
        });

        return Map.of("code", 200, "message", "画像保存成功");
    }

    /**
     * 添加持仓
     */
    @Transactional
    public Map<String, Object> addHolding(Long userId, Map<String, Object> body) {
        String stockCode = (String) body.get("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        stockCode = stockCode.toUpperCase().trim();

        // 检查是否已存在
        Optional<UserHolding> existing = holdingRepository.findByUserIdAndStockCode(userId, stockCode);
        UserHolding holding;
        if (existing.isPresent()) {
            holding = existing.get();
        } else {
            holding = new UserHolding();
            holding.setUserId(userId);
            holding.setStockCode(stockCode);
        }
        holding.setStockName((String) body.getOrDefault("stockName", stockCode));
        holding.setSector((String) body.get("sector"));
        if (body.get("positionRatio") != null) {
            holding.setPositionRatio(((Number) body.get("positionRatio")).doubleValue());
        }
        if (body.get("costPrice") != null) {
            holding.setCostPrice(((Number) body.get("costPrice")).doubleValue());
        }
        holdingRepository.save(holding);

        return Map.of("code", 200, "message", "持仓添加成功");
    }

    /**
     * 删除持仓
     */
    @Transactional
    public Map<String, Object> removeHolding(Long userId, String stockCode) {
        holdingRepository.deleteByUserIdAndStockCode(userId, stockCode.toUpperCase().trim());
        return Map.of("code", 200, "message", "持仓删除成功");
    }

    /**
     * 获取可选关注领域
     */
    public Map<String, Object> getFocusOptions(Long userId) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);
        Set<String> selected = new HashSet<>();
        if (profile != null && profile.getFocusAreas() != null) {
            selected.addAll(Arrays.asList(profile.getFocusAreas().split(",")));
        }

        List<Map<String, Object>> options = FOCUS_OPTIONS.stream().map(opt -> {
            Map<String, Object> m = new LinkedHashMap<>(opt);
            m.put("selected", selected.contains(opt.get("name")));
            return m;
        }).toList();

        return Map.of("code", 200, "data", options);
    }
}
