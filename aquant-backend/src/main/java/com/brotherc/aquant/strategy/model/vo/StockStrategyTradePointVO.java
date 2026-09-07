package com.brotherc.aquant.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class StockStrategyTradePointVO {

    private String tradeDate;

    private BigDecimal price;

    private BigDecimal capital;

    private String signal;

    private Integer sequence;

}
