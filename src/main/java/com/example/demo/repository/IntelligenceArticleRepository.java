package com.example.demo.repository;

import com.example.demo.model.IntelligenceArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntelligenceArticleRepository extends JpaRepository<IntelligenceArticle, Long> {

    List<IntelligenceArticle> findByIntelligenceIdOrderByIsPrimaryDesc(Long intelligenceId);

    boolean existsByArticleId(Long articleId);

    List<IntelligenceArticle> findByArticleId(Long articleId);
}
