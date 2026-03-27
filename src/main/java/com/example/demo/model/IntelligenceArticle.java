package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "intelligence_articles",
       uniqueConstraints = @UniqueConstraint(columnNames = "articleId"))
public class IntelligenceArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long intelligenceId;

    private Long articleId;

    private Boolean isPrimary;

    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (this.addedAt == null) this.addedAt = LocalDateTime.now();
        if (this.isPrimary == null) this.isPrimary = false;
    }

    public IntelligenceArticle() {}

    public IntelligenceArticle(Long intelligenceId, Long articleId, boolean isPrimary) {
        this.intelligenceId = intelligenceId;
        this.articleId = articleId;
        this.isPrimary = isPrimary;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIntelligenceId() { return intelligenceId; }
    public void setIntelligenceId(Long intelligenceId) { this.intelligenceId = intelligenceId; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
