package com.brotherc.aquant.strategy.service;

import com.brotherc.aquant.stock.entity.StockQuote;
import com.brotherc.aquant.stock.entity.StockQuoteHistory;
import com.brotherc.aquant.stock.repository.StockQuoteHistoryRepository;
import com.brotherc.aquant.stock.repository.StockQuoteRepository;
import com.brotherc.aquant.strategy.model.vo.StockStrategyBacktestDetailReqVO;
import com.brotherc.aquant.strategy.model.vo.StockStrategyBacktestDetailVO;
import com.brotherc.aquant.strategy.model.vo.StockTradeBacktestVO;
import org.apache.commons.math3.stat.inference.TTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class StockStrategyBacktestDetailServiceTest {

    @Mock
    private StockQuoteRepository stockQuoteRepository;
    @Mock
    private StockQuoteHistoryRepository stockQuoteHistoryRepository;
    @InjectMocks
    private StockStrategyBacktestDetailService detailService;

    private StockQuote stock;
    private List<StockQuoteHistory> allHistories;

    @BeforeEach
    void setUp() {
        stock = new StockQuote();
        stock.setCode("sh600000");
        stock.setName("测试股票");
        stock.setLatestPrice(new BigDecimal("108"));

        allHistories = new ArrayList<>();
        LocalDate firstDate = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 400; i++) {
            double price = 100D + Math.sin(i / 8D) * 12D + i * 0.02D;
            StockQuoteHistory history = new StockQuoteHistory();
            history.setCode(stock.getCode());
            history.setTradeDate(firstDate.plusDays(i).toString());
            history.setClosePrice(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            allHistories.add(history);
        }

        when(stockQuoteRepository.findByCode(stock.getCode())).thenReturn(stock);
        lenient().when(stockQuoteHistoryRepository.findLatestByCode(anyString(), anyInt())).thenAnswer(invocation -> {
            int limit = invocation.getArgument(1);
            List<StockQuoteHistory> descending = new ArrayList<>(
                    allHistories.subList(Math.max(0, allHistories.size() - limit), allHistories.size())
            );
            Collections.reverse(descending);
            return descending;
        });
    }

    @Test
    void dualMaDetailShouldMatchBacktestReturn() {
        StockStrategyBacktestDetailReqVO request = request("DUAL_MA");
        StockStrategyBacktestDetailVO detail = detailService.getDetail(request);
        BigDecimal[] prices = latestPrices(270);
        StockTradeBacktestVO summary = new DualMovingAverageStrategy(stockQuoteHistoryRepository)
                .backtestSingle(stock, prices, 5, 20, 1, new TTest(), BigDecimal.valueOf(5), BigDecimal.valueOf(20));

        assertDetailMatchesSummary(detail, summary);
        assertThat(detail.getIndicatorSeries()).extracting("name").containsExactly("MA5", "MA20");
    }

    @Test
    void momentumDetailShouldMatchBacktestReturn() {
        StockStrategyBacktestDetailReqVO request = request("MOMENTUM");
        StockStrategyBacktestDetailVO detail = detailService.getDetail(request);
        StockTradeBacktestVO summary = new MomentumStrategy(stockQuoteHistoryRepository)
                .backtestSingle(stock, latestPrices(270), 20, 1, new TTest());

        assertDetailMatchesSummary(detail, summary);
        assertThat(detail.getIndicatorSeries()).extracting("name").containsExactly("动量(%)");
    }

    @Test
    void macdDetailShouldMatchBacktestReturn() {
        StockStrategyBacktestDetailReqVO request = request("MACD");
        StockStrategyBacktestDetailVO detail = detailService.getDetail(request);
        StockTradeBacktestVO summary = new MacdStrategy(stockQuoteHistoryRepository)
                .backtestSingle(stock, latestPrices(337), 12, 26, 9, 1, new TTest());

        assertDetailMatchesSummary(detail, summary);
        assertThat(detail.getIndicatorSeries()).extracting("name").containsExactly("DIF", "DEA", "MACD柱");
    }

    @Test
    void gridDetailShouldMatchBacktestReturn() {
        StockStrategyBacktestDetailReqVO request = request("GRID");
        StockStrategyBacktestDetailVO detail = detailService.getDetail(request);
        StockTradeBacktestVO summary = new GridTradingStrategy(stockQuoteHistoryRepository)
                .backtestSingle(stock, latestPrices(251), new BigDecimal("0.03"), 5, 1, new TTest());

        assertDetailMatchesSummary(detail, summary);
        assertThat(detail.getIndicatorSeries()).extracting("name")
                .containsExactly("网格参考价", "下一买入价", "下一卖出价");
    }

    @Test
    void customDateRangeShouldKeepWarmupDataOutsideVisibleRange() {
        when(stockQuoteHistoryRepository.findByCodeOrderByTradeDateAsc(stock.getCode())).thenReturn(allHistories);
        StockStrategyBacktestDetailReqVO request = request("MACD");
        request.setStartDate(LocalDate.of(2024, 10, 27));
        request.setEndDate(LocalDate.of(2024, 12, 16));

        StockStrategyBacktestDetailVO detail = detailService.getDetail(request);

        assertThat(detail.getTradeDates()).first().isEqualTo("2024-10-27");
        assertThat(detail.getTradeDates()).last().isEqualTo("2024-12-16");
        assertThat(detail.getTradeDates()).hasSize(51);
        assertThat(detail.getIndicatorSeries()).allSatisfy(series ->
                assertThat(series.getValues()).hasSize(51));
    }

    private StockStrategyBacktestDetailReqVO request(String strategyType) {
        StockStrategyBacktestDetailReqVO request = new StockStrategyBacktestDetailReqVO();
        request.setCode(stock.getCode());
        request.setStrategyType(strategyType);
        request.setRecentYears(1);
        return request;
    }

    private BigDecimal[] latestPrices(int size) {
        return allHistories.subList(allHistories.size() - size, allHistories.size()).stream()
                .map(StockQuoteHistory::getClosePrice)
                .toArray(BigDecimal[]::new);
    }

    private void assertDetailMatchesSummary(
            StockStrategyBacktestDetailVO detail, StockTradeBacktestVO summary
    ) {
        BigDecimal detailReturn = detail.getFinalCapital().divide(detail.getInitialCapital(), 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
        assertThat(detailReturn).isCloseTo(summary.getTotalReturn(), within(new BigDecimal("0.0001")));
        assertThat(detail.getTradeDates()).hasSameSizeAs(detail.getClosePrices());
        assertThat(detail.getCapitalValues()).hasSameSizeAs(detail.getClosePrices());
        assertThat(detail.getTradePoints()).isNotEmpty();
    }
}
