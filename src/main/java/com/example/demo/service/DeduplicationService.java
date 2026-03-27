package com.example.demo.service;

import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 四级去重服务（业界标准方案）
 * Level 1: URL精确匹配
 * Level 2: SimHash标题指纹（汉明距离≤3判重，Google网页去重算法）
 * Level 3: 内容MD5哈希
 * Level 4: SimHash内容指纹（捕获改写/转述的重复）
 */
@Service
public class DeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationService.class);
    private final NewsArticleRepository newsArticleRepository;

    // 内存SimHash指纹缓存，避免每次查库
    private final ConcurrentHashMap<Long, Long> titleFingerprints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> contentFingerprints = new ConcurrentHashMap<>();
    private long nextId = 1;

    public DeduplicationService(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    /**
     * 四级去重检查
     * @return null=不重复, 否则返回重复原因
     */
    public String checkDuplicate(String title, String url, String content) {
        // Level 1: URL精确匹配（最快）
        if (url != null && !url.isBlank() && newsArticleRepository.existsBySourceUrl(url)) {
            return "URL_MATCH";
        }

        // Level 2: SimHash标题指纹（汉明距离≤3）
        long titleHash = simHash(title);
        for (var entry : titleFingerprints.entrySet()) {
            if (hammingDistance(titleHash, entry.getValue()) <= 3) {
                log.debug("标题SimHash去重命中: {}", title);
                return "TITLE_SIMHASH";
            }
        }

        // Level 3: 内容MD5精确哈希
        String md5 = computeContentHash(content);
        if (md5 != null && newsArticleRepository.existsByContentHash(md5)) {
            return "CONTENT_HASH";
        }

        // Level 4: SimHash内容指纹（捕获改写/转述）
        if (content != null && content.length() > 50) {
            long contentHash = simHash(content);
            for (var entry : contentFingerprints.entrySet()) {
                if (hammingDistance(contentHash, entry.getValue()) <= 5) {
                    log.debug("内容SimHash去重命中: {}", title);
                    return "CONTENT_SIMHASH";
                }
            }
        }

        // 通过去重，缓存指纹
        long id = nextId++;
        titleFingerprints.put(id, titleHash);
        if (content != null && content.length() > 50) {
            contentFingerprints.put(id, simHash(content));
        }

        return null;
    }

    /**
     * SimHash算法：文本 → 64位指纹
     * 1. 分词（中文按字符bigram，英文按空格）
     * 2. 每个词做hash得到64位值
     * 3. 加权合并：词出现则对应位+1，否则-1
     * 4. 最终每位>0取1，否则取0
     */
    public long simHash(String text) {
        if (text == null || text.isBlank()) return 0;

        int[] bits = new int[64];
        String cleaned = text.replaceAll("\\s+", "");

        // 中文bigram分词 + 英文单词
        for (int i = 0; i < cleaned.length() - 1; i++) {
            String token = cleaned.substring(i, i + 2);
            long hash = murmurHash64(token);
            for (int j = 0; j < 64; j++) {
                if (((hash >> j) & 1) == 1) {
                    bits[j]++;
                } else {
                    bits[j]--;
                }
            }
        }

        long fingerprint = 0;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /** 汉明距离：两个64位指纹有多少位不同 */
    public int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** MurmurHash64 简化实现 */
    private long murmurHash64(String key) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= b;
            h *= 0x100000001b3L;
        }
        return h;
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
