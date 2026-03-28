package com.example.demo.controller;

import com.example.demo.model.UserHolding;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserHoldingRepository;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
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
        Map<String, Object> data = new LinkedHashMap<>();
        if (profile != null) {
            data.put("investorType", profile.getInvestorType());
            data.put("investmentCycle", profile.getInvestmentCycle());
            data.put("focusAreas", profile.getFocusAreas() != null ? profile.getFocusAreas() : "");
            data.put("holdings", profile.getHoldings() != null ? profile.getHoldings() : "");
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    /** PUT /api/profile?userId=1 */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, Object> body) {
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        if (body.containsKey("investorType")) profile.setInvestorType((String) body.get("investorType"));
        if (body.containsKey("investmentCycle")) profile.setInvestmentCycle((String) body.get("investmentCycle"));
        if (body.containsKey("focusAreas")) {
            Object fa = body.get("focusAreas");
            if (fa instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> list = (java.util.List<String>) fa;
                profile.setFocusAreas(String.join(",", list));
            } else {
                profile.setFocusAreas(String.valueOf(fa));
            }
        }
        if (body.containsKey("holdings")) profile.setHoldings(String.valueOf(body.get("holdings")));
        userProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("code", 200, "message", "saved"));
    }

    /** GET /api/profile/full?userId=1 — C端完整画像查询 */
    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> getFullProfile(
            @RequestParam(defaultValue = "0") Long userId) {
        if (userId == 0) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        var profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null) {
            data.put("investorType", profile.getInvestorType());
            data.put("investmentCycle", profile.getInvestmentCycle());
            data.put("focusAreas", profile.getFocusAreas() != null
                    ? List.of(profile.getFocusAreas().split(",")) : List.of());
            data.put("holdings", profile.getHoldings() != null ? profile.getHoldings() : "");
        } else {
            data.put("investorType", null);
            data.put("investmentCycle", null);
            data.put("focusAreas", List.of());
            data.put("holdings", "");
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    // ========== Holdings CRUD ==========

    /** GET /api/profile/holdings?userId=1 */
    @GetMapping("/holdings")
    public ResponseEntity<Map<String, Object>> getHoldings(
            @RequestParam(defaultValue = "1") Long userId) {
        List<UserHolding> holdings = userHoldingRepository.findByUserId(userId);
        List<Map<String, Object>> list = holdings.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("stockCode", h.getStockCode());
            m.put("stockName", h.getStockName());
            m.put("sector", h.getSector());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("code", 200, "data", list));
    }

    /** POST /api/profile/holdings?userId=1 */
    @PostMapping("/holdings")
    public ResponseEntity<Map<String, Object>> addHolding(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, String> body) {
        String stockCode = body.get("stockCode");
        String stockName = body.get("stockName");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "error", "stockCode required"));
        }
        if (stockName == null || stockName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "error", "stockName required"));
        }
        UserHolding holding = new UserHolding();
        holding.setUserId(userId);
        holding.setStockCode(stockCode.trim().toUpperCase());
        holding.setStockName(stockName.trim());
        holding.setSector(body.getOrDefault("sector", ""));
        userHoldingRepository.save(holding);

        // Sync to UserProfile.holdings field
        syncHoldingsToProfile(userId);

        return ResponseEntity.ok(Map.of("code", 200, "message", "added", "id", holding.getId()));
    }

    /** DELETE /api/profile/holdings/{id} */
    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Map<String, Object>> deleteHolding(@PathVariable Long id) {
        var holding = userHoldingRepository.findById(id);
        if (holding.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "error", "not found"));
        }
        Long userId = holding.get().getUserId();
        userHoldingRepository.deleteById(id);

        // Sync to UserProfile.holdings field
        syncHoldingsToProfile(userId);

        return ResponseEntity.ok(Map.of("code", 200, "message", "deleted"));
    }

    /** Keep UserProfile.holdings in sync with user_holdings table */
    private void syncHoldingsToProfile(Long userId) {
        List<UserHolding> all = userHoldingRepository.findByUserId(userId);
        String holdingsStr = all.stream()
                .map(UserHolding::getStockCode)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        profile.setHoldings(holdingsStr);
        userProfileRepository.save(profile);
    }
}
