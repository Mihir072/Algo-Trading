CREATE TABLE instrument (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    token VARCHAR(64) NOT NULL,
    exchange VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(32) NOT NULL,
    expiry DATE,
    strike NUMERIC(12, 2),
    option_type VARCHAR(2),
    lot_size INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_instrument_exchange_token UNIQUE (exchange, token)
);

CREATE INDEX idx_instrument_symbol_expiry
    ON instrument (symbol, expiry);

CREATE TABLE market_tick (
    id BIGSERIAL PRIMARY KEY,
    instrument_token VARCHAR(64) NOT NULL,
    ltp NUMERIC(18, 6) NOT NULL,
    last_traded_quantity BIGINT,
    volume BIGINT,
    open_interest BIGINT,
    timestamp TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_market_tick_token_timestamp UNIQUE (instrument_token, timestamp)
);

CREATE INDEX idx_market_tick_token_timestamp
    ON market_tick (instrument_token, timestamp DESC);

CREATE TABLE option_snapshot (
    id BIGSERIAL PRIMARY KEY,
    underlying VARCHAR(32) NOT NULL,
    expiry DATE NOT NULL,
    atm_strike NUMERIC(12, 2) NOT NULL,
    selected_strikes JSONB NOT NULL,
    contract_snapshots JSONB NOT NULL,
    total_ce_oi BIGINT NOT NULL,
    total_pe_oi BIGINT NOT NULL,
    ce_change_oi BIGINT,
    pe_change_oi BIGINT,
    pcr_oi NUMERIC(18, 8),
    pcr_change_oi NUMERIC(18, 8),
    support_strike NUMERIC(12, 2),
    support_oi BIGINT,
    resistance_strike NUMERIC(12, 2),
    resistance_oi BIGINT,
    data_status VARCHAR(16) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_option_snapshot_underlying_time
    ON option_snapshot (underlying, captured_at DESC);

CREATE TABLE futures_candle (
    id BIGSERIAL PRIMARY KEY,
    instrument_token VARCHAR(64) NOT NULL,
    open NUMERIC(18, 6) NOT NULL,
    high NUMERIC(18, 6) NOT NULL,
    low NUMERIC(18, 6) NOT NULL,
    close NUMERIC(18, 6) NOT NULL,
    volume BIGINT NOT NULL,
    open_interest BIGINT,
    timestamp TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_futures_candle_token_timestamp UNIQUE (instrument_token, timestamp)
);

CREATE INDEX idx_futures_candle_token_timestamp
    ON futures_candle (instrument_token, timestamp DESC);

CREATE TABLE strategy_signal (
    id BIGSERIAL PRIMARY KEY,
    underlying VARCHAR(32) NOT NULL,
    signal VARCHAR(16) NOT NULL,
    entry NUMERIC(18, 6),
    vwap NUMERIC(18, 6),
    atr NUMERIC(18, 6),
    pcr_oi NUMERIC(18, 8),
    pcr_change_oi NUMERIC(18, 8),
    pcr_trend VARCHAR(16) NOT NULL,
    support NUMERIC(18, 6),
    resistance NUMERIC(18, 6),
    stop_loss NUMERIC(18, 6),
    target NUMERIC(18, 6),
    reward_risk NUMERIC(18, 8),
    reasons TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_strategy_signal_value CHECK (signal IN ('LONG', 'SHORT', 'NO_TRADE'))
);

CREATE INDEX idx_strategy_signal_underlying_time
    ON strategy_signal (underlying, captured_at DESC);

CREATE TABLE paper_trade (
    id BIGSERIAL PRIMARY KEY,
    signal_id BIGINT NOT NULL REFERENCES strategy_signal (id),
    underlying VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    entry_price NUMERIC(18, 6) NOT NULL,
    exit_price NUMERIC(18, 6),
    stop_loss NUMERIC(18, 6) NOT NULL,
    target NUMERIC(18, 6) NOT NULL,
    quantity INTEGER NOT NULL,
    gross_pnl NUMERIC(18, 6),
    estimated_charges NUMERIC(18, 6),
    slippage NUMERIC(18, 6),
    net_pnl NUMERIC(18, 6),
    status VARCHAR(16) NOT NULL,
    exit_reason VARCHAR(32),
    entry_time TIMESTAMPTZ NOT NULL,
    exit_time TIMESTAMPTZ,
    CONSTRAINT chk_paper_trade_direction CHECK (direction IN ('LONG', 'SHORT')),
    CONSTRAINT chk_paper_trade_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_paper_trade_underlying_status
    ON paper_trade (underlying, status);

CREATE INDEX idx_paper_trade_entry_time
    ON paper_trade (entry_time DESC);
