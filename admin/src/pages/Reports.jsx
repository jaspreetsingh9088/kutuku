import { useSearchParams } from 'react-router-dom';
import { api, qs } from '../api';
import { Empty, ErrorBox, Loading, PageHead, inr, label, useLoad, useToast } from '../components/ui';

const TABS = [
  ['sales', 'Sales'], ['products', 'Product-wise'], ['orders', 'Orders'], ['stock', 'Stock & inventory'], ['low', 'Low stock'],
];
const MONEY = ['gross_sales', 'discounts', 'delivery', 'refunds', 'net_sales', 'avg_order_value', 'revenue', 'value', 'mrp', 'price', 'stock_value'];
const today = () => new Date().toISOString().slice(0, 10);
const daysAgo = (n) => new Date(Date.now() - n * 864e5).toISOString().slice(0, 10);

export default function Reports() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') || 'sales';
  const f = { period: sp.get('period') || 'daily', from: sp.get('from') || daysAgo(29), to: sp.get('to') || today() };
  const set = (o) => { const n = new URLSearchParams(sp); Object.entries(o).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k))); setSp(n); };

  const type = tab === 'low' ? 'stock' : tab;
  const params = tab === 'low' ? { type: 'low' } : tab === 'stock' ? {} : tab === 'sales' ? f : { from: f.from, to: f.to };
  const [data, loading, error, reload] = useLoad(() => api.get(`/reports/${type}${qs(params)}`), [type, JSON.stringify(params)]);
  const exp = (format) => api.download(`/reports/${type}${qs({ ...params, export: 1, format })}`, `${tab}-report.${format}`).catch((e) => toast(e.message, 'err'));
  const cols = data && data.rows.length ? Object.keys(data.rows[0]) : [];
  const fmt = (k, v) => (MONEY.includes(k) ? inr(v) : v === null || v === '' ? '—' : String(v));

  return (
    <>
      <PageHead title="Reports & analytics" sub="Sales, products, orders and inventory">
        <button className="btn" onClick={() => exp('xlsx')}>Export Excel</button>
        <button className="btn" onClick={() => exp('csv')}>Export CSV</button>
      </PageHead>
      <div className="tabs">{TABS.map(([k, t]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => set({ tab: k })}>{t}</button>)}</div>
      {!['stock', 'low'].includes(tab) && (
        <div className="filters">
          {tab === 'sales' && (
            <select value={f.period} onChange={(e) => set({ period: e.target.value })}><option value="daily">Daily</option><option value="weekly">Weekly</option><option value="monthly">Monthly</option></select>
          )}
          <input type="date" value={f.from} onChange={(e) => set({ from: e.target.value })} />
          <input type="date" value={f.to} onChange={(e) => set({ to: e.target.value })} />
          {[['7 days', 6], ['30 days', 29], ['90 days', 89], ['1 year', 364]].map(([t, n]) => <button key={t} className="btn sm" onClick={() => set({ from: daysAgo(n), to: today() })}>{t}</button>)}
        </div>
      )}
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !data ? <Loading /> : data && (
        <>
          {Object.keys(data.totals).length > 0 && (
            <div className="grid g4" style={{ marginBottom: 16 }}>
              {Object.entries(data.totals).slice(0, 4).map(([k, v]) => <div key={k} className="card stat"><div className="k">{label(k)}</div><div className="v">{fmt(k, v)}</div></div>)}
            </div>
          )}
          <div className="table-wrap">
            {data.rows.length === 0 ? <Empty>No data for this period.</Empty> : (
              <table>
                <thead><tr>{cols.map((c) => <th key={c} className={typeof data.rows[0][c] === 'number' ? 'num' : ''}>{label(c)}</th>)}</tr></thead>
                <tbody>
                  {data.rows.map((r, i) => <tr key={i}>{cols.map((c) => <td key={c} className={typeof r[c] === 'number' ? 'num' : ''}>{c.endsWith('status') && !c.startsWith('stock') ? label(r[c]) : fmt(c, r[c])}</td>)}</tr>)}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </>
  );
}
