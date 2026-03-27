package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "trending_searches")
public class TrendingSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String keyword;

    private Integer searchCount;

    private LocalDate date;

    @PrePersist
    public void prePersist() {
        if (this.searchCount == null) this.searchCount = 1;
        if (this.date == null) this.date = LocalDate.now();
    }

    public TrendingSearch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getSearchCount() { return searchCount; }
    public void setSearchCount(Integer searchCount) { this.searchCount = searchCount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
