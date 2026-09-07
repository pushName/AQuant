package com.brotherc.aquant.strategy.service;

import com.brotherc.aquant.common.enums.TradeSignal;
import com.brotherc.aquant.common.exception.BusinessException;
import com.brotherc.aquant.common.exception.ExceptionEnum;
import com.brotherc.aquant.stock.entity.StockQuote;
import com.brotherc.aquant.stock.entity.StockQuoteHistory;
import com.brotherc.aquant.stock.repository.StockQuoteHistoryRepository;
import com.brotherc.aquant.stock.repository.StockQuoteRepository;
import com.brotherc.aquant.strategy.model.vo.StockStrategyBacktestDetailReqVO;
import com.brotherc.aquant.strategy.model.vo.StockStrategyBacktestDetailVO;
import com.brotherc.aquant.strategy.model.vo.StockStrategyIndicatorSeriesVO;
import com.brotherc.aquant.strategy.model.vo.StockStrategyTradePointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockStrategyBacktestDetailService {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000");
    private static final int CALCULATION_SCALE = 8;

    private final StockQuoteRepository stockQuoteRepository;
    private final StockQuoteHistoryRepository stockQuoteHistoryRepository;

    public StockStrategyBacktestDetailVO getDetail(StockStrategyBacktestDetailReqVO reqVO) {
        String strategyType = reqVO.getStrategyType().toUpperCase();
        int warmupDays = getWarmupDays(reqVO, strategyType);
        StockQuote stock = stockQuoteRepository.findByCode(reqVO.getCode());
        if (stock == null) {
            throw new BusinessException(ExceptionEnum.STOCK_NOT_FOUND);
        }

        List<StockQuoteHistory> histories;
        int displayStart;
        if (reqVO.getStartDate() != null || reqVO.getEndDate() != null) {
            if (reqVO.getStartDate() == null || reqVO.getEndDate() == null
                    || reqVO.getStartDate().isAfter(reqVO.getEndDate())) {
                throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_TYPE_ILLEGAL);
            }
            List<StockQuoteHistory> allHistories = stockQuoteHistoryRepository.findByCodeOrderByTradeDateAsc(reqVO.getCode());
            int requestedStart = 0;
            while (requestedStart < allHistories.size()
                    && allHistories.get(requestedStart).getTradeDate().compareTo(reqVO.getStartDate().toString()) < 0) {
                requestedStart++;
            }
            int requestedEnd = allHistories.size() - 1;
            while (requestedEnd >= 0
                    && allHistories.get(requestedEnd).getTradeDate().compareTo(reqVO.getEndDate().toString()) > 0) {
                requestedEnd--;
            }
            if (requestedStart > requestedEnd) {
                throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_BACKTEST_SAMPLE_INSUFFICIENT);
            }
            int calculationStart = Math.max(0, requestedStart - warmupDays);
            histories = new ArrayList<>(allHistories.subList(calculationStart, requestedEnd + 1));
            displayStart = Math.max(warmupDays, requestedStart - calculationStart);
        } else {
            int needDays = reqVO.getRecentYears() * 250 + warmupDays;
            histories = stockQuoteHistoryRepository.findLatestByCode(reqVO.getCode(), needDays);
            Collections.reverse(histories);
            displayStart = "GRID".equals(strategyType) ? 0 : warmupDays;
        }
        if (histories.size() <= warmupDays) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_BACKTEST_SAMPLE_INSUFFICIENT);
        }

        List<String> dates = histories.stream().map(StockQuoteHistory::getTradeDate).toList();
        List<BigDecimal> prices = histories.stream().map(StockQuoteHistory::getClosePrice).toList();
        for (BigDecimal price : prices) {
            if (price == null || price.signum() <= 0) {
                throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_BACKTEST_SAMPLE_INSUFFICIENT);
            }
        }

        return switch (strategyType) {
            case "DUAL_MA" -> buildDualMaDetail(reqVO, stock, dates, prices, displayStart);
            case "MOMENTUM" -> buildMomentumDetail(reqVO, stock, dates, prices, displayStart);
            case "MACD" -> buildMacdDetail(reqVO, stock, dates, prices, displayStart);
            case "GRID" -> buildGridDetail(reqVO, stock, dates, prices, displayStart);
            default -> throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_TYPE_ILLEGAL);
        };
    }

    private StockStrategyBacktestDetailVO buildDualMaDetail(
            StockStrategyBacktestDetailReqVO reqVO, StockQuote stock,
            List<String> dates, List<BigDecimal> prices, int displayStart
    ) {
        int maShort = reqVO.getMaShort();
        int maLong = reqVO.getMaLong();
        if (maShort <= 0 || maShort >= maLong) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_DUAL_MA_ILLEGAL);
        }

        List<BigDecimal> shortValues = movingAverage(prices, maShort);
        List<BigDecimal> longValues = movingAverage(prices, maLong);
        List<BigDecimal> capitalValues = emptyValues(prices.size());
        List<StockStrategyTradePointVO> trades = new ArrayList<>();
        BigDecimal netValue = BigDecimal.ONE;
        BigDecimal costPrice = null;
        int buySequence = 0;
        int sellSequence = 0;
        for (int i = displayStart; i < prices.size(); i++) {
            int previousCompare = shortValues.get(i - 1).compareTo(longValues.get(i - 1));
            int currentCompare = shortValues.get(i).compareTo(longValues.get(i));
            if (previousCompare <= 0 && currentCompare > 0 && costPrice == null) {
                costPrice = prices.get(i);
                trades.add(trade(dates, prices, i, netValue, TradeSignal.BUY, ++buySequence));
            } else if (previousCompare >= 0 && currentCompare < 0 && costPrice != null) {
                netValue = netValue.multiply(prices.get(i).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
                costPrice = null;
                trades.add(trade(dates, prices, i, netValue, TradeSignal.SELL, ++sellSequence));
            }
            capitalValues.set(i, capital(netValue, costPrice, prices.get(i)));
        }
        if (costPrice != null) {
            int last = prices.size() - 1;
            netValue = netValue.multiply(prices.get(last).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
            capitalValues.set(last, capital(netValue, null, prices.get(last)));
            trades.add(trade(dates, prices, last, netValue, TradeSignal.SELL, ++sellSequence));
        }

        List<StockStrategyIndicatorSeriesVO> indicators = List.of(
                series("MA" + maShort, "line", shortValues, displayStart),
                series("MA" + maLong, "line", longValues, displayStart)
        );
        return detail(stock, "DUAL_MA", "双均线策略", dates, prices, capitalValues, indicators, trades, displayStart);
    }

    private StockStrategyBacktestDetailVO buildMomentumDetail(
            StockStrategyBacktestDetailReqVO reqVO, StockQuote stock,
            List<String> dates, List<BigDecimal> prices, int displayStart
    ) {
        int lookbackDays = reqVO.getLookbackDays();
        if (lookbackDays <= 0) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_TYPE_ILLEGAL);
        }

        List<BigDecimal> momentumValues = emptyValues(prices.size());
        List<BigDecimal> capitalValues = emptyValues(prices.size());
        List<StockStrategyTradePointVO> trades = new ArrayList<>();
        BigDecimal netValue = BigDecimal.ONE;
        BigDecimal costPrice = null;
        BigDecimal previousMomentum = null;
        int buySequence = 0;
        int sellSequence = 0;
        for (int i = displayStart; i < prices.size(); i++) {
            BigDecimal momentum = prices.get(i).subtract(prices.get(i - lookbackDays))
                    .divide(prices.get(i - lookbackDays), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            momentumValues.set(i, momentum);
            if (previousMomentum != null && previousMomentum.signum() <= 0
                    && momentum.signum() > 0 && costPrice == null) {
                costPrice = prices.get(i);
                trades.add(trade(dates, prices, i, netValue, TradeSignal.BUY, ++buySequence));
            } else if (previousMomentum != null && previousMomentum.signum() >= 0
                    && momentum.signum() < 0 && costPrice != null) {
                netValue = netValue.multiply(prices.get(i).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
                costPrice = null;
                trades.add(trade(dates, prices, i, netValue, TradeSignal.SELL, ++sellSequence));
            }
            capitalValues.set(i, capital(netValue, costPrice, prices.get(i)));
            previousMomentum = momentum;
        }
        if (costPrice != null) {
            int last = prices.size() - 1;
            netValue = netValue.multiply(prices.get(last).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
            capitalValues.set(last, capital(netValue, null, prices.get(last)));
            trades.add(trade(dates, prices, last, netValue, TradeSignal.SELL, ++sellSequence));
        }

        return detail(
                stock, "MOMENTUM", "动量策略", dates, prices, capitalValues,
                List.of(series("动量(%)", "line", momentumValues, displayStart)), trades, displayStart
        );
    }

    private StockStrategyBacktestDetailVO buildMacdDetail(
            StockStrategyBacktestDetailReqVO reqVO, StockQuote stock,
            List<String> dates, List<BigDecimal> prices, int displayStart
    ) {
        int fastPeriod = reqVO.getFastPeriod();
        int slowPeriod = reqVO.getSlowPeriod();
        int signalPeriod = reqVO.getSignalPeriod();
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0 || fastPeriod >= slowPeriod) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_MACD_PARAMS_ILLEGAL);
        }

        List<BigDecimal> difValues = emptyValues(prices.size());
        List<BigDecimal> deaValues = emptyValues(prices.size());
        List<BigDecimal> histogramValues = emptyValues(prices.size());
        double fastEma = prices.get(0).doubleValue();
        double slowEma = fastEma;
        double dea = 0D;
        double fastAlpha = 2D / (fastPeriod + 1D);
        double slowAlpha = 2D / (slowPeriod + 1D);
        double signalAlpha = 2D / (signalPeriod + 1D);
        for (int i = 1; i < prices.size(); i++) {
            double close = prices.get(i).doubleValue();
            fastEma += fastAlpha * (close - fastEma);
            slowEma += slowAlpha * (close - slowEma);
            double dif = fastEma - slowEma;
            dea += signalAlpha * (dif - dea);
            difValues.set(i, decimal(dif));
            deaValues.set(i, decimal(dea));
            histogramValues.set(i, decimal((dif - dea) * 2D));
        }

        List<BigDecimal> capitalValues = emptyValues(prices.size());
        List<StockStrategyTradePointVO> trades = new ArrayList<>();
        BigDecimal netValue = BigDecimal.ONE;
        BigDecimal costPrice = null;
        int buySequence = 0;
        int sellSequence = 0;
        for (int i = displayStart; i < prices.size(); i++) {
            BigDecimal previousGap = difValues.get(i - 1).subtract(deaValues.get(i - 1));
            BigDecimal currentGap = difValues.get(i).subtract(deaValues.get(i));
            if (previousGap.signum() <= 0 && currentGap.signum() > 0 && costPrice == null) {
                costPrice = prices.get(i);
                trades.add(trade(dates, prices, i, netValue, TradeSignal.BUY, ++buySequence));
            } else if (previousGap.signum() >= 0 && currentGap.signum() < 0 && costPrice != null) {
                netValue = netValue.multiply(prices.get(i).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
                costPrice = null;
                trades.add(trade(dates, prices, i, netValue, TradeSignal.SELL, ++sellSequence));
            }
            capitalValues.set(i, capital(netValue, costPrice, prices.get(i)));
        }
        if (costPrice != null) {
            int last = prices.size() - 1;
            netValue = netValue.multiply(prices.get(last).divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
            capitalValues.set(last, capital(netValue, null, prices.get(last)));
            trades.add(trade(dates, prices, last, netValue, TradeSignal.SELL, ++sellSequence));
        }

        List<StockStrategyIndicatorSeriesVO> indicators = List.of(
                series("DIF", "line", difValues, displayStart),
                series("DEA", "line", deaValues, displayStart),
                series("MACD柱", "bar", histogramValues, displayStart)
        );
        return detail(stock, "MACD", "MACD策略", dates, prices, capitalValues, indicators, trades, displayStart);
    }

    private StockStrategyBacktestDetailVO buildGridDetail(
            StockStrategyBacktestDetailReqVO reqVO, StockQuote stock,
            List<String> dates, List<BigDecimal> prices, int displayStart
    ) {
        BigDecimal gridRate = reqVO.getGridRate();
        int gridCount = reqVO.getGridCount();
        if (gridRate == null || gridRate.signum() <= 0 || gridRate.compareTo(new BigDecimal("0.5")) >= 0
                || gridCount < 1 || gridCount > 50) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_GRID_PARAMS_ILLEGAL);
        }

        List<BigDecimal> referenceValues = emptyValues(prices.size());
        List<BigDecimal> buyValues = emptyValues(prices.size());
        List<BigDecimal> sellValues = emptyValues(prices.size());
        List<BigDecimal> capitalValues = emptyValues(prices.size());
        List<StockStrategyTradePointVO> trades = new ArrayList<>();
        BigDecimal referencePrice = prices.get(displayStart);
        BigDecimal cash = new BigDecimal("0.5");
        BigDecimal shares = new BigDecimal("0.5").divide(referencePrice, CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal unitCapital = BigDecimal.ONE.divide(
                BigDecimal.valueOf(gridCount * 2L), CALCULATION_SCALE, RoundingMode.HALF_UP
        );
        int positionLevel = 0;
        int buySequence = 0;
        int sellSequence = 0;
        capitalValues.set(displayStart, INITIAL_CAPITAL);

        for (int i = displayStart; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            BigDecimal buyTrigger = referencePrice.multiply(BigDecimal.ONE.subtract(gridRate));
            BigDecimal sellTrigger = referencePrice.multiply(BigDecimal.ONE.add(gridRate));
            if (i > displayStart && price.compareTo(buyTrigger) <= 0 && positionLevel < gridCount
                    && cash.compareTo(unitCapital) >= 0) {
                shares = shares.add(unitCapital.divide(price, CALCULATION_SCALE, RoundingMode.HALF_UP));
                cash = cash.subtract(unitCapital);
                referencePrice = buyTrigger;
                positionLevel++;
                trades.add(gridTrade(dates, prices, i, cash, shares, TradeSignal.BUY, ++buySequence));
            } else if (i > displayStart && price.compareTo(sellTrigger) >= 0 && positionLevel > -gridCount
                    && shares.signum() > 0) {
                BigDecimal sellShares = unitCapital.divide(price, CALCULATION_SCALE, RoundingMode.HALF_UP).min(shares);
                shares = shares.subtract(sellShares);
                cash = cash.add(sellShares.multiply(price));
                referencePrice = sellTrigger;
                positionLevel--;
                trades.add(gridTrade(dates, prices, i, cash, shares, TradeSignal.SELL, ++sellSequence));
            }
            referenceValues.set(i, decimal(referencePrice));
            buyValues.set(i, decimal(referencePrice.multiply(BigDecimal.ONE.subtract(gridRate))));
            sellValues.set(i, decimal(referencePrice.multiply(BigDecimal.ONE.add(gridRate))));
            capitalValues.set(i, cash.add(shares.multiply(price)).multiply(INITIAL_CAPITAL).setScale(2, RoundingMode.HALF_UP));
        }

        List<StockStrategyIndicatorSeriesVO> indicators = List.of(
                series("网格参考价", "line", referenceValues, 0),
                series("下一买入价", "line", buyValues, 0),
                series("下一卖出价", "line", sellValues, 0)
        );
        return detail(stock, "GRID", "网格交易策略", dates, prices, capitalValues, indicators, trades, displayStart);
    }

    private int getWarmupDays(StockStrategyBacktestDetailReqVO reqVO, String strategyType) {
        if (reqVO.getRecentYears() == null || reqVO.getRecentYears() < 1 || reqVO.getRecentYears() > 10) {
            throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_TYPE_ILLEGAL);
        }
        return switch (strategyType) {
            case "DUAL_MA" -> reqVO.getMaLong();
            case "MOMENTUM" -> reqVO.getLookbackDays();
            case "MACD" -> reqVO.getSlowPeriod() * 3 + reqVO.getSignalPeriod();
            case "GRID" -> 1;
            default -> throw new BusinessException(ExceptionEnum.STOCK_STRATEGY_TYPE_ILLEGAL);
        };
    }

    private List<BigDecimal> movingAverage(List<BigDecimal> prices, int period) {
        List<BigDecimal> result = emptyValues(prices.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
            if (i >= period) {
                sum = sum.subtract(prices.get(i - period));
            }
            if (i >= period - 1) {
                result.set(i, sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    private BigDecimal capital(BigDecimal netValue, BigDecimal costPrice, BigDecimal currentPrice) {
        BigDecimal currentNetValue = costPrice == null ? netValue
                : netValue.multiply(currentPrice.divide(costPrice, CALCULATION_SCALE, RoundingMode.HALF_UP));
        return currentNetValue.multiply(INITIAL_CAPITAL).setScale(2, RoundingMode.HALF_UP);
    }

    private StockStrategyTradePointVO trade(
            List<String> dates, List<BigDecimal> prices, int index, BigDecimal netValue,
            TradeSignal signal, int sequence
    ) {
        return new StockStrategyTradePointVO(
                dates.get(index), prices.get(index), netValue.multiply(INITIAL_CAPITAL).setScale(2, RoundingMode.HALF_UP),
                signal.name(), sequence
        );
    }

    private StockStrategyTradePointVO gridTrade(
            List<String> dates, List<BigDecimal> prices, int index, BigDecimal cash, BigDecimal shares,
            TradeSignal signal, int sequence
    ) {
        BigDecimal capital = cash.add(shares.multiply(prices.get(index)))
                .multiply(INITIAL_CAPITAL).setScale(2, RoundingMode.HALF_UP);
        return new StockStrategyTradePointVO(
                dates.get(index), prices.get(index), capital, signal.name(), sequence
        );
    }

    private StockStrategyIndicatorSeriesVO series(
            String name, String type, List<BigDecimal> values, int displayStart
    ) {
        return new StockStrategyIndicatorSeriesVO(name, type, new ArrayList<>(values.subList(displayStart, values.size())));
    }

    private StockStrategyBacktestDetailVO detail(
            StockQuote stock, String strategyType, String strategyName,
            List<String> dates, List<BigDecimal> prices, List<BigDecimal> capitalValues,
            List<StockStrategyIndicatorSeriesVO> indicatorSeries,
            List<StockStrategyTradePointVO> trades, int displayStart
    ) {
        StockStrategyBacktestDetailVO result = new StockStrategyBacktestDetailVO();
        result.setCode(stock.getCode());
        result.setName(stock.getName());
        result.setStrategyType(strategyType);
        result.setStrategyName(strategyName);
        result.setInitialCapital(INITIAL_CAPITAL);
        result.setTradeDates(new ArrayList<>(dates.subList(displayStart, dates.size())));
        result.setClosePrices(new ArrayList<>(prices.subList(displayStart, prices.size())));
        result.setCapitalValues(new ArrayList<>(capitalValues.subList(displayStart, capitalValues.size())));
        result.setFinalCapital(result.getCapitalValues().get(result.getCapitalValues().size() - 1));
        result.setIndicatorSeries(indicatorSeries);
        result.setTradePoints(trades.stream()
                .filter(item -> item.getTradeDate().compareTo(result.getTradeDates().get(0)) >= 0)
                .toList());
        return result;
    }

    private List<BigDecimal> emptyValues(int size) {
        return new ArrayList<>(Arrays.asList(new BigDecimal[size]));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

}
