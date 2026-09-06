package com.brotherc.aquant.strategy.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StockStrategyBacktestDetailVO {

    private String code;

    private String name;

    private String strategyType;

    private String strategyName;

    private BigDecimal initialCapital;

    private BigDecimal finalCapital;

    private List<String> tradeDates;

    private List<BigDecimal> closePrices;

    private List<BigDecimal> capitalValues;

    private List<StockStrategyIndicatorSeriesVO> indicatorSeries;

    private List<StockStrategyTradePointVO> tradePoints;

}
