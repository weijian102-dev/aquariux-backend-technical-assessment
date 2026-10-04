# Design Decisions

## 1. Overview

This document describes the main design decisions and assumptions made while implementing the Aquariux cryptocurrency trading backend.

The implementation focuses on correctness, transaction integrity, concurrency safety, maintainability, and the functional requirements defined in the technical assessment.

---

## 2. Supported Trading Pairs

The trading service currently supports:

- BTCUSDT
- ETHUSDT

Trading pairs are resolved using the existing `crypto_pairs` configuration rather than hard-coded database IDs.

This ensures market prices and trades are associated with the correct configured trading pair.

---

## 3. Market Price Aggregation

Market prices are retrieved from:

- Binance
- Huobi

The application periodically retrieves market prices from both exchanges and determines the best available market price for each supported trading pair.

The aggregation rules are:

- **Best Bid:** Highest bid across the available exchanges.
- **Best Ask:** Lowest ask across the available exchanges.

The bid and ask may originate from different exchanges.

For example:

```text
Binance Bid: 85,000
Binance Ask: 85,010

Huobi Bid:   84,990
Huobi Ask:   85,005

Best Bid:    85,000 - Binance
Best Ask:    85,005 - Huobi
```

Both the selected price and its originating exchange are persisted.

---

## 4. Price Refresh Strategy

Market prices are refreshed using a Spring scheduled task.

The current refresh interval is:

```text
10 seconds
```

Each successful refresh creates a new price-history record instead of replacing the previous record.

This preserves historical pricing information and allows trading operations to use the latest persisted aggregated price.

The scheduler is enabled by default.

It can be disabled using:

```properties
app.price-scheduler.enabled=false
```

This configuration is primarily used by automated tests to prevent external exchange calls from affecting isolated test execution.

---

## 5. Trade Execution Price

Trades execute using the latest aggregated market price stored by the price-discovery process.

### BUY

A BUY executes using the:

```text
Best Ask Price
```

This represents the lowest available selling price across the supported exchanges.

### SELL

A SELL executes using the:

```text
Best Bid Price
```

This represents the highest available buying price across the supported exchanges.

---

## 6. Transactional Trade Execution

Trade execution is performed inside a database transaction.

The following operations form one logical transaction:

1. Validate the trade request.
2. Resolve the trading pair.
3. Retrieve the latest market price.
4. Determine the execution price.
5. Validate the available wallet balance.
6. Debit the source wallet.
7. Credit the destination wallet.
8. Persist the trade record.

If an exception occurs during execution, the transaction is rolled back.

This prevents scenarios where wallet balances are modified without the corresponding trade being recorded.

---

## 7. Wallet Management

Wallet balances are stored separately for each user and cryptocurrency symbol.

### BUY

For a BUY transaction:

- The quote-currency wallet is debited.
- The base cryptocurrency wallet is credited.

For example:

```text
BUY BTCUSDT

USDT balance decreases.
BTC balance increases.
```

If the user does not currently have a wallet for the acquired cryptocurrency, the wallet is created when the cryptocurrency is first acquired.

### SELL

For a SELL transaction:

- The base cryptocurrency wallet is debited.
- The quote-currency wallet is credited.

A trade is rejected when the user does not have sufficient available balance.

---

## 8. Concurrency Control

Wallet balances represent financial values and must remain consistent when multiple trades are submitted concurrently.

The implementation uses:

```text
@Transactional
```

together with database row locking:

```sql
SELECT ... FOR UPDATE
```

Wallet rows are locked while their balances are being modified.

This prevents concurrent transactions from reading the same balance and independently spending funds based on stale values.

The implementation was validated using an integration test that submits:

```text
100 simultaneous trades
```

against the trading service.

The concurrency test completed successfully without test failures or errors.

---

## 9. Financial Calculations

Financial values use:

```text
BigDecimal
```

rather than floating-point types such as `double`.

This avoids binary floating-point precision issues when handling:

- Cryptocurrency quantities
- Market prices
- Wallet balances
- Trade totals

The existing database decimal precision is retained for persisted financial values.

---

## 10. Trade History and Immutability

Every successfully executed trade creates a transaction record containing:

- User
- Cryptocurrency pair
- Trade type
- Quantity
- Execution price
- Total amount
- Execution timestamp

Trade history is returned newest-first.

No trade update or delete operation is exposed by the implementation.

This ensures completed transactions remain available as an audit history.

---

## 11. Error Handling

Centralized API exception handling is used to provide controlled HTTP responses for expected business errors.

Examples include:

- Missing user ID
- Missing trading pair
- Missing trade type
- Invalid quantity
- Unsupported trading pair
- Insufficient wallet balance
- Missing market price
- Invalid execution price

Business validation failures are therefore returned as controlled API responses rather than unhandled server exceptions.

---

## 12. External Exchange Failure Handling

Binance and Huobi are external dependencies and may become temporarily unavailable.

Exchange calls are therefore handled independently.

If an exchange request fails:

- The failure is logged.
- The scheduler does not terminate.
- Available exchange information can still be processed where possible.
- Previously stored historical price records remain available.

If no valid price can be obtained during a scheduler execution, a new invalid price record is not created.

---

## 13. Testing Strategy

The implementation uses both unit and integration testing.

### Unit Tests

Unit tests cover trading business logic including:

- Successful BUY
- Successful SELL
- Insufficient BUY balance
- Insufficient SELL balance
- Unsupported trading pair
- Invalid quantity
- Unavailable market price

External dependencies and database mappers are mocked for these tests.

### Concurrency Integration Test

A Spring integration test exercises the actual transactional and database-locking behaviour.

The test submits 100 concurrent trades and verifies that all expected transactions complete successfully.

External market-price scheduling is disabled during this test so that Binance and Huobi network calls do not affect the result.

---

## 14. Authentication and Authorization

Authentication and user-account creation are treated as outside the scope of this implementation based on the assessment assumption that the user is already authenticated and secured.

The trading implementation therefore accepts the provided user ID and focuses on trading authorization assumptions, wallet balances, transaction integrity, and trade processing.

In a production deployment, the user identity should normally be obtained from the authenticated security context rather than trusted directly from request input.

---

## 15. Database Design

The implementation uses the database schema provided with the assessment.

No additional schema migration was required for the implemented functionality.

Existing tables are used for:

- Users
- Symbols
- Cryptocurrency pairs
- Wallets
- Price history
- Trades

The existing Flyway migration and H2 setup are retained.

---

## 16. In-Memory Database Behaviour

The provided application uses an H2 in-memory database.

As a result, runtime data created during local execution is reset when the application is restarted.

Flyway migrations and seed data are reapplied when a new application instance starts.

This behaviour is appropriate for the assessment environment but would be replaced by a persistent database in a production deployment.

---

## 17. Performance and Scalability

The trading path avoids calling external exchanges during trade execution.

Exchange prices are retrieved asynchronously by the scheduled price-discovery process and persisted beforehand.

Trade execution therefore reads the latest persisted price instead of waiting for Binance or Huobi API responses.

This reduces external network dependency and latency on the trading API path.

Concurrency safety is provided through transactional database locking.

The implementation has been tested with 100 simultaneous trade operations as required by the assessment.

---

## 18. Assumptions

The following assumptions were made