# Aquariux Trading System – Production Release Notes

**Release Type:** Feature Release
**Component:** Backend Trading Service
**Release Status:** Ready for Deployment
**Affected Services:** Trading API, Wallet Management, Market Price Aggregation
**Database Migration Required:** No new migration required
**Downtime Required:** No planned downtime

---

## 1. Release Summary

This release introduces the core cryptocurrency trading capabilities for the Aquariux Trading System.

The release enables users to execute BTCUSDT and ETHUSDT BUY/SELL transactions using aggregated market prices from Binance and Huobi. It also introduces transactional wallet management, trade history, scheduled market-price aggregation, concurrency protection, and standardized API error handling.

The implementation has been validated through functional testing, unit testing, and concurrent trade execution testing.

---

## 2. Scope of Release

### Trading Operations

Implemented trade execution for the following supported trading pairs:

- BTCUSDT
- ETHUSDT

Trade execution follows the aggregated market price:

- **BUY** orders execute against the lowest available ask price.
- **SELL** orders execute against the highest available bid price.

Before execution, the service validates the requested quantity and available wallet balance.

Trade execution, wallet balance updates, and trade persistence are executed within a database transaction.

---

## 3. Market Price Aggregation

Implemented scheduled market-price retrieval from:

- Binance
- Huobi

Market prices are refreshed every 10 seconds.

For each supported trading pair, the system determines:

- **Best Bid:** Highest bid available across supported exchanges.
- **Best Ask:** Lowest ask available across supported exchanges.

The selected bid/ask price and originating exchange are persisted to price history.

### Corrections Included

The following price aggregation issues were corrected:

- Corrected BTCUSDT and ETHUSDT database pair mapping.
- Corrected bid and ask price persistence.
- Corrected bid and ask source persistence.
- Enabled Spring scheduled execution for market-price updates.

The scheduler can be disabled through application configuration for isolated automated testing.

---

## 4. Wallet Management

Wallet processing has been integrated into trade execution.

### BUY

When executing a BUY:

1. Validate sufficient quote-currency balance.
2. Deduct the transaction amount from the quote wallet.
3. Credit the purchased cryptocurrency to the base wallet.
4. Create the cryptocurrency wallet when the user acquires that asset for the first time.

### SELL

When executing a SELL:

1. Validate sufficient cryptocurrency balance.
2. Deduct the sold quantity from the base wallet.
3. Credit the proceeds to the quote-currency wallet.

Wallet updates are performed within the same transaction as trade creation.

Database row locking (`SELECT FOR UPDATE`) is used during balance modification to prevent concurrent transactions from operating against stale wallet balances.

---

## 5. Trade History and Auditability

All successfully executed trades are persisted with:

- User ID
- Cryptocurrency pair
- Trade type
- Quantity
- Execution price
- Total transaction amount
- Execution timestamp

A trade-history API has been added to retrieve transactions for a user.

Results are returned in reverse chronological order, with the latest transaction returned first.

No trade update or deletion functionality has been introduced, preserving transaction history as an audit record.

---

## 6. API Error Handling

Centralized REST exception handling has been introduced.

The API now provides controlled responses for scenarios including:

- Invalid trade requests
- Unsupported trading pairs
- Invalid quantities
- Insufficient wallet balances
- Missing market prices
- Invalid execution prices

Business validation failures return appropriate HTTP error responses instead of unhandled application exceptions.

---

## 7. Concurrency and Transaction Integrity

Trade processing uses Spring transactional boundaries together with database row locking.

This protects wallet balances when multiple transactions are executed against the same wallet concurrently.

A concurrency integration test was implemented using **100 simultaneous trade executions** against the trading service.

### Validation Result

**100 simultaneous trades**

Result:

**PASS**

All 100 transactions completed successfully during the test with no test failures or errors.

---

## 8. Automated Testing

Unit tests cover the primary trading scenarios:

- Successful BTC BUY
- Successful BTC SELL
- BUY with insufficient balance
- SELL with insufficient cryptocurrency balance
- Unsupported trading pair
- Zero/invalid quantity
- Market price unavailable

Integration testing additionally covers:

- 100 simultaneous trade executions
- Transactional wallet locking
- Trade persistence under concurrent execution

External market-price scheduling is disabled during the concurrency test to isolate the trading workflow from Binance/Huobi network dependencies.

### Build Verification

Command:

`mvn clean test`

Result:

**BUILD SUCCESS**

---

## 9. Functional Verification

The following functionality was manually validated:

| Test Scenario | Result |
|---|---|
| Binance market-price retrieval | PASS |
| Huobi market-price retrieval | PASS |
| Scheduled price refresh | PASS |
| BTCUSDT pair mapping | PASS |
| ETHUSDT pair mapping | PASS |
| Highest bid selection | PASS |
| Lowest ask selection | PASS |
| BUY execution using best ask | PASS |
| SELL execution using best bid | PASS |
| Wallet debit/credit | PASS |
| Insufficient balance rejection | PASS |
| Trade persistence | PASS |
| Trade history retrieval | PASS |
| 100 concurrent trades | PASS |
| Full automated test suite | PASS |

---

## 10. Deployment Impact

### Application

Application restart/redeployment is required for the new backend code.

### Database

No additional database migration has been introduced as part of this implementation.

Existing tables used include:

- `crypto_pairs`
- `crypto_prices`
- `trades`
- `user_wallets`
- `users`
- `symbols`

### External Dependencies

Runtime market-price aggregation depends on connectivity to:

- Binance market API
- Huobi market API

Failure to retrieve prices from an individual provider is logged and does not terminate the application.

### Configuration

Market-price scheduling is enabled by default.

It can be disabled using:

`app.price-scheduler.enabled=false`

This is primarily used to prevent external market API calls during isolated automated testing.

---

## 11. Deployment Procedure

1. Deploy the approved application build.
2. Start the Spring Boot application.
3. Confirm application startup completes successfully.
4. Confirm the price scheduler starts.
5. Confirm Binance and Huobi price retrieval succeeds.
6. Confirm BTCUSDT and ETHUSDT prices are persisted.
7. Perform API health/smoke testing.
8. Execute a controlled BUY transaction.
9. Verify wallet balance adjustment.
10. Execute a controlled SELL transaction.
11. Verify wallet balance adjustment.
12. Verify both transactions appear in trade history.
13. Monitor application logs for scheduler, database, or transaction errors.

---

## 12. Post-Deployment Validation

Following deployment, verify:

**Market Data**
- BTCUSDT and ETHUSDT prices are refreshed periodically.
- Price timestamps reflect the current scheduler execution.
- Bid and ask prices are associated with the correct trading pair.
- Exchange sources are recorded correctly.

**Trading**
- BUY uses the latest aggregated ask price.
- SELL uses the latest aggregated bid price.
- Insufficient-balance transactions are rejected.
- Successful transactions create trade records.

**Wallet**
- BUY deducts USDT and credits cryptocurrency.
- SELL deducts cryptocurrency and credits USDT.
- Wallet balances remain consistent after transactions.

**Audit**
- New trades appear in trade history.
- Trade timestamps and execution values are persisted correctly.

---

## 13. Rollback Plan

If a critical issue is identified after deployment:

1. Stop or remove the affected application deployment from service.
2. Redeploy the previous stable application version.
3. Verify the previous version starts successfully.
4. Confirm API availability.
5. Confirm wallet and trade-history access.
6. Review trades executed during the affected release window before performing any data remediation.

Because trade records represent financial transactions, executed trade records should **not be automatically deleted or modified as part of application rollback**.

Any required transaction-data remediation should be reviewed separately.

---

## 14. Known Operational Considerations

Market-price availability depends on external exchange API availability.

The price scheduler logs failures when an exchange cannot be reached. Existing historical prices remain persisted in the database.

Trading operations depend on the latest available persisted market price. Monitoring should therefore include scheduler execution and market-price freshness.

The application currently supports:

- BTCUSDT
- ETHUSDT

Additional trading pairs require corresponding pair configuration and market-price support.

---

## 15. Release Acceptance Criteria

The release is considered successfully deployed when:

- Application starts without critical errors.
- Scheduled market-price aggregation is operational.
- BTCUSDT and ETHUSDT prices are updated.
- BUY and SELL transactions execute successfully.
- Wallet balances update correctly.
- Invalid/insufficient-balance transactions are rejected.
- Trade history is persisted and retrievable.
- Automated tests pass.
- No critical errors are observed during post-deployment verification.

---

**Release Decision:** Ready for deployment following final code review and successful build verification.