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

    /** PUT /api/profile/save — 保存完整画像（含持仓） */
    @PutMapping("/save")
    public ResponseEntity<Map<String, Object>> saveProfile(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody Map<String, Object> body) {

        UserProfile profile = userProfileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });

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
        if (body.containsKey("holdings")) {
            @SuppressWarnings("unchecked")
            List<String> holdingList = (List<String>) body.get("holdings");
            profile.setHoldings(holdingList != null ? String.join(",", holdingList) : "");
        }

        userProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("code", 200, "message", "saved"));
    }
}
