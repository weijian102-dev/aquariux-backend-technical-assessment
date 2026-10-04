package com.aquariux.technical.assessment.trade.service;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
       "app.price-scheduler.enabled=false"
})
class TradeConcurrencyTest {

    @Autowired
    private TradeServiceInterface tradeService;

    @Test
    void shouldHandle100SimultaneousTrades() throws Exception {

        int numberOfTrades = 100;

        /*
         * User 1 starts with 10,000 USDT.
         *
         * Keep each trade very small so all 100 BUY trades
         * can complete even when BTC price is high.
         *
         * 100 x 0.00001 BTC = 0.001 BTC total.
         */
        BigDecimal quantityPerTrade =
                new BigDecimal("0.00001");

        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfTrades);

        /*
         * All worker threads wait on this latch so that we can
         * release them at approximately the same time.
         */
        CountDownLatch startLatch =
                new CountDownLatch(1);

        /*
         * Used to make sure every worker has been submitted and
         * is ready before the test releases them.
         */
        CountDownLatch readyLatch =
                new CountDownLatch(numberOfTrades);

        List<Future<TradeResponse>> futures =
                new ArrayList<>();

        try {

            for (int i = 0; i < numberOfTrades; i++) {

                Future<TradeResponse> future =
                        executorService.submit(() -> {

                            readyLatch.countDown();

                            startLatch.await();

                            TradeRequest request =
                                    new TradeRequest();

                            request.setUserId(1L);
                            request.setPairName("BTCUSDT");
                            request.setTradeType(TradeType.BUY);
                            request.setQuantity(quantityPerTrade);

                            return tradeService.executeTrade(request);
                        });

                futures.add(future);
            }

            /*
             * Wait until all 100 tasks have reached the starting
             * point.
             */
            boolean allReady =
                    readyLatch.await(
                            10,
                            TimeUnit.SECONDS
                    );

            assertEquals(
                    true,
                    allReady,
                    "All 100 trade tasks should be ready"
            );

            /*
             * Release all 100 trades together.
             */
            startLatch.countDown();

            List<TradeResponse> responses =
                    new ArrayList<>();

            for (Future<TradeResponse> future : futures) {

                TradeResponse response =
                        future.get(
                                30,
                                TimeUnit.SECONDS
                        );

                responses.add(response);
            }

            /*
             * Every trade should have completed.
             */
            assertEquals(
                    numberOfTrades,
                    responses.size()
            );

            /*
             * Verify each completed trade has a database ID
             * and valid execution information.
             */
            for (TradeResponse response : responses) {

                assertNotNull(response);
                assertNotNull(response.getTradeId());
                assertNotNull(response.getPrice());
                assertNotNull(response.getTradeTime());

                assertEquals(
                        TradeType.BUY,
                        response.getTradeType()
                );

                assertEquals(
                        "BTCUSDT",
                        response.getPairName()
                );

                assertEquals(
                        0,
                        quantityPerTrade.compareTo(
                                response.getQuantity()
                        )
                );
            }

            /*
             * Verify that all 100 trades were written
             * to trade history.
             *
             * The database already contains seeded trades,
             * therefore we do not expect history size to be
             * exactly 100.
             */
            long concurrentTrades =
                    tradeService
                            .getTradeHistory(1L)
                            .stream()
                            .filter(trade ->
                                    "BUY".equals(
                                            trade.getTradeType()
                                    )
                            )
                            .filter(trade ->
                                    trade.getCryptoPairId()
                                            .equals(1L)
                            )
                            .filter(trade ->
                                    trade.getQuantity()
                                            .compareTo(
                                                    quantityPerTrade
                                            ) == 0
                            )
                            .count();

            assertEquals(
                    numberOfTrades,
                    concurrentTrades,
                    "All 100 concurrent trades should be recorded"
            );

        } finally {

            executorService.shutdownNow();

            executorService.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            );
        }
    }
}