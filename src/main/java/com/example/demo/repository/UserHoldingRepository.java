package com.example.demo.repository;

import com.example.demo.model.UserHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserHoldingRepository extends JpaRepository<UserHolding, Long> {

    List<UserHolding> findByUserId(Long userId);

    Optional<UserHolding> findByUserIdAndStockCode(Long userId, String stockCode);

    void deleteByUserIdAndStockCode(Long userId, String stockCode);
}
