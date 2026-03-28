package com.example.demo.controller;

import com.example.demo.model.Intelligence;
import com.example.demo.model.MarketData;
import com.example.demo.model.User;
import com.example.demo.model.UserProfile;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.MarketDataRepository;
import com.example.demo.repository.UserProfileRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final IntelligenceRepository intelligenceRepository;
    private final MarketDataRepository marketDataRepository;

    public HomeController(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          IntelligenceRepository intelligenceRepository,
                          MarketDataRepository marketDataRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.intelligenceRepository = intelligenceRepository;
        this.marketDataRepository = marketDataRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> home(
            @RequestParam(defaultValue = "0") Long userId) {

        Map<String, Object> data = new LinkedHashMap<>();

        // 1. Greeting
        data.put("greeting", buildGreeting(userId));

        // 2. Quick actions
        data.put("quickActions", List.of(
                Map.of("id", "collect", "name", "采集新闻", "icon", "refresh"),
                Map.of("id", "query", "name", "智能问答", "icon", "chat"),
                Map.of("id", "market", "name", "行情数据", "icon", "chart"),
                Map.of("id", "profile", "name", "我的画像", "icon", "user")
        ));

        // 3. Market overview
        data.put("marketOverview", buildMarketOverview());

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    private Map<String, Object> buildGreeting(Long userId) {
        Map<String, Object> greeting = new LinkedHashMap<>();
        String nickname = "访客";
        String profileTag = "";

        if (userId > 0) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) nickname = user.getNickname() != null ? user.getNickname() : "用户";

            UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                List<String> tags = new ArrayList<>();
                if (profile.getInvestorType() != null) {
                    String type = profile.getInvestorType();
                    if ("growth".equals(type)) tags.add("成长型");
                    else if ("balanced".equals(type)) tags.add("均衡型");
                    else if ("conservative".equals(type)) tags.add("保守型");
                }
                if (profile.getFocusAreas() != null && !profile.getFocusAreas().isBlank()) {
                    String[] areas = profile.getFocusAreas().split(",");
                    if (areas.length > 0) tags.add(areas[0].trim());
                    if (areas.length > 1) tags.add(areas[1].trim());
                }
                profileTag = String.join(" | ", tags);
            }
        }

        greeting.put("nickname", nickname);
        greeting.put("profileTag", profileTag);

        // Market status one-liner from recent intelligences
        List<Intelligence> recent = intelligenceRepository
                .findByCreatedAtAfterOrderByLatestArticleTimeDesc(LocalDateTime.now().minusHours(24));
        long pos = recent.stream().filter(i -> "positive".equals(i.getSentiment())).count();
        long neg = recent.stream().filter(i -> "negative".equals(i.getSentiment())).count();
        long total = recent.size();
        String status;
        if (total == 0) {
            status = "暂无最新数据";
        } else if (pos > neg * 2) {
            status = "市场情绪偏多，已追踪" + total + "条情报";
        } else if (neg > pos * 2) {
            status = "市场情绪偏空，注意风险";
        } else {
            status = "市场情绪中性，已追踪" + total + "条情报";
        }
        greeting.put("marketStatus", status);

        return greeting;
    }

    private Map<String, Object> buildMarketOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // Sentiment index from recent 24h intelligences
        List<Intelligence> recent = intelligenceRepository
                .findByCreatedAtAfterOrderByLatestArticleTimeDesc(LocalDateTime.now().minusHours(24));
        long total = recent.size();
        long positive = recent.stream().filter(i -> "positive".equals(i.getSentiment())).count();
        long negative = recent.stream().filter(i -> "negative".equals(i.getSentiment())).count();

        int sentimentIndex = total > 0 ? (int) Math.round((double) positive / total * 100) : 50;
        String sentimentLabel;
        if (sentimentIndex >= 70) sentimentLabel = "偏多";
        else if (sentimentIndex >= 40) sentimentLabel = "中性";
        else sentimentLabel = "偏空";

        overview.put("sentimentIndex", sentimentIndex);
        overview.put("sentimentLabel", sentimentLabel);
        overview.put("totalIntelligences", total);
        overview.put("positiveCount", positive);
        overview.put("negativeCount", negative);
        overview.put("neutralCount", total - positive - negative);

        // Market data summary
        List<MarketData> allMarket = marketDataRepository.findAll();
        if (!allMarket.isEmpty()) {
            long up = allMarket.stream().filter(m -> m.getChangePercent() != null && m.getChangePercent() > 0).count();
            long down = allMarket.stream().filter(m -> m.getChangePercent() != null && m.getChangePercent() < 0).count();
            double avgChange = allMarket.stream()
                    .filter(m -> m.getChangePercent() != null)
                    .mapToDouble(MarketData::getChangePercent)
                    .average().orElse(0);
            overview.put("stockUp", up);
            overview.put("stockDown", down);
            overview.put("stockFlat", allMarket.size() - up - down);
            overview.put("avgChangePercent", Math.round(avgChange * 100) / 100.0);
        } else {
            overview.put("stockUp", 0);
            overview.put("stockDown", 0);
            overview.put("stockFlat", 0);
            overview.put("avgChangePercent", 0);
        }

        // Top tags from recent intelligences
        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        for (Intelligence intel : recent) {
            if (intel.getTags() != null) {
                for (String tag : intel.getTags().split(",")) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) tagCounts.merge(tag, 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> hotTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("tag", e.getKey(), "count", e.getValue()))
                .toList();
        overview.put("hotTags", hotTags);

        return overview;
    }
}
