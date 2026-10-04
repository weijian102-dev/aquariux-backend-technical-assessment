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
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final CryptoPairMapper cryptoPairMapper;
    private final CryptoPriceMapper cryptoPriceMapper;
    private final UserWalletMapper userWalletMapper;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {

        // 1. Validate request
        validateTradeRequest(tradeRequest);

        // 2. Find trading pair
        CryptoPair cryptoPair =
                cryptoPairMapper.findByPairName(tradeRequest.getPairName());

        if (cryptoPair == null) {
            throw new IllegalArgumentException(
                    "Unsupported trading pair: " + tradeRequest.getPairName()
            );
        }

        // 3. Get latest aggregated price
        CryptoPrice latestPrice =
                cryptoPriceMapper.findLatestPriceByPairId(cryptoPair.getId());

        if (latestPrice == null) {
            throw new IllegalStateException(
                    "Price unavailable for: " + tradeRequest.getPairName()
            );
        }

        // BUY uses ask price, SELL uses bid price
        BigDecimal executionPrice =
                tradeRequest.getTradeType() == TradeType.BUY
                        ? latestPrice.getAskPrice()
                        : latestPrice.getBidPrice();

        if (executionPrice == null ||
                executionPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid execution price");
        }

        BigDecimal totalAmount =
                tradeRequest.getQuantity().multiply(executionPrice);

        // 4. Execute wallet movement
        if (tradeRequest.getTradeType() == TradeType.BUY) {
            executeBuy(tradeRequest, cryptoPair, totalAmount);
        } else {
            executeSell(tradeRequest, cryptoPair, totalAmount);
        }

        // 5. Record immutable trade history
        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setCryptoPairId(cryptoPair.getId());
        trade.setTradeType(tradeRequest.getTradeType().name());
        trade.setQuantity(tradeRequest.getQuantity());
        trade.setPrice(executionPrice);
        trade.setTotalAmount(totalAmount);
        trade.setTradeTime(LocalDateTime.now());

        tradeMapper.insertTrade(trade);

        // 6. Build response
        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(trade.getUserId());
        response.setPairName(cryptoPair.getPairName());
        response.setTradeType(tradeRequest.getTradeType());
        response.setQuantity(trade.getQuantity());
        response.setPrice(trade.getPrice());
        response.setTotalAmount(trade.getTotalAmount());
        response.setTradeTime(trade.getTradeTime());

        return response;
    }

    private void executeBuy(
            TradeRequest tradeRequest,
            CryptoPair cryptoPair,
            BigDecimal totalAmount) {

        Long userId = tradeRequest.getUserId();

        /*
         * BUY:
         *
         * quote currency (USDT) -= totalAmount
         * base currency (BTC/ETH) += quantity
         */

        UserWallet quoteWallet =
                userWalletMapper.findByUserIdAndSymbolIdForUpdate(
                        userId,
                        cryptoPair.getQuoteSymbolId()
                );

        if (quoteWallet == null) {
            throw new IllegalArgumentException(
                    "Quote currency wallet not found"
            );
        }

        // Check whether user has enough USDT
        if (quoteWallet.getBalance().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient balance to execute BUY"
            );
        }

        BigDecimal newQuoteBalance =
                quoteWallet.getBalance().subtract(totalAmount);

        userWalletMapper.updateBalance(
                quoteWallet.getId(),
                newQuoteBalance
        );

        // User may not have a BTC/ETH wallet yet
        UserWallet baseWallet =
                userWalletMapper.findByUserIdAndSymbolIdForUpdate(
                        userId,
                        cryptoPair.getBaseSymbolId()
                );

        if (baseWallet == null) {

            baseWallet = new UserWallet();
            baseWallet.setUserId(userId);
            baseWallet.setSymbolId(cryptoPair.getBaseSymbolId());
            baseWallet.setBalance(tradeRequest.getQuantity());

            userWalletMapper.insertWallet(baseWallet);

        } else {

            BigDecimal newBaseBalance =
                    baseWallet.getBalance()
                            .add(tradeRequest.getQuantity());

            userWalletMapper.updateBalance(
                    baseWallet.getId(),
                    newBaseBalance
            );
        }
    }

    private void executeSell(
            TradeRequest tradeRequest,
            CryptoPair cryptoPair,
            BigDecimal totalAmount) {

        Long userId = tradeRequest.getUserId();

        /*
         * SELL:
         *
         * base currency (BTC/ETH) -= quantity
         * quote currency (USDT) += totalAmount
         */

        UserWallet baseWallet =
                userWalletMapper.findByUserIdAndSymbolIdForUpdate(
                        userId,
                        cryptoPair.getBaseSymbolId()
                );

        if (baseWallet == null) {
            throw new IllegalArgumentException(
                    "Crypto wallet not found"
            );
        }

        // Check whether user owns enough BTC/ETH
        if (baseWallet.getBalance()
                .compareTo(tradeRequest.getQuantity()) < 0) {

            throw new IllegalArgumentException(
                    "Insufficient crypto balance to execute SELL"
            );
        }

        BigDecimal newBaseBalance =
                baseWallet.getBalance()
                        .subtract(tradeRequest.getQuantity());

        userWalletMapper.updateBalance(
                baseWallet.getId(),
                newBaseBalance
        );

        UserWallet quoteWallet =
                userWalletMapper.findByUserIdAndSymbolIdForUpdate(
                        userId,
                        cryptoPair.getQuoteSymbolId()
                );

        if (quoteWallet == null) {

            quoteWallet = new UserWallet();
            quoteWallet.setUserId(userId);
            quoteWallet.setSymbolId(cryptoPair.getQuoteSymbolId());
            quoteWallet.setBalance(totalAmount);

            userWalletMapper.insertWallet(quoteWallet);

        } else {

            BigDecimal newQuoteBalance =
                    quoteWallet.getBalance()
                            .add(totalAmount);

            userWalletMapper.updateBalance(
                    quoteWallet.getId(),
                    newQuoteBalance
            );
        }
    }

    private void validateTradeRequest(TradeRequest tradeRequest) {

        if (tradeRequest == null) {
            throw new IllegalArgumentException(
                    "Trade request is required"
            );
        }

        if (tradeRequest.getUserId() == null) {
            throw new IllegalArgumentException(
                    "User ID is required"
            );
        }

        if (tradeRequest.getPairName() == null ||
                tradeRequest.getPairName().isBlank()) {

            throw new IllegalArgumentException(
                    "Trading pair is required"
            );
        }

        if (tradeRequest.getTradeType() == null) {
            throw new IllegalArgumentException(
                    "Trade type is required"
            );
        }

        if (tradeRequest.getQuantity() == null ||
                tradeRequest.getQuantity()
                        .compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }

    @Override
        @Transactional(readOnly = true)
        public List<Trade> getTradeHistory(Long userId) {

        if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
        }

        return tradeMapper.findByUserId(userId);
        }
}