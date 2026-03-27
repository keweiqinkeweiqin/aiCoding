package com.example.demo.controller;

import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserProfileRepository userProfileRepository;

    public ProfileController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /** Get profile (use userId=1 as default until auth is implemented) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        var profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            return ResponseEntity.ok(Map.of("code", 200, "data", Map.of()));
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "userId", profile.getUserId(),
                "investorType", profile.getInvestorType() != null ? profile.getInvestorType() : "",
                "investmentCycle", profile.getInvestmentCycle() != null ? profile.getInvestmentCycle() : "",
                "focusAreas", profile.getFocusAreas() != null ? profile.getFocusAreas() : "",
                "holdings", profile.getHoldings() != null ? profile.getHoldings() : ""
        )));
    }

    /** Save or update profile */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody Map<String, String> body) {
        var profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        if (body.containsKey("investorType")) profile.setInvestorType(body.get("investorType"));
        if (body.containsKey("investmentCycle")) profile.setInvestmentCycle(body.get("investmentCycle"));
        if (body.containsKey("focusAreas")) profile.setFocusAreas(body.get("focusAreas"));
        if (body.containsKey("holdings")) profile.setHoldings(body.get("holdings"));
        userProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("code", 200, "message", "saved"));
    }
}
