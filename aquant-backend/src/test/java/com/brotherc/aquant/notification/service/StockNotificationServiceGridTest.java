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
class StockNotificationServiceGridTest {

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
    void shouldNormalizeGridParamsWhenSaving() throws Exception {
        when(notificationRepository.findAllByUserIdAndStockCodeAndAssetType(1L, "600000", "STOCK"))
                .thenReturn(List.of());
        when(notificationRepository.existsByStockCodeAndAssetType("600000", "STOCK")).thenReturn(true);
        when(notificationRepository.save(any(StockNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockNotificationReqVO request = new StockNotificationReqVO();
        request.setStockCode("600000");
        request.setAssetType("STOCK");
        request.setType(NotificationType.GRID.getType());
        request.setParams("{\"condition\":\"buy\",\"gridPercent\":3,\"gridCount\":5}");

        notificationService.save(request, 1L);

        ArgumentCaptor<StockNotification> captor = ArgumentCaptor.forClass(StockNotification.class);
        verify(notificationRepository).save(captor.capture());
        JsonNode params = objectMapper.readTree(captor.getValue().getParams());
        assertThat(params.path("condition").asText()).isEqualTo("BUY");
        assertThat(params.path("gridPercent").decimalValue()).isEqualByComparingTo("3");
        assertThat(params.path("gridCount").asInt()).isEqualTo(5);
    }

    @Test
    void shouldRejectInvalidGridParams() {
        StockNotificationReqVO request = new StockNotificationReqVO();
        request.setStockCode("600000");
        request.setAssetType("STOCK");
        request.setType(NotificationType.GRID.getType());
        request.setParams("{\"condition\":\"BOTH\",\"gridPercent\":50,\"gridCount\":5}");

        assertThatThrownBy(() -> notificationService.save(request, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldNotNotifyTwiceWhilePriceStaysInsideTriggeredGrid() {
        StockNotification config = new StockNotification();
        config.setId(9L);
        config.setUserId(1L);
        config.setStockCode("600000");
        config.setAssetType("STOCK");
        config.setType(NotificationType.GRID.getType());
        config.setNotifyStrategy(2);
        config.setParams("{\"condition\":\"BOTH\",\"gridPercent\":3,\"gridCount\":5}");

        StockQuoteHistory newest = history("2026-09-05", "100");
        StockQuoteHistory oldest = history("2026-09-04", "100");
        when(stockQuoteHistoryRepository.findLatestByCode(anyString(), anyInt()))
                .thenAnswer(invocation -> new ArrayList<>(List.of(newest, oldest)));

        notificationService.checkStockAndNotify("测试股票", new BigDecimal("96"), List.of(config));
        config.setLastNotifyAt(null);
        notificationService.checkStockAndNotify("测试股票", new BigDecimal("96"), List.of(config));

        verify(notificationRepository, times(1)).save(config);
    }

    private StockQuoteHistory history(String tradeDate, String closePrice) {
        StockQuoteHistory result = new StockQuoteHistory();
        result.setTradeDate(tradeDate);
        result.setClosePrice(new BigDecimal(closePrice));
        return result;
    }
}
