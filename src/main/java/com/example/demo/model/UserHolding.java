package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_holdings")
public class UserHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(length = 100)
    private String stockName;

    @Column(length = 50)
    private String sector;

    private Double positionRatio;

    private Double costPrice;

    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (this.addedAt == null) this.addedAt = LocalDateTime.now();
    }

    public UserHolding() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public Double getPositionRatio() { return positionRatio; }
    public void setPositionRatio(Double positionRatio) { this.positionRatio = positionRatio; }
    public Double getCostPrice() { return costPrice; }
    public void setCostPrice(Double costPrice) { this.costPrice = costPrice; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
