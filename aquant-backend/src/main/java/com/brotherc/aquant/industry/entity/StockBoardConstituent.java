package com.brotherc.aquant.industry.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行业当前成分股。
 */
@Data
@Entity
@Table(name = "stock_board_constituent")
public class StockBoardConstituent {

    /**
     * 主键 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 行业板块代码
     */
    @Column(name = "board_code")
    private String boardCode;

    /**
     * 成分股票代码
     */
    @Column(name = "stock_code")
    private String stockCode;

    /**
     * 成分股票名称
     */
    @Column(name = "stock_name")
    private String stockName;

    /**
     * 数据源更新时间
     */
    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

}
