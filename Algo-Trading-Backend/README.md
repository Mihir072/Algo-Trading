# Options Analytics & Algo-Trading Backend

Spring Boot 4 / Java 21 backend for NIFTY and BANKNIFTY market analytics, strategy signals, historical backtests, and **futures-only paper trading**. Real order placement is not implemented and `realOrdersEnabled` is hard-disabled.

## Requirements and startup without Docker

- Java 21
- Managed PostgreSQL and Redis instances reachable from the backend
- Angel One SmartAPI account/API access for live data features

Create a local `.env` from the template. The application imports this ignored file when launched directly with Maven. Set the managed database JDBC URL, database username/password, and Redis URL. For Neon, use the JDBC URL form with `sslmode=require`; for Redis Cloud, use its connection URL. Set the URLs and passwords only in the local `.env`:

```powershell
Copy-Item .env.example .env
```

Set `ANGELONE_API_KEY`, `ANGELONE_CLIENT_CODE`, `ANGELONE_PIN`, `ANGELONE_TOTP_SECRET`, and the broker-required `ANGELONE_CLIENT_LOCAL_IP`, `ANGELONE_CLIENT_PUBLIC_IP`, and `ANGELONE_MAC_ADDRESS` when configuring live feed. The generated JWT, refresh, and feed tokens are held in memory only. Do not commit credentials or tokens.

Set `ANGELONE_INSTRUMENT_MASTER_ENABLED=true` and `MARKET_ANALYTICS_ENABLED=true` to enable periodic instrument-master refresh and market analytics. Configure market holidays using `MARKET_HOLIDAYS` as comma-separated ISO dates where applicable.

Start the backend directly (the database and cache are managed cloud services):

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies PostgreSQL migrations at startup. The API listens on port 8080. Health is available at `http://localhost:8080/actuator/health`.
The generated OpenAPI document and Swagger UI are available at `http://localhost:8080/v3/api-docs` and `http://localhost:8080/swagger-ui/index.html`.

## Angel One session and live market stream

1. `POST /api/v1/broker/instruments/refresh` downloads and persists supported NIFTY/BANKNIFTY index, future, and option metadata.
2. `POST /api/v1/broker/session` performs PIN/TOTP authentication. If instruments are loaded, it starts the SmartAPI binary WebSocket 2.0 stream.
3. Check `GET /api/v1/broker/market-stream`. The instrument master uses official token metadata; token values and expiry dates are not hardcoded.
4. The stream subscribes to index and current futures quotes, then subscribes to the active expiry's ATM ± configured strike-range options once it receives a spot quote.

The broker-required IP/MAC headers must reflect the actual registered client environment. They are intentionally not guessed or fetched through a third party. Provider behavior and account permissions require validation with an authorized Angel One account.

## Implemented APIs

Base path: `/api/v1`

| Area | Routes |
|---|---|
| Broker | `POST /broker/session`, `GET /broker/session`, `POST /broker/session/refresh`, `POST /broker/instruments/refresh`, `GET /broker/instruments`, `GET/POST/DELETE /broker/market-stream` |
| Market | `GET /market/underlying/{symbol}`, `/market/futures/{symbol}`, `/market/option-chain/{symbol}`, `/market/instruments/{symbol}` |
| Analytics | `GET /analytics/pcr/{symbol}`, `/analytics/vwap/{symbol}`, `/analytics/atr/{symbol}`, `/analytics/oi-levels/{symbol}`, `/analytics/greeks/{symbol}`, `POST /analytics/recalculate/{symbol}` |
| Strategy | `POST /strategy/evaluate` (explicit input), `POST /strategy/evaluate/{symbol}` (latest cached data), `GET /strategy/signal/{symbol}`, `GET /strategy/history`, `GET/PUT /strategy/config`, `GET /strategy/status`, `GET /strategy/recommendation/{symbol}` |
| Paper trading | `POST /paper-trading/start`, `/paper-trading/stop`, `/paper-trading/signals/{signalId}`, `/paper-trading/positions/{symbol}/mark`, `/paper-trading/trades/{tradeId}/close`; `GET /paper-trading/positions`, `/paper-trading/trades`, `/paper-trading/performance` |
| Backtest | `POST /backtest` with a time-ordered array of point-in-time observations |
| Frontend stream | WebSocket `/ws/market-data` |

Unavailable market data is returned with explicit `MISSING`, `STALE`, `INVALID`, or `UNKNOWN` status; a missing Greeks value is never inferred. Signal rejection reasons are included in the response. A backtest rejects observations whose option/candle timestamps occur after the simulated timestamp and enters on the next candle, not the signal candle.

## Safety and execution mode

- The backend has no live order-placement client or order-placement route.
- Paper trades simulate futures execution only; ATM options are recommendations and their premium must not be assigned futures stop/target levels.
- Paper trading must be enabled in configuration and explicitly started. Entries are restricted to the configured weekday market session, with configurable daily loss/trade limits and cooldown.
- Initial strategy thresholds are configurable test parameters and are not represented as profitable or investment advice.
- Broker credentials and live market data are not required for unit tests.

## Tests

Run the deterministic unit and Spring context tests with an in-memory H2 database:

```powershell
.\mvnw.cmd test
```
