package com.example.demo.service;

import com.example.demo.model.Intelligence;
import com.example.demo.model.IntelligenceArticle;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.IntelligenceArticleRepository;
import com.example.demo.repository.IntelligenceRepository;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceService.class);

    private final IntelligenceRepository intelligenceRepository;
    private final IntelligenceArticleRepository intelligenceArticleRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final ChatClient chatClient;

    public IntelligenceService(IntelligenceRepository intelligenceRepository,
                               IntelligenceArticleRepository intelligenceArticleRepository,
                               NewsArticleRepository newsArticleRepository,
                               ChatClient.Builder chatClientBuilder) {
        this.intelligenceRepository = intelligenceRepository;
        this.intelligenceArticleRepository = intelligenceArticleRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.chatClient = chatClientBuilder.build();
    }

    /** 分页查询情报列表 */
    public Page<Intelligence> listIntelligences(int hours, int page, int size) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        // 优先按 latestArticleTime 过滤，fallback 到 createdAt
        Page<Intelligence> result = intelligenceRepository
                .findByLatestArticleTimeAfterOrderByLatestArticleTimeDesc(since, PageRequest.of(page, size));
        if (result.isEmpty()) {
            result = intelligenceRepository
                    .findByCreatedAtAfterOrderByLatestArticleTimeDesc(since, PageRequest.of(page, size));
        }
        return result;
    }

    /**
     * 批量并发生成缺失 content 的情报正文。
     * 用于控制面板手动触发，避免 C 端用户首次访问时长时间等待 LLM。
     * @param concurrency 并发线程数
     * @return 生成结果统计
     */
    public BatchGenerateResult batchGenerateContent(int concurrency) {
        List<Intelligence> all = intelligenceRepository.findAll();
        List<Intelligence> needContent = all.stream()
                .filter(i -> i.getContent() == null || i.getContent().isBlank())
                .toList();

        if (needContent.isEmpty()) {
            return new BatchGenerateResult(0, 0, 0);
        }

        log.info("批量生成情报正文: 共 {} 条待生成, 并发={}", needContent.size(), concurrency);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger();

        List<java.util.concurrent.CompletableFuture<Void>> futures = needContent.stream()
                .map(intel -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        getDetail(intel.getId());
                        success.incrementAndGet();
                        log.debug("批量生成完成: intel={}", intel.getId());
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        log.warn("批量生成失败: intel={}, error={}", intel.getId(), e.getMessage());
                    }
                }, pool))
                .toList();

        // 等待全部完成
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        pool.shutdown();

        log.info("批量生成情报正文完成: total={}, success={}, failed={}", needContent.size(), success.get(), failed.get());
        return new BatchGenerateResult(needContent.size(), success.get(), failed.get());
    }

    public record BatchGenerateResult(int total, int success, int failed) {}

    /**
     * 批量生成正文（SSE 流式进度推送）。
     * 事件: start → progress(每条) → done
     */
    public void batchGenerateContentStream(int concurrency, SseEmitter emitter) {
        try {
            List<Intelligence> all = intelligenceRepository.findAll();
            List<Intelligence> needContent = all.stream()
                    .filter(i -> i.getContent() == null || i.getContent().isBlank())
                    .toList();

            // start 事件：告知总数
            emitter.send(SseEmitter.event().name("start")
                    .data("{\"total\":" + needContent.size() + ",\"concurrency\":" + concurrency + "}"));

            if (needContent.isEmpty()) {
                emitter.send(SseEmitter.event().name("done")
                        .data("{\"total\":0,\"success\":0,\"failed\":0}"));
                emitter.complete();
                return;
            }

            var pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
            var success = new java.util.concurrent.atomic.AtomicInteger();
            var failed = new java.util.concurrent.atomic.AtomicInteger();
            var completed = new java.util.concurrent.atomic.AtomicInteger();

            var futures = needContent.stream()
                    .map(intel -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                        long t0 = System.currentTimeMillis();
                        boolean ok = false;
                        String errMsg = null;
                        try {
                            getDetail(intel.getId());
                            success.incrementAndGet();
                            ok = true;
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            errMsg = e.getMessage();
                        }
                        int done = completed.incrementAndGet();
                        long elapsed = System.currentTimeMillis() - t0;
                        try {
                            String json = "{\"current\":" + done
                                    + ",\"total\":" + needContent.size()
                                    + ",\"success\":" + success.get()
                                    + ",\"failed\":" + failed.get()
                                    + ",\"intelId\":" + intel.getId()
                                    + ",\"title\":" + escapeJson(intel.getTitle())
                                    + ",\"ok\":" + ok
                                    + ",\"ms\":" + elapsed
                                    + (errMsg != null ? ",\"error\":" + escapeJson(errMsg) : "")
                                    + "}";
                            synchronized (emitter) {
                                emitter.send(SseEmitter.event().name("progress").data(json));
                            }
                        } catch (Exception ignored) {}
                    }, pool))
                    .toList();

            java.util.concurrent.CompletableFuture.allOf(
                    futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            pool.shutdown();

            emitter.send(SseEmitter.event().name("done")
                    .data("{\"total\":" + needContent.size()
                            + ",\"success\":" + success.get()
                            + ",\"failed\":" + failed.get() + "}"));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    /** 清空所有情报的 content 缓存 */
    public int clearAllContent() {
        List<Intelligence> all = intelligenceRepository.findAll();
        int count = 0;
        for (Intelligence intel : all) {
            if (intel.getContent() != null && !intel.getContent().isBlank()) {
                intel.setContent(null);
                intelligenceRepository.save(intel);
                count++;
            }
        }
        log.info("Cleared content for {} intelligences", count);
        return count;
    }

    /** 获取情报详情，包含关联的所有原始新闻 */
    public IntelligenceDetail getDetail(Long id) {
        Intelligence intel = intelligenceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Intelligence not found: " + id));

        // 获取关联的新闻列表
        List<IntelligenceArticle> myLinks = intelligenceArticleRepository
                .findByIntelligenceIdOrderByIsPrimaryDesc(id);

        List<Long> articleIds = myLinks.stream()
                .map(IntelligenceArticle::getArticleId).toList();
        List<NewsArticle> articles = newsArticleRepository.findAllById(articleIds);

        // 按置信度降序排列
        articles.sort((a, b) -> {
            double sa = a.getCredibilityScore() != null ? a.getCredibilityScore() : 0;
            double sb = b.getCredibilityScore() != null ? b.getCredibilityScore() : 0;
            return Double.compare(sb, sa);
        });

        // 如果情报没有 content，从关联新闻生成
        if (intel.getContent() == null || intel.getContent().isBlank()) {
            String generated = generateContentFromArticles(intel, articles);
            intel.setContent(generated);
            intelligenceRepository.save(intel);
        }

        // 构建来源列表（所有关联文章）
        List<SourceInfo> sources = articles.stream().map(a -> new SourceInfo(
                a.getId(),
                a.getSourceName(),
                mapCredibilityTag(a.getCredibilityLevel()),
                a.getTitle(),
                a.getSourceUrl()
        )).toList();

        // 估算阅读时间
        int contentLength = intel.getContent() != null ? intel.getContent().length() : 0;
        int readMinutes = Math.max(1, contentLength / 500);
        String readTime = (readMinutes <= 1) ? "1 min" : readMinutes + " min";

        return new IntelligenceDetail(intel, sources, readTime, articles);
    }

    /** Generate content: LLM synthesis for multi-source, fallback to concatenation */
    private String generateContentFromArticles(Intelligence intel, List<NewsArticle> articles) {
        if (articles.isEmpty()) return "";

        // Try LLM synthesis for multi-source intelligences
        if (articles.size() >= 2) {
            try {
                // 限制最多取5条新闻，每条摘要最多200字，控制总 prompt 长度
                String articlesText = articles.stream().limit(5).map(a -> {
                    String body = a.getSummary() != null ? a.getSummary() : "";
                    if (body.isBlank() && a.getContent() != null) {
                        body = a.getContent().length() > 200 ? a.getContent().substring(0, 200) + "..." : a.getContent();
                    } else if (body.length() > 200) {
                        body = body.substring(0, 200) + "...";
                    }
                    return "【" + a.getSourceName() + "】" + a.getTitle() + "\n" + body;
                }).collect(Collectors.joining("\n\n"));

                String prompt = "你是投研分析师。基于以下 " + Math.min(articles.size(), 5) + " 条新闻，撰写中文投研分析。\n\n"
                        + "要求：1)事件概述2-3句 2)综合各来源差异信息，标注来源 3)市场影响分析 4)2-3个关注要点用·开头\n"
                        + "格式：段落间空行，400-600字，纯文本无Markdown\n\n"
                        + "【新闻素材】\n" + articlesText;

                String content = chatClient.prompt().user(prompt).call().content();
                if (content != null && !content.isBlank()) {
                    // 清理可能的 Markdown 标记
                    content = content.replaceAll("(?m)^#{1,4}\\s*", "")
                                     .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                                     .replaceAll("\\*([^*]+)\\*", "$1")
                                     .trim();
                    log.info("LLM content generated for intel {} ({} chars)", intel.getId(), content.length());
                    return content;
                }
            } catch (Exception e) {
                log.warn("LLM content generation failed for intel {}: {}", intel.getId(), e.getMessage());
            }
        }

        // Single source: also try LLM to polish the content
        if (articles.size() == 1) {
            try {
                NewsArticle a = articles.get(0);
                String body = a.getContent() != null ? a.getContent() : (a.getSummary() != null ? a.getSummary() : "");
                if (body.length() > 400) body = body.substring(0, 400) + "...";
                if (!body.isBlank()) {
                    String prompt = "将以下新闻改写为投研快讯。要求：第一段概述核心事件2-3句，第二段分析市场影响。200-300字，纯文本无Markdown。\n\n"
                            + "【" + a.getSourceName() + "】" + a.getTitle() + "\n" + body;

                    String content = chatClient.prompt().user(prompt).call().content();
                    if (content != null && !content.isBlank()) {
                        content = content.replaceAll("(?m)^#{1,4}\\s*", "")
                                         .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                                         .replaceAll("\\*([^*]+)\\*", "$1")
                                         .trim();
                        log.info("LLM content polished for single-source intel {}", intel.getId());
                        return content;
                    }
                }
            } catch (Exception e) {
                log.warn("LLM polish failed for intel {}: {}", intel.getId(), e.getMessage());
            }
        }

        // Fallback: concatenate articles
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            if (articles.size() > 1) {
                sb.append("【").append(a.getSourceName() != null ? a.getSourceName() : "来源").append("】");
            }
            String body = a.getContent();
            if (body == null || body.isBlank()) body = a.getSummary();
            if (body == null || body.isBlank()) body = a.getTitle();
            sb.append(body);
            if (i < articles.size() - 1) sb.append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String mapCredibilityTag(String level) {
        if (level == null) return "未知";
        if ("authoritative".equals(level)) return "权威";
        if ("normal".equals(level)) return "可信";
        if ("questionable".equals(level)) return "存疑";
        return "未知";
    }

    public record SourceInfo(
            Long articleId,
            String sourceName,
            String credibilityTag,
            String title,
            String sourceUrl
    ) {}

    public record IntelligenceDetail(
            Intelligence intelligence,
            List<SourceInfo> sources,
            String readTime,
            List<NewsArticle> articles
    ) {}
}
