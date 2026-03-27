package com.example.demo.service;

import com.example.demo.embedding.VectorSearchService;
import com.example.demo.model.NewsArticle;
import com.example.demo.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能问答：用户输入 → Embedding语义检索相关新闻 → LLM推理生成分析
 */
@Service
public class SmartQueryService {

    private static final Logger log = LoggerFactory.getLogger(SmartQueryService.class);
    private final VectorSearchService vectorSearchService;
    private final NewsArticleRepository newsArticleRepository;
    private final ChatClient chatClient;

    public SmartQueryService(VectorSearchService vectorSearchService,
                             NewsArticleRepository newsArticleRepository,
                             ChatClient.Builder chatClientBuilder) {
        this.vectorSearchService = vectorSearchService;
        this.newsArticleRepository = newsArticleRepository;
        this.chatClient = chatClientBuilder.build();
    }

    public QueryResult query(String userQuestion) {
        // 1. 语义检索最相关的5篇新闻
        List<Long> articleIds = vectorSearchService.semanticSearch(userQuestion, 5);
        List<NewsArticle> relatedArticles = articleIds.stream()
                .map(newsArticleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (relatedArticles.isEmpty()) {
            return new QueryResult("暂无相关新闻数据，请先采集新闻。", List.of(), 0);
        }

        // 2. 组装上下文
        String newsContext = relatedArticles.stream()
                .map(a -> String.format("【%s】%s\n来源: %s | 可信度: %s\n%s",
                        a.getSourceName(), a.getTitle(),
                        a.getSourceUrl(), a.getCredibilityLevel(),
                        a.getContent() != null && a.getContent().length() > 300
                                ? a.getContent().substring(0, 300) + "..."
                                : a.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 调LLM推理
        String systemPrompt = """
                你是「华尔街之眼」AI投研助手，专注AI与科技投资领域。
                基于提供的新闻数据回答用户问题。要求：
                1. 标注信息来源及可信度
                2. 区分事实与观点
                3. 给出投资相关的分析和建议
                4. 如果信息不足，明确说明
                """;

        String userPrompt = String.format("""
                ## 相关新闻（共%d条，按语义相关度排序）
                
                %s
                
                ## 用户问题
                %s
                
                请基于以上新闻数据进行分析和回答。
                """, relatedArticles.size(), newsContext, userQuestion);

        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            return new QueryResult(answer, relatedArticles, relatedArticles.size());
        } catch (Exception e) {
            log.error("LLM推理失败: {}", e.getMessage());
            return new QueryResult("AI分析服务暂时不可用: " + e.getMessage(), relatedArticles, relatedArticles.size());
        }
    }

    public record QueryResult(String answer, List<NewsArticle> relatedNews, int matchedCount) {}
}
