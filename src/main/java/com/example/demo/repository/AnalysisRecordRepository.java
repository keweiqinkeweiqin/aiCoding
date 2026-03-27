package com.example.demo.repository;

import com.example.demo.model.AnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRecordRepository extends JpaRepository<AnalysisRecord, Long> {

    Optional<AnalysisRecord> findFirstByUserIdAndNewsArticleIdOrderByCreatedAtDesc(
            Long userId, Long newsArticleId);

    List<AnalysisRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
