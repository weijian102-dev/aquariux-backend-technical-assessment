package com.aquariux.technical.assessment.trade.service;

import java.util.List;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.Trade;

public interface TradeServiceInterface {
    TradeResponse executeTrade(TradeRequest tradeRequest);
    List<Trade> getTradeHistory(Long userId);
}