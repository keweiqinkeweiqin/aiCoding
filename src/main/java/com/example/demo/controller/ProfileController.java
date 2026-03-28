package com.example.demo.controller;

import com.example.demo.model.UserHolding;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserHoldingRepository;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * GET /api/profile?userId=1
     * 返回完整画像，focusAreas 为字符串数组，holdings 为对象数组
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId == 0) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        var profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            data.put("investorType", profile.getInvestorType());
            data.put("investmentCycle", profile.getInvestmentCycle());
            data.put("focusAreas", splitToList(profile.getFocusAreas()));
        } else {
            data.put("investorType", null);
            data.put("investmentCycle", null);
            data.put("focusAreas", List.of());
        }
        // 持仓从 user_holdings 表读取
        data.put("holdings", buildHoldingsList(userId));
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    /**
     * PUT /api/profile?userId=1
     * 保存画像，focusAreas 接受字符串数组或逗号分隔字符串，
     * holdings 接受对象数组 [{stockCode, stockName, sector}]
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, Object> body) {
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });

        if (body.containsKey("investorType"))
            profile.setInvestorType((String) body.get("investorType"));
        if (body.containsKey("investmentCycle"))
            profile.setInvestmentCycle((String) body.get("investmentCycle"));

        // focusAreas: 接受 ["AI","chip"] 或 "AI,chip"
        if (body.containsKey("focusAreas")) {
            Object fa = body.get("focusAreas");
            if (fa instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) fa;
                profile.setFocusAreas(String.join(",", list));
            } else {
                profile.setFocusAreas(String.valueOf(fa));
            }
        }

        // holdings: 接受 [{stockCode, stockName, sector}] 数组，全量替换
        if (body.containsKey("holdings")) {
            Object h = body.get("holdings");
            if (h instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> holdingList = (List<Map<String, String>>) h;
                // 删除旧持仓
                userHoldingRepository.findByUserId(userId).forEach(
                        old -> userHoldingRepository.deleteById(old.getId()));
                // 写入新持仓
                List<String> codes = new ArrayList<>();
                for (Map<String, String> item : holdingList) {
                    String code = item.getOrDefault("stockCode", "").trim().toUpperCase();
                    String name = item.getOrDefault("stockName", "").trim();
                    if (code.isEmpty()) continue;
                    UserHolding uh = new UserHolding();
                    uh.setUserId(userId);
                    uh.setStockCode(code);
                    uh.setStockName(name);
                    uh.setSector(item.getOrDefault("sector", ""));
                    if (item.containsKey("percentage")) {
                        try { uh.setPercentage(Double.parseDouble(item.get("percentage"))); } catch (Exception ignored) {}
                    }
                    if (item.containsKey("costPrice")) {
                        try { uh.setCostPrice(Double.parseDouble(item.get("costPrice"))); } catch (Exception ignored) {}
                    }
                    userHoldingRepository.save(uh);
                    codes.add(code);
                }
                profile.setHoldings(String.join(",", codes));
            }
            // 如果传的是字符串，兼容旧格式
            else if (h instanceof String) {
                profile.setHoldings((String) h);
            }
        }

        userProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("code", 200, "message", "saved"));
    }

    /**
     * GET /api/profile/full?userId=1 — 同 GET /api/profile，保持兼容
     */
    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> getFullProfile(
            @RequestParam(defaultValue = "0") Long userId) {
        return getProfile(userId);
    }

    // ========== Holdings 单条操作（控制面板用） ==========

    /** GET /api/profile/holdings?userId=1 */
    @GetMapping("/holdings")
    public ResponseEntity<Map<String, Object>> getHoldings(
            @RequestParam(defaultValue = "1") Long userId) {
        return ResponseEntity.ok(Map.of("code", 200, "data", buildHoldingsList(userId)));
    }

    /** POST /api/profile/holdings?userId=1 */
    @PostMapping("/holdings")
    public ResponseEntity<Map<String, Object>> addHolding(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, String> body) {
        String stockCode = body.get("stockCode");
        String stockName = body.get("stockName");
        if (stockCode == null || stockCode.isBlank())
            return ResponseEntity.badRequest().body(Map.of("code", 400, "error", "stockCode required"));
        if (stockName == null || stockName.isBlank())
            return ResponseEntity.badRequest().body(Map.of("code", 400, "error", "stockName required"));

        UserHolding holding = new UserHolding();
        holding.setUserId(userId);
        holding.setStockCode(stockCode.trim().toUpperCase());
        holding.setStockName(stockName.trim());
        holding.setSector(body.getOrDefault("sector", ""));
        if (body.containsKey("percentage")) {
            try { holding.setPercentage(Double.parseDouble(body.get("percentage"))); } catch (Exception ignored) {}
        }
        if (body.containsKey("costPrice")) {
            try { holding.setCostPrice(Double.parseDouble(body.get("costPrice"))); } catch (Exception ignored) {}
        }
        userHoldingRepository.save(holding);
        syncHoldingsToProfile(userId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "added", "id", holding.getId()));
    }

    /** DELETE /api/profile/holdings/{id} */
    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Map<String, Object>> deleteHolding(@PathVariable Long id) {
        var holding = userHoldingRepository.findById(id);
        if (holding.isEmpty())
            return ResponseEntity.status(404).body(Map.of("code", 404, "error", "not found"));
        Long userId = holding.get().getUserId();
        userHoldingRepository.deleteById(id);
        syncHoldingsToProfile(userId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "deleted"));
    }

    // ========== 内部方法 ==========

    private List<String> splitToList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildHoldingsList(Long userId) {
        return userHoldingRepository.findByUserId(userId).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            m.put("percentage", h.getPercentage());
            m.put("costPrice", h.getCostPrice());
            return m;
        }).collect(Collectors.toList());
    }

    private void syncHoldingsToProfile(Long userId) {
        List<UserHolding> all = userHoldingRepository.findByUserId(userId);
        String holdingsStr = all.stream()
                .map(UserHolding::getStockCode)
                .reduce((a, b) -> a + "," + b).orElse("");
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        profile.setHoldings(holdingsStr);
        userProfileRepository.save(profile);
    }
}
