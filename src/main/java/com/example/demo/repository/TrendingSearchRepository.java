package com.example.demo.repository;

import com.example.demo.model.TrendingSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrendingSearchRepository extends JpaRepository<TrendingSearch, Long> {

    Optional<TrendingSearch> findByKeywordAndDate(String keyword, LocalDate date);

    List<TrendingSearch> findByDateOrderBySearchCountDesc(LocalDate date);
}
