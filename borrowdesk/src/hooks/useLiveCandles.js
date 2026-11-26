import { Client } from '@stomp/stompjs';
import { useEffect, useState } from 'react';
import { marketSocketUrl, tradingDeskApi, unwrapRows } from '../api/tradingDeskApi';

const empty = { key: '', candles: [], loading: false, status: '' };
const timestamp = candle => Date.parse(candle.tradedDateTimestamp ?? candle.traded_date_timestamp ?? candle.time);
const candleFields = ['open', 'high', 'low', 'close', 'volume'];

function mergeCandles(history, updates) {
  const byTime = new Map(history.map(candle => [timestamp(candle), candle]));
  let changed = false;
  for (const candle of updates) {
    const time = timestamp(candle);
    if (!Number.isFinite(time)) continue;
    const previous = byTime.get(time);
    if (previous && candleFields.every(field => Number(previous[field]) === Number(candle[field])
      && (previous[field] == null) === (candle[field] == null))) continue;
    byTime.set(time, candle);
    changed = true;
  }
  return changed ? [...byTime.values()].sort((left, right) => timestamp(left) - timestamp(right)) : history;
}

export function useLiveCandles(symbol, exchange) {
  const key = symbol && exchange ? JSON.stringify([symbol, exchange]) : '';
  const [state, setState] = useState(empty);

  useEffect(() => {
    if (!key) return;
    const abort = new AbortController();
    let connectedPreviously = false;
    let pendingUpdates = null;
    let chartFrame = 0;
    const frameUpdates = new Map();
    const update = reducer => {
      if (!abort.signal.aborted) {
        setState(current => current.key === key ? reducer(current) : current);
      }
    };
    setState({ ...empty, key, loading: true, status: 'Connecting to live feed…' });

    async function loadHistory() {
      const duringRequest = new Map();
      pendingUpdates = duringRequest;
      try {
        const history = unwrapRows(await tradingDeskApi.candles(symbol, exchange, abort.signal));
        if (pendingUpdates === duringRequest) {
          update(current => ({
            ...current,
            candles: mergeCandles(mergeCandles(current.candles, history), duringRequest.values()),
            loading: false,
            historyError: false,
          }));
        }
      } catch {
        if (pendingUpdates === duringRequest) {
          update(current => ({ ...current, loading: false, historyError: true }));
        }
      } finally {
        if (pendingUpdates === duringRequest) pendingUpdates = null;
      }
    }

    const topic = String(symbol).trim();
    const normalize = value => String(value ?? '').trim().toUpperCase();
    const client = new Client({
      brokerURL: marketSocketUrl(),
      reconnectDelay: 3000,
      connectionTimeout: 5000,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      onConnect: () => {
        if (abort.signal.aborted) return;
        client.subscribe('/topic/price/' + topic, ({ body }) => {
          let message;
          try { message = JSON.parse(body); } catch { return; }
          if (normalize(message?.exchange) !== normalize(exchange)) return;
          const candle = { ...message, tradedDateTimestamp: message.tradeDateTimestamp };
          const time = timestamp(candle);
          if (!Number.isFinite(time) || candleFields.slice(0, 4).some(field =>
            candle[field] == null || !Number.isFinite(Number(candle[field])))) return;
          pendingUpdates?.set(time, candle);
          frameUpdates.set(time, candle);
          if (!chartFrame) {
            chartFrame = window.requestAnimationFrame(() => {
              chartFrame = 0;
              const updates = [...frameUpdates.values()];
              frameUpdates.clear();
              update(current => {
                const candles = mergeCandles(current.candles, updates);
                return candles === current.candles ? current : { ...current, candles };
              });
            });
          }
        });
        update(current => ({ ...current, status: 'Live' }));
        if (connectedPreviously) void loadHistory();
        connectedPreviously = true;
      },
      onWebSocketClose: () => update(current => ({ ...current, status: 'Reconnecting live feed…' })),
      onStompError: () => client.forceDisconnect(),
    });

    void loadHistory();
    client.activate();
    return () => {
      abort.abort();
      window.cancelAnimationFrame(chartFrame);
      void client.deactivate({ force: true });
    };
  }, [key, symbol, exchange]);

  return state.key === key ? state : { ...empty, key, loading: Boolean(key) };
}
