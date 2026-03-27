package com.example.demo.repository;

import com.example.demo.model.Intelligence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface IntelligenceRepository extends JpaRepository<Intelligence, Long> {

    Page<Intelligence> findByCreatedAtAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since, Pageable pageable);

    List<Intelligence> findByCreatedAtAfterOrderByLatestArticleTimeDesc(
            LocalDateTime since);
}
