package com.brotherc.aquant.strategy.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class StockStrategyIndicatorSeriesVO {

    private String name;

    private String type;

    private List<BigDecimal> values;

}
