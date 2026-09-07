package com.brotherc.aquant.industry.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 东方财富行业板块当前行情，与同花顺表物理隔离。
 */
@Data
@Entity
@Table(name = "stock_industry_board_em")
public class StockIndustryBoardEm {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 序号 / 排名
     */
    @Column(name = "seq_no")
    private Integer seqNo;

    /**
     * 行业板块名称（如 白酒、半导体）
     */
    @Column(name = "sector_name", nullable = false)
    private String sectorName;

    /**
     * 行业板块代码（如 BK0475）
     */
    @Column(name = "sector_code", nullable = false)
    private String sectorCode;

    /**
     * 涨跌幅(%)
     */
    @Column(name = "change_percent")
    private BigDecimal changePercent;

    /**
     * 总成交额
     */
    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    /**
     * 上涨家数
     */
    @Column(name = "rise_count")
    private Integer riseCount;

    /**
     * 下跌家数
     */
    @Column(name = "fall_count")
    private Integer fallCount;

    /**
     * 板块均价
     */
    @Column(name = "average_price")
    private BigDecimal averagePrice;

    /**
     * 领涨股票名称
     */
    @Column(name = "leading_stock")
    private String leadingStock;

    /**
     * 领涨股票涨跌幅(%)
     */
    @Column(name = "leading_stock_change_percent")
    private BigDecimal leadingStockChangePercent;

    /**
     * 交易日期
     */
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

}
