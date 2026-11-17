import { Link } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api';
import { Badge, ErrorBox, Loading, PageHead, fmtDate, inr, label, useLoad } from '../components/ui';

function Stat({ k, v, s, warn, to }) {
  const body = <div className={`card stat ${warn ? 'warn' : ''}`}><div className="k">{k}</div><div className="v">{v}</div>{s && <div className="s">{s}</div>}</div>;
  return to ? <Link to={to} style={{ color: 'inherit', textDecoration: 'none' }}>{body}</Link> : body;
}

export default function Dashboard() {
  const [d, loading, error, reload] = useLoad(() => api.get('/dashboard'));
  if (loading && !d) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const pending = ['placed', 'confirmed', 'packed'].reduce((a, s) => a + (d.status_counts[s] || 0), 0);
  return (
    <>
      <PageHead title="Dashboard" sub="Your store at a glance"><button className="btn" onClick={reload}>Refresh</button></PageHead>
      <div className="grid g4">
        <Stat k="Total sales" v={inr(d.totals.sales)} s={`${d.totals.orders} paid orders`} />
        <Stat k="Today" v={inr(d.today.sales)} s={`${d.today.orders} orders`} />
        <Stat k="This month" v={inr(d.month.sales)} s={`${d.month.orders} orders`} />
        <Stat k="Customers" v={d.customers.total} s={`+${d.customers.new_7d} in last 7 days`} to="/customers" />
        <Stat k="Products" v={d.catalog.products} s={`${d.catalog.active_products} active`} to="/products" />
        <Stat k="Categories" v={d.catalog.categories} to="/categories" />
        <Stat k="To be shipped" v={pending} s="placed, confirmed or packed" to="/orders" />
        <Stat k="Low-stock variants" v={d.low_stock.count} warn={d.low_stock.count > 0} to="/products?stock=low" />
      </div>

      <div className="grid g-main" style={{ marginTop: 16 }}>
        <div className="card">
          <h2>Sales — last 30 days</h2>
          <div style={{ height: 260 }}>
            <ResponsiveContainer>
              <AreaChart data={d.sales_30d} margin={{ left: 0, right: 8, top: 8 }}>
                <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#0f766e" stopOpacity={0.25} /><stop offset="100%" stopColor="#0f766e" stopOpacity={0} /></linearGradient></defs>
                <CartesianGrid stroke="#eef0f3" vertical={false} />
                <XAxis dataKey="day" tickFormatter={(v) => v.slice(8)} tick={{ fontSize: 11, fill: '#5b6170' }} tickLine={false} axisLine={false} interval={2} />
                <YAxis tick={{ fontSize: 11, fill: '#5b6170' }} tickLine={false} axisLine={false} width={60} tickFormatter={(v) => `₹${v >= 1000 ? `${Math.round(v / 1000)}k` : v}`} />
                <Tooltip formatter={(v, n) => (n === 'sales' ? inr(v) : v)} labelFormatter={(l) => fmtDate(l, false)} />
                <Area type="monotone" dataKey="sales" stroke="#0f766e" strokeWidth={2} fill="url(#g)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
        <div className="card">
          <div className="inline" style={{ justifyContent: 'space-between' }}><h2>Low-stock alerts</h2><Link to="/reports?tab=stock">View all</Link></div>
          {d.low_stock.items.length === 0 ? <p className="muted">All products are well stocked.</p> : (
            <ul className="list-plain">
              {d.low_stock.items.map((v) => (
                <li key={v.id}>
                  <span><Link to={`/products/${v.product_id}`}>{v.name}</Link> <span className="muted">· {v.label}</span></span>
                  <Badge value={v.stock === 0 ? 'Out of stock' : `${v.stock} left`} tone={v.stock === 0 ? 'red' : 'amber'} />
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: 16, padding: 0 }}>
        <div className="inline" style={{ justifyContent: 'space-between', padding: '16px 18px 4px' }}><h2>Recent orders</h2><Link to="/orders">All orders</Link></div>
        <div style={{ overflowX: 'auto' }}>
          <table>
            <thead><tr><th>Order</th><th>Customer</th><th>Date</th><th>Status</th><th>Payment</th><th className="num">Total</th></tr></thead>
            <tbody>
              {d.recent_orders.map((o) => (
                <tr key={o.id}>
                  <td><Link to={`/orders/${o.id}`}>{o.order_number}</Link></td>
                  <td>{o.customer_name || '—'} <span className="muted">{o.mobile}</span></td>
                  <td>{fmtDate(o.created_at)}</td>
                  <td><Badge value={o.status} /></td>
                  <td><Badge value={o.payment_status} /></td>
                  <td className="num">{inr(o.total)}</td>
                </tr>
              ))}
              {d.recent_orders.length === 0 && <tr><td colSpan={6} className="muted" style={{ textAlign: 'center' }}>No orders yet</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
      <p className="muted" style={{ marginTop: 10, fontSize: 12 }}>Order status counts: {Object.entries(d.status_counts).map(([k, v]) => `${label(k)} ${v}`).join(' · ') || '—'}</p>
    </>
  );
}
