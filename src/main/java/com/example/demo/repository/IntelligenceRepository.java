package com.example.demo.repository;

import com.example.demo.model.Intelligence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface IntelligenceRepository extends JpaRepository<Intelligence, Long> {

    Page<Intelligence> findByCreatedAtAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since, Pageable pageable);

    Page<Intelligence> findByLatestArticleTimeAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since, Pageable pageable);

    List<Intelligence> findByCreatedAtAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since);

    List<Intelligence> findByLatestArticleTimeAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since);

    @Query("SELECT i FROM Intelligence i WHERE LOWER(i.title) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(i.summary) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(i.tags) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "OR LOWER(i.relatedStocks) LIKE LOWER(CONCAT('%',:keyword,'%'))")
    List<Intelligence> searchByKeyword(String keyword);
}
