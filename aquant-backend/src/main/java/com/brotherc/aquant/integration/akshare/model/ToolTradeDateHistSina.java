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
     * 交易日期，AKShare 通常返回 yyyy-MM-ddTHH:mm:ss.SSS 格式。
     */
    @JsonProperty("trade_date")
    private String tradeDate;

}
