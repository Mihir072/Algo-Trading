import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'

const SYMBOLS = ['NIFTY', 'BANKNIFTY']
const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const TODAY_LABEL = new Date().toLocaleDateString('en-IN', { weekday: 'short', day: '2-digit', month: 'short' })

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}/api/v1${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(payload?.message || `Request failed (${response.status})`)
  }
  return payload
}

function formatNumber(value, digits = 2) {
  const number = Number(value)
  return Number.isFinite(number)
    ? number.toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
    : '--'
}

function formatCompact(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(number) : '--'
}

function formatTime(value) {
  if (!value) return 'Waiting for data'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Waiting for data' : date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function getMarketStatus(update) {
  const spotAt = update?.spotTimestamp ? new Date(update.spotTimestamp).getTime() : NaN
  const futuresAt = update?.futuresTimestamp ? new Date(update.futuresTimestamp).getTime() : NaN
  const now = Date.now()
  if (!Number.isFinite(spotAt) || !Number.isFinite(futuresAt)) return 'MISSING'
  const spotAge = now - spotAt
  const futuresAge = now - futuresAt
  return spotAge >= 0 && spotAge <= 60_000 && futuresAge >= 0 && futuresAge <= 60_000
    ? 'VALID'
    : 'STALE'
}

function Icon({ name, size = 18 }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    chart: <><path d="M3 3v18h18" /><path d="m7 14 4-4 4 3 6-7" /></>,
    layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5" /><path d="m3 16 9 5 9-5" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="m19.4 15 .1.1 1.4 1.1-1.4 2.4-1.7-.7a8 8 0 0 1-1.8 1l-.3 1.8h-2.8l-.3-1.8a8 8 0 0 1-1.8-1l-1.7.7-1.4-2.4L7.1 15a8 8 0 0 1 0-2l-1.4-1.1 1.4-2.4 1.7.7a8 8 0 0 1 1.8-1l.3-1.8h2.8l.3 1.8a8 8 0 0 1 1.8 1l1.7-.7 1.4 2.4-1.4 1.1a8 8 0 0 1-.1 2Z" /></>,
    pulse: <><path d="M3 12h4l3-8 4 16 3-8h4" /></>,
    arrow: <><path d="M7 17 17 7" /><path d="M8 7h9v9" /></>,
    refresh: <><path d="M20 7v5h-5" /><path d="M4 17v-5h5" /><path d="M5.6 9a7 7 0 0 1 11.6-2L20 12M4 12l2.8 5a7 7 0 0 0 11.6-2" /></>,
    plug: <><path d="M12 22v-5" /><path d="M9 8V2M15 8V2" /><path d="M18 8v4a6 6 0 0 1-12 0V8Z" /></>,
    shield: <><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" /><path d="m9 12 2 2 4-4" /></>,
    menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function MetricCard({ label, value, detail, tone = 'default', icon }) {
  return (
    <article className={`metric-card ${tone}`}>
      <div className="metric-top">
        <span>{label}</span>
        <span className="metric-icon"><Icon name={icon} size={16} /></span>
      </div>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  )
}

function PriceChart({ candles = [] }) {
  const values = candles.map((candle) => Number(candle.close)).filter(Number.isFinite)
  if (values.length < 2) {
    return <div className="chart-empty"><span className="chart-empty-icon"><Icon name="chart" size={20} /></span><span>Price history will appear when live market data is connected.</span></div>
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const points = values.map((value, index) => `${(index / (values.length - 1)) * 100},${94 - ((value - min) / range) * 82}`).join(' ')
  const first = values[0]
  const last = values.at(-1)
  const rising = last >= first
  return (
    <div className="chart-wrap">
      <div className="chart-labels"><span>{formatNumber(max, 0)}</span><span>{formatNumber((max + min) / 2, 0)}</span><span>{formatNumber(min, 0)}</span></div>
      <svg className="price-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="Recent futures candle close prices">
        <defs><linearGradient id="chartFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor={rising ? '#34d399' : '#fb7185'} stopOpacity=".22" /><stop offset="100%" stopColor={rising ? '#34d399' : '#fb7185'} stopOpacity="0" /></linearGradient></defs>
        <path d={`M ${points} L 100 100 L 0 100 Z`} fill="url(#chartFill)" />
        <polyline points={points} fill="none" stroke={rising ? '#34d399' : '#fb7185'} strokeWidth="1.15" vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="chart-time"><span>Earlier</span><span>Latest</span></div>
    </div>
  )
}

function App() {
  const [symbol, setSymbol] = useState('NIFTY')
  const [market, setMarket] = useState(null)
  const [signals, setSignals] = useState([])
  const [positions, setPositions] = useState([])
  const [performance, setPerformance] = useState(null)
  const [broker, setBroker] = useState(null)
  const [strategyStatus, setStrategyStatus] = useState(null)
  const [stream, setStream] = useState(null)
  const [health, setHealth] = useState('checking')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [lastUpdated, setLastUpdated] = useState(null)
  const [activeNav, setActiveNav] = useState('Overview')
  const [mobileMenu, setMobileMenu] = useState(false)

  const refreshDashboard = useCallback(async () => {
    const healthRequest = fetch(`${API_BASE}/actuator/health`).then(async (response) => {
      if (!response.ok) throw new Error('Backend health check failed')
      return response.json()
    })
    const requests = [
      request(`/market/underlying/${symbol}`).catch(() => null),
      request('/strategy/history').catch(() => []),
      request('/paper-trading/positions').catch(() => []),
      request('/paper-trading/performance').catch(() => null),
      request('/broker/session').catch(() => null),
      request('/broker/market-stream').catch(() => null),
      request('/strategy/status').catch(() => null),
    ]
    const [healthResult, marketResult, signalResult, positionResult, perfResult, brokerResult, streamResult, strategyResult] =
      await Promise.allSettled([healthRequest, ...requests])
    setHealth(healthResult.status === 'fulfilled' && healthResult.value.status === 'UP' ? 'online' : 'offline')
    setMarket(marketResult.status === 'fulfilled' ? marketResult.value : null)
    setSignals(signalResult.status === 'fulfilled' && Array.isArray(signalResult.value) ? signalResult.value : [])
    setPositions(positionResult.status === 'fulfilled' && Array.isArray(positionResult.value) ? positionResult.value : [])
    setPerformance(perfResult.status === 'fulfilled' ? perfResult.value : null)
    setBroker(brokerResult.status === 'fulfilled' ? brokerResult.value : null)
    setStream(streamResult.status === 'fulfilled' ? streamResult.value : null)
    setStrategyStatus(strategyResult.status === 'fulfilled' ? strategyResult.value : null)
    setLastUpdated(new Date())
  }, [symbol])

  useEffect(() => {
    const initialRefresh = window.setTimeout(refreshDashboard, 0)
    const interval = window.setInterval(refreshDashboard, 15000)
    return () => {
      window.clearTimeout(initialRefresh)
      window.clearInterval(interval)
    }
  }, [refreshDashboard])

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socketBase = API_BASE
      ? `${new URL(API_BASE, window.location.origin).protocol === 'https:' ? 'wss:' : 'ws:'}//${new URL(API_BASE, window.location.origin).host}`
      : `${protocol}//${window.location.host}`
    const socket = new WebSocket(`${socketBase}/ws/market-data`)
    socket.onmessage = (event) => {
      try {
        const update = JSON.parse(event.data)
        if (update.underlying?.toUpperCase() === symbol) {
          setMarket({ underlying: symbol, status: getMarketStatus(update), updatedAt: update.updatedAt, data: update })
          setLastUpdated(new Date())
        }
      } catch {
        setError('Received an unreadable market-data update.')
      }
    }
    socket.onerror = () => setStream((current) => current ? { ...current, frontendConnected: false } : current)
    return () => socket.close()
  }, [symbol])

  const runAction = async (label, action) => {
    setBusy(label)
    setError('')
    try {
      await action()
      await refreshDashboard()
    } catch (actionError) {
      setError(actionError.message || 'The requested action failed.')
    } finally {
      setBusy('')
    }
  }

  const marketData = market?.data || null
  const chain = marketData?.optionChain
  const vwap = marketData?.vwap
  const atr = marketData?.atr
  const oiLevels = marketData?.oiLevels
  const latestSignal = marketData?.latestSignal || signals.find((item) => item.underlying?.toUpperCase() === symbol)
  const candles = useMemo(() => marketData?.futuresCandles || [], [marketData])
  const currentPrice = marketData?.futuresPrice ?? marketData?.spotPrice
  const netPnl = performance?.netPnl
  const navItems = [
    { label: 'Overview', icon: 'grid' },
    { label: 'Markets', icon: 'chart' },
    { label: 'Strategy', icon: 'pulse' },
    { label: 'Paper trading', icon: 'layers' },
  ]

  const connectBroker = () => runAction('Connecting broker', async () => {
    await request('/broker/instruments/refresh', { method: 'POST' })
    await request('/broker/session', { method: 'POST' })
    await request('/broker/market-stream/connect', { method: 'POST' })
  })

  const togglePaperTrading = () => runAction(strategyStatus?.paperTradingRunning ? 'Stopping paper trading' : 'Starting paper trading', async () => {
    await request(`/paper-trading/${strategyStatus?.paperTradingRunning ? 'stop' : 'start'}`, { method: 'POST' })
  })
  const signalDirection = latestSignal?.direction || latestSignal?.signal
  const signalReasons = Array.isArray(latestSignal?.reasons)
    ? latestSignal.reasons
    : typeof latestSignal?.reasons === 'string'
      ? latestSignal.reasons.split(/\r?\n/).filter(Boolean)
      : []

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileMenu ? 'sidebar-open' : ''}`}>
        <a className="brand" href="#overview" onClick={() => setActiveNav('Overview')}>
          <span className="brand-mark"><Icon name="pulse" size={20} /></span>
          <span><strong>Northstar</strong><small>TRADING TERMINAL</small></span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="main-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <button key={item.label} className={activeNav === item.label ? 'nav-item active' : 'nav-item'} onClick={() => { setActiveNav(item.label); setMobileMenu(false); document.getElementById(item.label === 'Overview' ? 'overview' : item.label === 'Markets' ? 'market-panel' : item.label === 'Strategy' ? 'strategy-panel' : 'paper-panel')?.scrollIntoView({ behavior: 'smooth' }) }}>
              <Icon name={item.icon} size={18} /><span>{item.label}</span>
              {item.label === 'Paper trading' && <span className="nav-count">{positions.length}</span>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note"><span className="note-icon"><Icon name="shield" size={17} /></span><strong>Paper mode only</strong><p>Live order placement is not enabled. Trades shown here are simulations.</p></div>
          <div className="profile">
            <div className="avatar">AT</div>
            <div><strong>Algo Trader</strong><small>Local workspace</small></div>
            <span className="profile-status" title="Application status" />
          </div>
        </div>
      </aside>

      <main className="main-content" id="overview">
        <header className="topbar">
          <button className="mobile-menu" aria-label="Toggle navigation" onClick={() => setMobileMenu(!mobileMenu)}><Icon name="menu" /></button>
          <div className="breadcrumb"><span>Workspace</span><b>/</b><strong>{activeNav}</strong></div>
          <div className="topbar-right">
            <span className="market-hours"><i className={`status-dot ${health === 'online' ? 'green' : health === 'offline' ? 'red' : ''}`} />{health === 'online' ? (broker?.authenticated ? 'Broker session active' : 'Backend connected') : health === 'offline' ? 'Backend offline' : 'Connecting...'}</span>
            <span className="topbar-divider" />
            <span className="date-label">{TODAY_LABEL}</span>
            <button className="icon-button" aria-label="Refresh dashboard" onClick={() => runAction('Refreshing', refreshDashboard)}><Icon name="refresh" /></button>
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-row">
            <div><div className="eyebrow"><span className="eyebrow-line" />MARKET INTELLIGENCE</div><h1>Good afternoon<span className="title-period">.</span></h1><p className="subtitle">Your market overview, signals, and simulated positions in one place.</p></div>
            <div className="welcome-actions">
              <label className="symbol-select-label" htmlFor="market-symbol">Market</label>
              <select id="market-symbol" className="symbol-select" value={symbol} onChange={(event) => setSymbol(event.target.value)}>{SYMBOLS.map((item) => <option key={item}>{item}</option>)}</select>
              <button className="button button-primary" disabled={Boolean(busy) || health !== 'online'} onClick={connectBroker}><Icon name="plug" size={16} />{busy === 'Connecting broker' ? 'Connecting...' : 'Connect broker'}</button>
            </div>
          </section>

          {error && <div className="error-banner" role="alert"><span>{error}</span><button aria-label="Dismiss error" onClick={() => setError('')}>×</button></div>}

          <section className="market-banner">
            <div className="instrument">
              <div className="instrument-icon">{symbol === 'NIFTY' ? 'N' : 'BN'}</div>
              <div><div className="instrument-title">{symbol} <span className="exchange-tag">NSE</span></div><small>{symbol === 'NIFTY' ? 'NIFTY 50' : 'NIFTY BANK'} · Index futures</small></div>
            </div>
            <div className="market-price"><strong>{formatNumber(currentPrice)}</strong><span className="price-change muted"><i className={`status-dot ${market?.status === 'VALID' ? 'green' : ''}`} />{market?.status || 'NO DATA'}</span></div>
            <div className="banner-divider" />
            <div className="banner-meta"><span>Live stream</span><strong>{stream?.connected ? 'Connected' : 'Disconnected'}</strong></div>
            <div className="banner-meta"><span>Last update</span><strong>{formatTime(market?.updatedAt || marketData?.updatedAt || lastUpdated)}</strong></div>
            <button className="button button-quiet" disabled={Boolean(busy) || health !== 'online'} onClick={() => runAction('Recalculating', () => request(`/analytics/recalculate/${symbol}`, { method: 'POST' }))}><Icon name="refresh" size={15} />Refresh analytics</button>
          </section>

          <section className="metrics-grid" aria-label="Market metrics">
            <MetricCard label="Open interest PCR" value={formatNumber(chain?.pcrOi, 3)} detail={chain ? `ATM ${formatNumber(chain.atmStrike, 0)} · ${chain.expiry || 'Nearest expiry'}` : 'Option chain not available'} tone="purple" icon="pulse" />
            <MetricCard label="Futures VWAP" value={formatNumber(vwap?.value)} detail={vwap?.status || 'Waiting for candle data'} tone="blue" icon="chart" />
            <MetricCard label="Average true range" value={formatNumber(atr?.value)} detail={atr?.status || '14-period volatility'} tone="amber" icon="arrow" />
            <MetricCard label="Paper P&L" value={netPnl == null ? '--' : `${Number(netPnl) >= 0 ? '+' : ''}${formatNumber(netPnl)}`} detail={performance ? `${performance.totalTrades} completed trades` : 'No completed trades'} tone={Number(netPnl) > 0 ? 'green' : Number(netPnl) < 0 ? 'red' : 'default'} icon="layers" />
          </section>

          <section className="content-grid" id="market-panel">
            <article className="panel chart-panel">
              <div className="panel-heading"><div><div className="panel-kicker">PRICE ACTION</div><h2>Futures overview</h2></div><span className={`chip ${market?.status === 'VALID' ? 'chip-live' : 'chip-muted'}`}><i className={`status-dot ${market?.status === 'VALID' ? 'green' : ''}`} />{market?.status || 'NO DATA'}</span></div>
              <div className="chart-main-price">{formatNumber(currentPrice)}<span> {marketData?.futuresPrice ? 'FUTURES' : 'SPOT'}</span></div>
              <PriceChart candles={candles} />
              <div className="chart-footer"><span>{candles.length ? `${candles.length} recent candles` : '5 minute timeframe'}</span><span>Data as of {formatTime(market?.updatedAt || marketData?.updatedAt)}</span></div>
            </article>

            <article className="panel signal-panel" id="strategy-panel">
              <div className="panel-heading"><div><div className="panel-kicker">STRATEGY ENGINE</div><h2>Latest signal</h2></div><span className="signal-orbit"><Icon name="pulse" size={17} /></span></div>
              <div className={`signal-direction ${signalDirection?.toLowerCase() || 'neutral'}`}>{signalDirection || 'NO SIGNAL'}</div>
              <div className="signal-time">{formatTime(latestSignal?.generatedAt || latestSignal?.capturedAt)}</div>
              <div className="signal-levels">
                <div><span>Entry</span><strong>{formatNumber(latestSignal?.entry ?? latestSignal?.inputs?.futuresPrice)}</strong></div>
                <div><span>Stop loss</span><strong>{formatNumber(latestSignal?.stopLoss ?? latestSignal?.riskPlan?.stopLoss)}</strong></div>
                <div><span>Target</span><strong>{formatNumber(latestSignal?.target ?? latestSignal?.riskPlan?.target)}</strong></div>
              </div>
              <div className="signal-reasons">
                <span className="reasons-title">Signal context</span>
                {signalReasons.length ? <ul>{signalReasons.slice(0, 3).map((reason, index) => <li key={`${reason}-${index}`}>{reason}</li>)}</ul> : <p>A signal will appear after market data is available and strategy evaluation runs.</p>}
              </div>
              <button className="button button-outline full-width" disabled={Boolean(busy) || health !== 'online'} onClick={() => runAction('Evaluating strategy', () => request(`/strategy/evaluate/${symbol}`, { method: 'POST' }))}>Evaluate strategy <Icon name="arrow" size={15} /></button>
            </article>
          </section>

          <section className="content-grid lower-grid">
            <article className="panel positions-panel" id="paper-panel">
              <div className="panel-heading"><div><div className="panel-kicker">SIMULATED EXECUTION</div><h2>Open positions <span className="heading-count">{positions.length}</span></h2></div><button className={`button ${strategyStatus?.paperTradingRunning ? 'button-danger-soft' : 'button-outline'}`} disabled={Boolean(busy) || health !== 'online'} onClick={togglePaperTrading}>{strategyStatus?.paperTradingRunning ? 'Stop paper trading' : 'Start paper trading'}</button></div>
              {positions.length ? <div className="table-scroll"><table><thead><tr><th>Instrument</th><th>Side</th><th>Entry</th><th>Mark</th><th>Net P&L</th><th>Status</th></tr></thead><tbody>{positions.map((position) => <tr key={position.id}><td><strong>{position.underlying}</strong><small>Futures · x{position.quantity}</small></td><td><span className={`side-pill ${position.direction?.toLowerCase()}`}>{position.direction}</span></td><td>{formatNumber(position.entryPrice)}</td><td>{formatNumber(position.exitPrice || position.entryPrice)}</td><td className={Number(position.netPnl) >= 0 ? 'positive' : 'negative'}>{formatNumber(position.netPnl)}</td><td><span className="open-tag">OPEN</span></td></tr>)}</tbody></table></div> : <div className="empty-table"><span className="empty-graphic"><Icon name="layers" size={22} /></span><strong>No open positions</strong><p>Start paper trading and accept a valid strategy signal to simulate a trade.</p></div>}
            </article>
            <article className="panel levels-panel">
              <div className="panel-heading"><div><div className="panel-kicker">OPTIONS FLOW</div><h2>Open interest levels</h2></div><span className="chip chip-muted">{symbol}</span></div>
              {oiLevels ? <div className="levels-list"><div className="level-row resistance"><span><i />Resistance</span><strong>{formatNumber(oiLevels.adjustedResistance)}</strong></div><div className="level-track"><span className="track-resistance" /><span className="track-price" /><span className="track-support" /></div><div className="level-row spot"><span><i />Current price</span><strong>{formatNumber(currentPrice)}</strong></div><div className="level-row support"><span><i />Support</span><strong>{formatNumber(oiLevels.adjustedSupport)}</strong></div><div className="oi-summary"><div><small>Call OI</small><strong>{formatCompact(chain?.totalCeOi)}</strong></div><div><small>Put OI</small><strong>{formatCompact(chain?.totalPeOi)}</strong></div></div></div> : <div className="empty-levels"><span className="empty-graphic"><Icon name="chart" size={22} /></span><strong>Levels unavailable</strong><p>Connect the broker feed to calculate support and resistance from live option interest.</p></div>}
            </article>
          </section>

          <section className="panel activity-panel">
            <div className="panel-heading"><div><div className="panel-kicker">RECENT ACTIVITY</div><h2>Signal history</h2></div><span className="panel-caption">Latest strategy evaluations</span></div>
            {signals.length ? <div className="table-scroll"><table><thead><tr><th>Market</th><th>Signal</th><th>Entry</th><th>Risk / reward</th><th>Generated</th><th>Note</th></tr></thead><tbody>{signals.slice(0, 6).map((signal, index) => <tr key={signal.id || `${signal.underlying}-${signal.generatedAt}-${index}`}><td><strong>{signal.underlying}</strong></td><td><span className={`side-pill ${signal.direction?.toLowerCase()}`}>{signal.direction}</span></td><td>{formatNumber(signal.entry)}</td><td>{formatNumber(signal.rewardRisk, 2)}</td><td>{formatTime(signal.generatedAt || signal.capturedAt)}</td><td className="reason-cell">{signal.reasons?.[0] || 'Strategy evaluated'}</td></tr>)}</tbody></table></div> : <div className="empty-activity">No strategy signals yet. Connect market data and evaluate a strategy to begin.</div>}
          </section>

          <footer className="page-footer"><span><i className={`status-dot ${health === 'online' ? 'green' : ''}`} />{health === 'online' ? 'Backend services available' : 'Waiting for backend connection'}</span><span>Paper trading only · No live orders · Updated {lastUpdated ? formatTime(lastUpdated) : 'not yet'}</span></footer>
        </div>
      </main>
      {mobileMenu && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileMenu(false)} />}
    </div>
  )
}

export default App
