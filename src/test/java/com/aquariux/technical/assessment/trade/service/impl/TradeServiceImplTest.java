package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPair;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private CryptoPairMapper cryptoPairMapper;

    @Mock
    private CryptoPriceMapper cryptoPriceMapper;

    @Mock
    private UserWalletMapper userWalletMapper;

    private TradeServiceImpl tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeServiceImpl(
                tradeMapper,
                cryptoPairMapper,
                cryptoPriceMapper,
                userWalletMapper
        );
    }

    @Test
    void shouldExecuteBuySuccessfully() {

        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setPairName("BTCUSDT");
        request.setTradeType(TradeType.BUY);
        request.setQuantity(new BigDecimal("0.01"));

        CryptoPair pair = new CryptoPair();
        pair.setId(1L);
        pair.setBaseSymbolId(1L);
        pair.setQuoteSymbolId(3L);
        pair.setPairName("BTCUSDT");
        pair.setActive(true);

        CryptoPrice price = new CryptoPrice();
        price.setCryptoPairId(1L);
        price.setBidPrice(new BigDecimal("3000.00"));
        price.setAskPrice(new BigDecimal("3100.00"));

        UserWallet usdtWallet = new UserWallet();
        usdtWallet.setId(10L);
        usdtWallet.setUserId(1L);
        usdtWallet.setSymbolId(3L);
        usdtWallet.setBalance(new BigDecimal("1000.00"));

        UserWallet btcWallet = new UserWallet();
        btcWallet.setId(11L);
        btcWallet.setUserId(1L);
        btcWallet.setSymbolId(1L);
        btcWallet.setBalance(new BigDecimal("0.02"));

        when(cryptoPairMapper.findByPairName("BTCUSDT"))
                .thenReturn(pair);

        when(cryptoPriceMapper.findLatestPriceByPairId(1L))
                .thenReturn(price);

        when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 3L))
                .thenReturn(usdtWallet);

        when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 1L))
                .thenReturn(btcWallet);

        doAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setId(100L);
            return 1;
        }).when(tradeMapper).insertTrade(any(Trade.class));

        TradeResponse response =
                tradeService.executeTrade(request);

        assertNotNull(response);

        assertEquals(100L, response.getTradeId());
        assertEquals(1L, response.getUserId());
        assertEquals("BTCUSDT", response.getPairName());
        assertEquals(TradeType.BUY, response.getTradeType());

        assertEquals(
                0,
                new BigDecimal("0.01")
                        .compareTo(response.getQuantity())
        );

        // BUY must execute using the ASK price.
        assertEquals(
                0,
                new BigDecimal("3100.00")
                        .compareTo(response.getPrice())
        );

        // 0.01 BTC * 3100 USDT = 31 USDT
        assertEquals(
                0,
                new BigDecimal("31.00")
                        .compareTo(response.getTotalAmount())
        );

        // USDT: 1000 - 31 = 969
        verify(userWalletMapper).updateBalance(
                eq(10L),
                eq(new BigDecimal("969.0000"))
        );

        // BTC: 0.02 + 0.01 = 0.03
        verify(userWalletMapper).updateBalance(
                eq(11L),
                eq(new BigDecimal("0.03"))
        );

        verify(tradeMapper, times(1))
                .insertTrade(any(Trade.class));
    }

    @Test
void shouldExecuteSellSuccessfully() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("BTCUSDT");
    request.setTradeType(TradeType.SELL);
    request.setQuantity(new BigDecimal("0.01"));

    CryptoPair pair = new CryptoPair();
    pair.setId(1L);
    pair.setBaseSymbolId(1L);
    pair.setQuoteSymbolId(3L);
    pair.setPairName("BTCUSDT");
    pair.setActive(true);

    CryptoPrice price = new CryptoPrice();
    price.setCryptoPairId(1L);
    price.setBidPrice(new BigDecimal("3000.00"));
    price.setAskPrice(new BigDecimal("3100.00"));

    UserWallet btcWallet = new UserWallet();
    btcWallet.setId(11L);
    btcWallet.setUserId(1L);
    btcWallet.setSymbolId(1L);
    btcWallet.setBalance(new BigDecimal("0.05"));

    UserWallet usdtWallet = new UserWallet();
    usdtWallet.setId(10L);
    usdtWallet.setUserId(1L);
    usdtWallet.setSymbolId(3L);
    usdtWallet.setBalance(new BigDecimal("1000.00"));

    when(cryptoPairMapper.findByPairName("BTCUSDT"))
            .thenReturn(pair);

    when(cryptoPriceMapper.findLatestPriceByPairId(1L))
            .thenReturn(price);

    when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 1L))
            .thenReturn(btcWallet);

    when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 3L))
            .thenReturn(usdtWallet);

    doAnswer(invocation -> {
        Trade trade = invocation.getArgument(0);
        trade.setId(101L);
        return 1;
    }).when(tradeMapper).insertTrade(any(Trade.class));

    TradeResponse response =
            tradeService.executeTrade(request);

    assertNotNull(response);
    assertEquals(TradeType.SELL, response.getTradeType());

    // SELL must use BID price
    assertEquals(
            0,
            new BigDecimal("3000.00")
                    .compareTo(response.getPrice())
    );

    // 0.01 * 3000 = 30
    assertEquals(
            0,
            new BigDecimal("30.00")
                    .compareTo(response.getTotalAmount())
    );

    // BTC: 0.05 - 0.01 = 0.04
    verify(userWalletMapper).updateBalance(
            eq(11L),
            eq(new BigDecimal("0.04"))
    );

    // USDT: 1000 + 30 = 1030
    verify(userWalletMapper).updateBalance(
            eq(10L),
            eq(new BigDecimal("1030.0000"))
    );

    verify(tradeMapper).insertTrade(any(Trade.class));
}


@Test
void shouldRejectBuyWhenBalanceIsInsufficient() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("BTCUSDT");
    request.setTradeType(TradeType.BUY);
    request.setQuantity(new BigDecimal("1"));

    CryptoPair pair = new CryptoPair();
    pair.setId(1L);
    pair.setBaseSymbolId(1L);
    pair.setQuoteSymbolId(3L);
    pair.setPairName("BTCUSDT");
    pair.setActive(true);

    CryptoPrice price = new CryptoPrice();
    price.setCryptoPairId(1L);
    price.setBidPrice(new BigDecimal("3000.00"));
    price.setAskPrice(new BigDecimal("3100.00"));

    // Only 1000 USDT, but BUY requires 3100
    UserWallet usdtWallet = new UserWallet();
    usdtWallet.setId(10L);
    usdtWallet.setUserId(1L);
    usdtWallet.setSymbolId(3L);
    usdtWallet.setBalance(new BigDecimal("1000.00"));

    when(cryptoPairMapper.findByPairName("BTCUSDT"))
            .thenReturn(pair);

    when(cryptoPriceMapper.findLatestPriceByPairId(1L))
            .thenReturn(price);

    when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 3L))
            .thenReturn(usdtWallet);

    IllegalArgumentException exception =
            assertThrows(
                    IllegalArgumentException.class,
                    () -> tradeService.executeTrade(request)
            );

    assertEquals(
            "Insufficient balance to execute BUY",
            exception.getMessage()
    );

    // Nothing should be modified.
    verify(userWalletMapper, never())
            .updateBalance(anyLong(), any(BigDecimal.class));

    verify(userWalletMapper, never())
            .insertWallet(any(UserWallet.class));

    verify(tradeMapper, never())
            .insertTrade(any(Trade.class));
}


@Test
void shouldRejectSellWhenCryptoBalanceIsInsufficient() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("BTCUSDT");
    request.setTradeType(TradeType.SELL);
    request.setQuantity(new BigDecimal("1"));

    CryptoPair pair = new CryptoPair();
    pair.setId(1L);
    pair.setBaseSymbolId(1L);
    pair.setQuoteSymbolId(3L);
    pair.setPairName("BTCUSDT");
    pair.setActive(true);

    CryptoPrice price = new CryptoPrice();
    price.setCryptoPairId(1L);
    price.setBidPrice(new BigDecimal("3000.00"));
    price.setAskPrice(new BigDecimal("3100.00"));

    // User only owns 0.05 BTC
    UserWallet btcWallet = new UserWallet();
    btcWallet.setId(11L);
    btcWallet.setUserId(1L);
    btcWallet.setSymbolId(1L);
    btcWallet.setBalance(new BigDecimal("0.05"));

    when(cryptoPairMapper.findByPairName("BTCUSDT"))
            .thenReturn(pair);

    when(cryptoPriceMapper.findLatestPriceByPairId(1L))
            .thenReturn(price);

    when(userWalletMapper.findByUserIdAndSymbolIdForUpdate(1L, 1L))
            .thenReturn(btcWallet);

    IllegalArgumentException exception =
            assertThrows(
                    IllegalArgumentException.class,
                    () -> tradeService.executeTrade(request)
            );

    assertEquals(
            "Insufficient crypto balance to execute SELL",
            exception.getMessage()
    );

    verify(userWalletMapper, never())
            .updateBalance(anyLong(), any(BigDecimal.class));

    verify(tradeMapper, never())
            .insertTrade(any(Trade.class));
}

@Test
void shouldRejectUnsupportedTradingPair() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("DOGEUSDT");
    request.setTradeType(TradeType.BUY);
    request.setQuantity(new BigDecimal("1"));

    when(cryptoPairMapper.findByPairName("DOGEUSDT"))
            .thenReturn(null);

    IllegalArgumentException exception =
            assertThrows(
                    IllegalArgumentException.class,
                    () -> tradeService.executeTrade(request)
            );

    assertEquals(
            "Unsupported trading pair: DOGEUSDT",
            exception.getMessage()
    );

    verifyNoInteractions(cryptoPriceMapper);
    verifyNoInteractions(userWalletMapper);
    verifyNoInteractions(tradeMapper);
}


@Test
void shouldRejectZeroQuantity() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("BTCUSDT");
    request.setTradeType(TradeType.BUY);
    request.setQuantity(BigDecimal.ZERO);

    IllegalArgumentException exception =
            assertThrows(
                    IllegalArgumentException.class,
                    () -> tradeService.executeTrade(request)
            );

    assertEquals(
            "Quantity must be greater than zero",
            exception.getMessage()
    );

    verifyNoInteractions(cryptoPairMapper);
    verifyNoInteractions(cryptoPriceMapper);
    verifyNoInteractions(userWalletMapper);
    verifyNoInteractions(tradeMapper);
}


@Test
void shouldRejectWhenPriceIsUnavailable() {

    TradeRequest request = new TradeRequest();
    request.setUserId(1L);
    request.setPairName("BTCUSDT");
    request.setTradeType(TradeType.BUY);
    request.setQuantity(new BigDecimal("0.01"));

    CryptoPair pair = new CryptoPair();
    pair.setId(1L);
    pair.setBaseSymbolId(1L);
    pair.setQuoteSymbolId(3L);
    pair.setPairName("BTCUSDT");
    pair.setActive(true);

    when(cryptoPairMapper.findByPairName("BTCUSDT"))
            .thenReturn(pair);

    when(cryptoPriceMapper.findLatestPriceByPairId(1L))
            .thenReturn(null);

    IllegalStateException exception =
            assertThrows(
                    IllegalStateException.class,
                    () -> tradeService.executeTrade(request)
            );

    assertEquals(
            "Price unavailable for: BTCUSDT",
            exception.getMessage()
    );

    verifyNoInteractions(userWalletMapper);
    verifyNoInteractions(tradeMapper);
}
}