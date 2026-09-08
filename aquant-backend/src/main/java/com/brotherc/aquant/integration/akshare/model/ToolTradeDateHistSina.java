package com.brotherc.aquant.integration.akshare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 新浪财经 A 股交易日历。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolTradeDateHistSina {

    /**
     * 交易日期，格式 yyyy-MM-dd。
     */
    @JsonProperty("trade_date")
    private String tradeDate;

}
