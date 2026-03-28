package com.example.demo.controller;

import com.example.demo.model.UserHolding;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserHoldingRepository;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserProfileRepository userProfileRepository;
    private final UserHoldingRepository userHoldingRepository;

    public ProfileController(UserProfileRepository userProfileRepository,
                             UserHoldingRepository userHoldingRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userHoldingRepository = userHoldingRepository;
    }

    /** GET /api/profile?userId=1 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestParam(defaultValue = "1") Long userId) {
        var profile = userProfileRepository.findByUserId(userId).orElse(null);
        var holdings = userHoldingRepository.findByUserId(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (profile != null) {
            data.put("investorType", profile.getInvestorType());
            data.put("investmentCycle", profile.getInvestmentCycle());
            data.put("focusAreas", profile.getFocusAreas() != null ? profile.getFocusAreas() : "");
        }
        data.put("holdings", holdings.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            return m;
        }).toList());
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    /** PUT /api/profile?userId=1 */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, String> body) {
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        if (body.containsKey("investorType")) profile.setInvestorType(body.get("investorType"));
        if (body.containsKey("investmentCycle")) profile.setInvestmentCycle(body.get("investmentCycle"));
        if (body.containsKey("focusAreas")) profile.setFocusAreas(body.get("focusAreas"));
        userProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("code", 200, "message", "saved"));
    }

    /** POST /api/profile/holdings?userId=1 */
    @PostMapping("/holdings")
    public ResponseEntity<Map<String, Object>> addHolding(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, String> body) {
        String rawCode = body.get("stockCode");
        if (rawCode == null || rawCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "stockCode required"));
        }
        final String code = rawCode.toUpperCase().trim();
        var existing = userHoldingRepository.findByUserIdAndStockCode(userId, code);
        UserHolding h = existing.orElseGet(() -> {
            UserHolding nh = new UserHolding();
            nh.setUserId(userId);
            nh.setStockCode(code);
            return nh;
        });
        h.setStockName(body.getOrDefault("stockName", code));
        h.setSector(body.get("sector"));
        userHoldingRepository.save(h);
        return ResponseEntity.ok(Map.of("code", 200, "message", "added", "data", Map.of("id", h.getId())));
    }

    /** DELETE /api/profile/holdings/{id} */
    @DeleteMapping("/holdings/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeHolding(@PathVariable Long id) {
        userHoldingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "deleted"));
    }

    /** GET /api/profile/holdings?userId=1 */
    @GetMapping("/holdings")
    public ResponseEntity<Map<String, Object>> listHoldings(
            @RequestParam(defaultValue = "1") Long userId) {
        var holdings = userHoldingRepository.findByUserId(userId);
        var list = holdings.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("code", 200, "data", list));
    }

    /** GET /api/profile/full?userId=1 — C端完整画像查询（用户信息+画像+持仓） */
    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> getFullProfile(
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId == 0) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of()));
        }
        Map<String, Object> data = new LinkedHashMap<>();

        // 画像
        var profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            data.put("investorType", profile.getInvestorType());
            data.put("investmentCycle", profile.getInvestmentCycle());
            data.put("focusAreas", profile.getFocusAreas() != null
                    ? List.of(profile.getFocusAreas().split(",")) : List.of());
        } else {
            data.put("investorType", null);
            data.put("investmentCycle", null);
            data.put("focusAreas", List.of());
        }

        // 持仓
        var holdings = userHoldingRepository.findByUserId(userId);
        data.put("holdings", holdings.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            return m;
        }).toList());

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }
}
