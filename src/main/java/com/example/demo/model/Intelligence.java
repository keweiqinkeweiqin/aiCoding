package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "intelligences")
public class Intelligence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(length = 1000)
    private String summary;

    @Column(columnDefinition = "CLOB")
    private String content;

    @Column(length = 20)
    private String priority; // high / medium / low

    @Column(length = 100)
    private String primarySource;

    @Column(length = 20)
    private String credibilityLevel; // authoritative / normal / questionable

    private Double credibilityScore;

    private Integer sourceCount;

    @Column(length = 20)
    private String sentiment; // positive / negative / neutral

    private Double sentimentScore;

    @Column(length = 2000)
    private String relatedStocks;

    @Column(length = 2000)
    private String tags;

    private Long titleSimhash;

    @Column(columnDefinition = "CLOB")
    private String embeddingJson;

    private LocalDateTime latestArticleTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.sourceCount == null) this.sourceCount = 1;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Intelligence() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getPrimarySource() { return primarySource; }
    public void setPrimarySource(String primarySource) { this.primarySource = primarySource; }
    public String getCredibilityLevel() { return credibilityLevel; }
    public void setCredibilityLevel(String credibilityLevel) { this.credibilityLevel = credibilityLevel; }
    public Double getCredibilityScore() { return credibilityScore; }
    public void setCredibilityScore(Double credibilityScore) { this.credibilityScore = credibilityScore; }
    public Integer getSourceCount() { return sourceCount; }
    public void setSourceCount(Integer sourceCount) { this.sourceCount = sourceCount; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public Double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }
    public String getRelatedStocks() { return relatedStocks; }
    public void setRelatedStocks(String relatedStocks) { this.relatedStocks = relatedStocks; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Long getTitleSimhash() { return titleSimhash; }
    public void setTitleSimhash(Long titleSimhash) { this.titleSimhash = titleSimhash; }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }
    public LocalDateTime getLatestArticleTime() { return latestArticleTime; }
    public void setLatestArticleTime(LocalDateTime latestArticleTime) { this.latestArticleTime = latestArticleTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
