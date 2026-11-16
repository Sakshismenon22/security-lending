export function tradingAliases(value) {
  if (Array.isArray(value)) return value.map(tradingAliases);
  if (!value || typeof value !== 'object') return value;
  const result = { ...value };
  for (const [key, item] of Object.entries(value)) {
    result[key] = tradingAliases(item);
    result[key.replace(/[A-Z]/g, c => '_' + c.toLowerCase())] = result[key];
  }
  return result;
}
const day = date => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(date));
export function candleQuote(candles) {
  const sorted = candles.filter(c => c.tradedDateTimestamp && Date.parse(c.tradedDateTimestamp) < Math.floor(Date.now()/60000)*60000 && c.close != null).sort((a,b) => Date.parse(a.tradedDateTimestamp)-Date.parse(b.tradedDateTimestamp));
  const last = sorted.at(-1);
  if (!last) return {};
  const currentDay = day(last.tradedDateTimestamp);
  const today = sorted.filter(c => day(c.tradedDateTimestamp) === currentDay);
  const prior = sorted.filter(c => day(c.tradedDateTimestamp) < currentDay).at(-1);
  const previousClose = prior ? Number(prior.close) : null;
  const change = previousClose == null ? null : Number(last.close)-previousClose;
  return {
    price: Number(last.close), close: Number(last.close), priceAt: last.tradedDateTimestamp,
    previousClose, change, changePercent: previousClose ? change/previousClose*100 : null,
    open: Number(today[0].open), high: Math.max(...today.map(c=>Number(c.high))), low: Math.min(...today.map(c=>Number(c.low))),
    volume: today.every(c=>c.volume != null) ? today.reduce((n,c)=>n+Number(c.volume),0) : null,
  };
}
export function positionValues(position, quote = {}) {
  const row = tradingAliases(position);
  const long = Number(row.long_quantity ?? 0), short = Number(row.short_quantity ?? 0);
  const price = quote.price ?? row.price;
  const previous = quote.previousClose;
  const quantity = long-short;
  const dayPnl = price != null && previous != null ? quantity*(Number(price)-Number(previous)) : null;
  const base = previous != null ? (long+short)*Number(previous) : 0;
  return { ...row, price, dayPnl, dayPnlPercent: dayPnl != null && base ? dayPnl/base*100 : null };
}
