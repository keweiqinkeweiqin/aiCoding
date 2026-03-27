package com.example.demo.repository;

import com.example.demo.model.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    List<MarketData> findByStockCodeOrderByCollectedAtDesc(String stockCode);

    List<MarketData> findTop1ByStockCodeOrderByCollectedAtDesc(String stockCode);
}
