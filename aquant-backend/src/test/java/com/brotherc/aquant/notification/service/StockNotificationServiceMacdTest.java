package com.brotherc.aquant.notification.service;

import com.brotherc.aquant.common.enums.NotificationType;
import com.brotherc.aquant.common.exception.BusinessException;
import com.brotherc.aquant.fund.repository.StockFundNetValueRepository;
import com.brotherc.aquant.notification.entity.StockNotification;
import com.brotherc.aquant.notification.model.vo.StockNotificationReqVO;
import com.brotherc.aquant.notification.repository.StockNotificationRepository;
import com.brotherc.aquant.stock.entity.StockQuoteHistory;
import com.brotherc.aquant.stock.repository.StockQuoteHistoryRepository;
import com.brotherc.aquant.sys.repository.SysUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockNotificationServiceMacdTest {

    @Mock
    private StockNotificationRepository notificationRepository;
    @Mock
    private StockQuoteHistoryRepository stockQuoteHistoryRepository;
    @Mock
    private StockFundNetValueRepository stockFundNetValueRepository;
    @Mock
    private SysUserRepository sysUserRepository;
    @Mock
    private JavaMailSender mailSender;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private StockNotificationService notificationService;

    @Test
    void shouldNormalizeMacdParamsWhenSaving() throws Exception {
        when(notificationRepository.findAllByUserIdAndStockCodeAndAssetType(1L, "600000", "STOCK"))
                .thenReturn(List.of());
        when(notificationRepository.existsByStockCodeAndAssetType("600000", "STOCK")).thenReturn(true);
        when(notificationRepository.save(any(StockNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockNotificationReqVO request = new StockNotificationReqVO();
        request.setStockCode("600000");
        request.setAssetType("STOCK");
        request.setType(NotificationType.MACD.getType());
        request.setParams("{\"condition\":\"up\",\"fastPeriod\":12,\"slowPeriod\":26,\"signalPeriod\":9}");

        notificationService.save(request, 1L);

        ArgumentCaptor<StockNotification> captor = ArgumentCaptor.forClass(StockNotification.class);
        verify(notificationRepository).save(captor.capture());
        JsonNode params = objectMapper.readTree(captor.getValue().getParams());
        assertThat(params.path("condition").asText()).isEqualTo("UP");
        assertThat(params.path("fastPeriod").asInt()).isEqualTo(12);
        assertThat(params.path("slowPeriod").asInt()).isEqualTo(26);
        assertThat(params.path("signalPeriod").asInt()).isEqualTo(9);
    }

    @Test
    void shouldRejectInvalidMacdPeriods() {
        StockNotificationReqVO request = new StockNotificationReqVO();
        request.setStockCode("600000");
        request.setAssetType("STOCK");
        request.setType(NotificationType.MACD.getType());
        request.setParams("{\"condition\":\"BOTH\",\"fastPeriod\":26,\"slowPeriod\":12,\"signalPeriod\":9}");

        assertThatThrownBy(() -> notificationService.save(request, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldNotifyOnlyOnceWhileMacdRemainsAboveSignalLine() {
        StockNotification config = new StockNotification();
        config.setId(10L);
        config.setUserId(1L);
        config.setStockCode("600000");
        config.setAssetType("STOCK");
        config.setType(NotificationType.MACD.getType());
        config.setNotifyStrategy(2);
        config.setParams("{\"condition\":\"BOTH\",\"fastPeriod\":12,\"slowPeriod\":26,\"signalPeriod\":9}");

        List<StockQuoteHistory> histories = new ArrayList<>();
        for (int i = 0; i < 88; i++) {
            histories.add(history(String.format("2026-%03d", i), BigDecimal.valueOf(200L - i)));
        }
        Collections.reverse(histories);
        when(stockQuoteHistoryRepository.findLatestByCode(anyString(), anyInt()))
                .thenAnswer(invocation -> new ArrayList<>(histories));

        notificationService.checkStockAndNotify("测试股票", new BigDecimal("300"), List.of(config));
        config.setLastNotifyAt(null);
        notificationService.checkStockAndNotify("测试股票", new BigDecimal("300"), List.of(config));

        verify(notificationRepository, times(1)).save(config);
    }

    private StockQuoteHistory history(String tradeDate, BigDecimal closePrice) {
        StockQuoteHistory result = new StockQuoteHistory();
        result.setTradeDate(tradeDate);
        result.setClosePrice(closePrice);
        return result;
    }
}
