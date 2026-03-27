package com.example.demo.service;

import com.example.demo.model.UserProfile;
import com.example.demo.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProfileService {

    private final UserProfileRepository profileRepository;

    public ProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * 获取用户画像（含持仓）
     */
    public Map<String, Object> getProfile(Long userId) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (profile != null) {
            result.put("investorType", profile.getInvestorType());
            result.put("investmentCycle", profile.getInvestmentCycle());
            result.put("focusAreas", profile.getFocusAreas() != null
                    ? Arrays.asList(profile.getFocusAreas().split(",")) : List.of());
            result.put("holdings", profile.getHoldings() != null
                    ? Arrays.asList(profile.getHoldings().split(",")) : List.of());
        } else {
            result.put("investorType", null);
            result.put("investmentCycle", null);
            result.put("focusAreas", List.of());
            result.put("holdings", List.of());
        }

        return Map.of("code", 200, "data", result);
    }
}
