package com.brotherc.aquant.strategy.entity;

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
 * 网格交易策略历史回测快照
 */
@Data
@Entity
@Table(name = "stock_strategy_grid_backtest_snapshot")
public class StockStrategyGridBacktestSnapshot {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 回测批次号
     */
    @Column(name = "batch_no")
    private Long batchNo;

    /**
     * 所属市场（如 A股/主板/创业板等）
     */
    @Column(name = "market")
    private String market;

    /**
     * 股票代码（如 600519、000001）
     */
    @Column(name = "code")
    private String code;

    /**
     * 股票名称
     */
    @Column(name = "name")
    private String name;

    /**
     * 单格价格涨跌比例（如 0.03 表示 3%）
     */
    @Column(name = "grid_rate")
    private BigDecimal gridRate;

    /**
     * 单方向允许交易的最大网格层数
     */
    @Column(name = "grid_count")
    private Integer gridCount;

    /**
     * 回测年限（如 1、3、5 年）
     */
    @Column(name = "recent_years")
    private Integer recentYears;

    /**
     * 策略累计收益率（百分比）
     */
    @Column(name = "total_return")
    private BigDecimal totalReturn;

    /**
     * 交易次数（开平仓交易总笔数）
     */
    @Column(name = "trade_count")
    private Integer tradeCount;

    /**
     * 胜率（百分比）
     */
    @Column(name = "win_rate")
    private BigDecimal winRate;

    /**
     * T 检验统计量
     */
    @Column(name = "t_value")
    private Double tValue;

    /**
     * P 值（统计显著性指标，P < 0.05 具有显著性）
     */
    @Column(name = "p_value")
    private Double pValue;

    /**
     * 策略可靠性评级
     */
    @Column(name = "reliability")
    private String reliability;

    /**
     * 最新股票价格
     */
    @Column(name = "latest_price")
    private BigDecimal latestPrice;

    /**
     * 价格处于历史区间比例（PIR）
     */
    @Column(name = "pir")
    private BigDecimal pir;

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
