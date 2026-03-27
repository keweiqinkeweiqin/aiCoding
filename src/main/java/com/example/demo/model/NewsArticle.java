package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String content;

    @Column(length = 500)
    private String summary;

    @Column(length = 1000)
    private String sourceUrl;

    private String sourceName;

    private String sourceType; // rss / api

    private String credibilityLevel; // authoritative / normal / questionable

    private String sentiment; // positive / negative / neutral

    private Double sentimentScore;

    @Column(length = 500)
    private String relatedStocks;

    @Column(length = 500)
    private String tags;

    private String contentHash;

    private Boolean isDuplicate;

    @Column(columnDefinition = "CLOB")
    private String embeddingJson;

    private LocalDateTime publishedAt;
    private LocalDateTime collectedAt;

    @PrePersist
    public void prePersist() {
        if (this.collectedAt == null) this.collectedAt = LocalDateTime.now();
        if (this.isDuplicate == null) this.isDuplicate = false;
    }

    public NewsArticle() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getCredibilityLevel() { return credibilityLevel; }
    public void setCredibilityLevel(String credibilityLevel) { this.credibilityLevel = credibilityLevel; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }
    public String getRelatedStocks() { return relatedStocks; }
    public void setRelatedStocks(String relatedStocks) { this.relatedStocks = relatedStocks; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Boolean getIsDuplicate() { return isDuplicate; }
    public void setIsDuplicate(Boolean isDuplicate) { this.isDuplicate = isDuplicate; }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
}
