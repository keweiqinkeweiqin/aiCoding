package com.example.demo.service;

import com.example.demo.repository.NewsArticleRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DeduplicationService {

    private final NewsArticleRepository newsArticleRepository;

    public DeduplicationService(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    /**
     * 三级去重（Level 4 Embedding去重等key到了再加）
     * @return null=不重复, 否则返回重复原因
     */
    public String checkDuplicate(String title, String url, String content) {
        // Level 1: URL精确匹配
        if (url != null && !url.isBlank() && newsArticleRepository.existsBySourceUrl(url)) {
            return "URL_MATCH";
        }

        // Level 2: 标题Jaccard相似度
        List<String> recentTitles = newsArticleRepository.findTitlesSince(
                LocalDateTime.now().minusHours(48));
        for (String existing : recentTitles) {
            if (jaccardSimilarity(title, existing) > 0.8) {
                return "TITLE_SIMILAR";
            }
        }

        // Level 3: 内容MD5哈希
        String hash = computeContentHash(content);
        if (hash != null && newsArticleRepository.existsByContentHash(hash)) {
            return "CONTENT_HASH";
        }

        return null; // 不重复
    }

    public double jaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        Set<String> bigrams1 = toBigrams(s1);
        Set<String> bigrams2 = toBigrams(s2);
        if (bigrams1.isEmpty() && bigrams2.isEmpty()) return 1.0;

        Set<String> intersection = new HashSet<>(bigrams1);
        intersection.retainAll(bigrams2);

        Set<String> union = new HashSet<>(bigrams1);
        union.addAll(bigrams2);

        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private Set<String> toBigrams(String s) {
        Set<String> bigrams = new HashSet<>();
        String cleaned = s.replaceAll("\\s+", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            bigrams.add(cleaned.substring(i, i + 2));
        }
        return bigrams;
    }

    public String computeContentHash(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            String normalized = content.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
