# Northstar Trading Terminal

React dashboard for the Spring Boot trading backend. It displays NIFTY/BANKNIFTY market state, PCR/VWAP/ATR metrics, strategy signals, paper positions, and strategy history. The browser never connects directly to PostgreSQL or Redis; those credentials stay on the backend.

## Run locally (no Docker)

1. Configure the backend `.env` using `Algo-Trading-Backend/.env.example` with the managed PostgreSQL and Redis service URLs, usernames, and passwords. If your Redis provider requires TLS, change the Redis URL scheme from `redis://` to `rediss://`. Do not put database/cache credentials in this frontend folder.
2. Start the Spring backend from `Algo-Trading-Backend`:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. In another PowerShell window:

   ```powershell
   Set-Location 'C:\Users\admin\Downloads\Algo-Trading\Algo-Trading-Frontend'
   npm install
   npm run dev
   ```

4. Open `http://localhost:5173`.

The Vite development server proxies API, health, and WebSocket requests to `http://localhost:8080`. Override the proxy target with `VITE_BACKEND_TARGET` if the backend listens elsewhere. For a separately hosted frontend, set `VITE_API_BASE_URL` at build time and add its origin to backend `FRONTEND_ORIGINS`.

## Connect live data

Add Angel One settings to the backend `.env`, start the backend, then use **Connect broker** in the dashboard. The button loads instrument metadata, authenticates, and requests the market stream. Broker IP/MAC values must match the provider's registration. Without the broker setup, the dashboard still starts but displays unavailable market-data states.

Starting paper trading only enables simulated order handling. Real broker order placement is not implemented.
