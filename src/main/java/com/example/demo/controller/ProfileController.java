package com.example.demo.controller;

import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserProfileRepository userProfileRepository;

    public ProfileController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
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
}
