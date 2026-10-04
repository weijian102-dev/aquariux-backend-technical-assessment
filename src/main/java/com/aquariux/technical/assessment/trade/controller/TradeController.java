package com.aquariux.technical.assessment.trade.controller;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeServiceInterface tradeService;

    @PostMapping("/execute")
    public ResponseEntity<TradeResponse> executeTrade(
            @RequestBody TradeRequest tradeRequest) {

        TradeResponse response =
                tradeService.executeTrade(tradeRequest);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Trade>> getTradeHistory(
            @PathVariable Long userId) {

        List<Trade> trades =
                tradeService.getTradeHistory(userId);

        return ResponseEntity.ok(trades);
    }
}