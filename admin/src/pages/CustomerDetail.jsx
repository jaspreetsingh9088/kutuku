import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { Badge, ErrorBox, Loading, PageHead, fmtDate, inr, useLoad, useToast } from '../components/ui';

export default function CustomerDetail() {
  const { id } = useParams();
  const toast = useToast();
  const [c, loading, error, reload] = useLoad(() => api.get(`/customers/${id}`), [id]);
  if (loading && !c) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const toggle = async () => {
    const msg = c.is_active ? 'Block this customer? They will be logged out and unable to place orders.' : 'Unblock this customer?';
    if (!window.confirm(msg)) return;
    try { await api.patch(`/customers/${id}/status`, { is_active: !c.is_active }); toast(c.is_active ? 'Customer blocked' : 'Customer unblocked'); reload(); } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title={c.name || 'Unnamed customer'} sub={`Customer since ${fmtDate(c.created_at, false)}`}>
        <Link className="btn" to="/customers">Back</Link>
        <button className={`btn ${c.is_active ? 'danger' : ''}`} onClick={toggle}>{c.is_active ? 'Block customer' : 'Unblock customer'}</button>
      </PageHead>
      <div className="grid g-main">
        <div className="card" style={{ padding: 0 }}>
          <h2 style={{ padding: '16px 18px 0' }}>Order history</h2>
          <div style={{ overflowX: 'auto' }}>
            <table>
              <thead><tr><th>Order</th><th>Date</th><th>Status</th><th>Payment</th><th className="num">Total</th></tr></thead>
              <tbody>
                {c.orders.map((o) => (
                  <tr key={o.id}><td><Link to={`/orders/${o.id}`}>{o.order_number}</Link></td><td>{fmtDate(o.created_at)}</td>
                    <td><Badge value={o.status} /></td><td><Badge value={o.payment_status} /></td><td className="num">{inr(o.total)}</td></tr>
                ))}
                {c.orders.length === 0 && <tr><td colSpan={5} className="muted" style={{ textAlign: 'center' }}>No orders yet</td></tr>}
              </tbody>
            </table>
          </div>
        </div>
        <div>
          <div className="card">
            <h2>Profile</h2>
            <dl className="kv">
              <dt>Mobile</dt><dd>{c.mobile}</dd>
              <dt>Email</dt><dd>{c.email || '—'}</dd>
              <dt>GSTIN</dt><dd>{c.gstin || '—'}</dd>
              <dt>Status</dt><dd><Badge value={c.is_active ? 'active' : 'Blocked'} tone={c.is_active ? 'green' : 'red'} /></dd>
              <dt>Paid orders</dt><dd>{c.stats.orders}</dd>
              <dt>Total spent</dt><dd className="total">{inr(c.stats.spent)}</dd>
            </dl>
          </div>
          <div className="card">
            <h2>Saved addresses</h2>
            {c.addresses.length === 0 && <p className="muted">None</p>}
            {c.addresses.map((a) => (
              <p key={a.id} style={{ lineHeight: 1.5, marginTop: 0 }}>
                <strong>{a.name}</strong> {a.is_default ? <Badge value="Default" tone="blue" /> : null}<br />
                {a.line1}{a.line2 && `, ${a.line2}`}, {a.city}, {a.state} – {a.pincode}<br /><span className="muted">{a.phone}</span>
              </p>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
