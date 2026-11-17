import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, qs } from '../api';
import { Badge, Empty, ErrorBox, Loading, PageHead, Pager, fmtDate, inr, label, useDebounced, useLoad, useToast } from '../components/ui';

const STATUSES = ['placed', 'confirmed', 'packed', 'shipped', 'out_for_delivery', 'delivered', 'cancelled', 'returned'];
const PAY = ['paid', 'pending', 'failed', 'refunded', 'partially_refunded'];

export default function Orders() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const [q, setQ] = useState(sp.get('q') || '');
  const dq = useDebounced(q);
  const f = { q: dq, status: sp.get('status') || '', payment_status: sp.get('payment_status') || '', from: sp.get('from') || '', to: sp.get('to') || '', page: sp.get('page') || 1 };
  const set = (k, v) => { const n = new URLSearchParams(sp); v ? n.set(k, v) : n.delete(k); if (k !== 'page') n.delete('page'); setSp(n); };
  const [data, loading, error, reload] = useLoad(() => api.get(`/orders${qs(f)}`), [JSON.stringify(f)]);
  const { page, ...exportFilters } = f;

  return (
    <>
      <PageHead title="Orders" sub="Track, update and fulfil customer orders">
        <button className="btn" onClick={() => api.download(`/orders-export${qs(exportFilters)}`, 'orders.xlsx').catch((e) => toast(e.message, 'err'))}>Download Excel</button>
        <button className="btn" onClick={() => api.download(`/orders-export${qs({ ...exportFilters, format: 'csv' })}`, 'orders.csv').catch((e) => toast(e.message, 'err'))}>CSV</button>
      </PageHead>
      <div className="tabs">
        {[['', 'All'], ['placed', 'New'], ['confirmed', 'Confirmed'], ['packed', 'Packed'], ['shipped', 'Shipped'], ['delivered', 'Delivered'], ['cancelled', 'Cancelled']].map(([v, t]) => (
          <button key={t} className={f.status === v ? 'on' : ''} onClick={() => set('status', v)}>{t}</button>
        ))}
      </div>
      <div className="filters">
        <input type="search" placeholder="Order no., customer name or mobile…" value={q} onChange={(e) => { setQ(e.target.value); set('page', ''); }} />
        <select value={f.status} onChange={(e) => set('status', e.target.value)}><option value="">Any status</option>{STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select>
        <select value={f.payment_status} onChange={(e) => set('payment_status', e.target.value)}><option value="">Any payment</option>{PAY.map((s) => <option key={s} value={s}>{label(s)}</option>)}</select>
        <input type="date" value={f.from} onChange={(e) => set('from', e.target.value)} title="From" />
        <input type="date" value={f.to} onChange={(e) => set('to', e.target.value)} title="To" />
      </div>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !data ? <Loading /> : data && (
        <>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Order</th><th>Date</th><th>Customer</th><th className="num">Items</th><th>Status</th><th>Payment</th><th className="num">Total</th></tr></thead>
              <tbody>
                {data.data.map((o) => (
                  <tr key={o.id}>
                    <td><Link to={`/orders/${o.id}`}><strong>{o.order_number}</strong></Link></td>
                    <td>{fmtDate(o.created_at)}</td>
                    <td><Link to={`/customers/${o.customer_id}`}>{o.customer_name || 'Customer'}</Link> <span className="muted">{o.mobile}</span></td>
                    <td className="num">{o.items}</td>
                    <td><Badge value={o.status} /></td>
                    <td><Badge value={o.payment_status} /></td>
                    <td className="num">{inr(o.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.data.length === 0 && <Empty>No orders found.</Empty>}
          </div>
          <Pager meta={data.meta} onPage={(p) => set('page', p)} />
        </>
      )}
    </>
  );
}
