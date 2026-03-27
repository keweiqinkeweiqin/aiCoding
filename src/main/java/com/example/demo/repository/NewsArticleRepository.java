package com.example.demo.repository;

import com.example.demo.model.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    boolean existsByContentHash(String contentHash);

    @Query("SELECT n.title FROM NewsArticle n WHERE n.collectedAt > :since")
    List<String> findTitlesSince(LocalDateTime since);

    List<NewsArticle> findByCollectedAtAfterOrderByCollectedAtDesc(LocalDateTime since);
}
