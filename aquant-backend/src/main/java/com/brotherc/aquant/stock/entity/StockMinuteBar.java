package com.brotherc.aquant.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票分钟级K线
 */
@Data
@Entity
@Table(name = "stock_minute_bar")
public class StockMinuteBar {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 股票代码，带交易所前缀，如 sh600519
     */
    @Column(name = "code")
    private String code;

    /**
     * 时间，格式 yyyy-MM-dd HH:mm:ss
     */
    @Column(name = "bar_time")
    private String barTime;

    /**
     * 分钟周期，当前固定 1（1分钟K线）
     */
    @Column(name = "period")
    private Integer period;

    /**
     * 开盘价
     */
    @Column(name = "open_price")
    private BigDecimal openPrice;

    /**
     * 最高价
     */
    @Column(name = "high_price")
    private BigDecimal highPrice;

    /**
     * 最低价
     */
    @Column(name = "low_price")
    private BigDecimal lowPrice;

    /**
     * 收盘价
     */
    @Column(name = "close_price")
    private BigDecimal closePrice;

    /**
     * 成交量，单位：股
     */
    @Column(name = "volume")
    private BigDecimal volume;

    /**
     * 成交额，单位：元
     */
    @Column(name = "turnover")
    private BigDecimal turnover;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
