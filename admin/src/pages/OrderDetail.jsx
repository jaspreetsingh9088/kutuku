import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { Badge, ErrorBox, Field, Loading, Modal, PageHead, fmtDate, inr, label, useLoad, useToast } from '../components/ui';

function CourierCard({ o, onSaved }) {
  const toast = useToast();
  const [f, setF] = useState({ courier_name: o.courier_name || '', courier_phone: o.courier_phone || '', tracking_url: o.tracking_url || '' });
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try { await api.patch(`/orders/${o.id}/courier`, f); toast('Courier details saved — shown on the app\'s Order Tracking screen'); onSaved(); } catch (e) { toast(e.message, 'err'); } finally { setBusy(false); }
  };
  return (
    <div className="card stack">
      <h2 style={{ margin: 0 }}>Courier</h2>
      <Field label="Delivery partner / courier name"><input value={f.courier_name} onChange={(e) => setF({ ...f, courier_name: e.target.value })} placeholder="e.g. Delhivery – Alexander Jr" /></Field>
      <Field label="Courier phone"><input value={f.courier_phone} onChange={(e) => setF({ ...f, courier_phone: e.target.value })} /></Field>
      <Field label="Tracking link"><input value={f.tracking_url} onChange={(e) => setF({ ...f, tracking_url: e.target.value })} placeholder="https://" /></Field>
      <div><button className="btn sm primary" disabled={busy} onClick={save}>Save courier</button></div>
    </div>
  );
}

export default function OrderDetail() {
  const { id } = useParams();
  const toast = useToast();
  const [o, loading, error, reload] = useLoad(() => api.get(`/orders/${id}`), [id]);
  const [modal, setModal] = useState(null); // 'cancel' | 'refund'
  const [form, setForm] = useState({});
  const [busy, setBusy] = useState(false);

  if (loading && !o) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;

  const run = async (fn, msg) => {
    setBusy(true);
    try { await fn(); toast(msg); setModal(null); reload(); } catch (e) { toast(e.message, 'err'); } finally { setBusy(false); }
  };
  const setStatus = (status) => run(() => api.patch(`/orders/${id}/status`, { status }), `Order marked ${label(status).toLowerCase()} — customer notified`);
  const a = o.shipping_address;
  const refundable = Math.round((o.total - o.refund_amount) * 100) / 100;
  const closed = ['cancelled', 'delivered', 'returned', 'pending_payment'].includes(o.status);

  return (
    <>
      <PageHead title={`Order ${o.order_number}`} sub={`Placed ${fmtDate(o.created_at)}`}>
        <Link className="btn" to="/orders">Back</Link>
        {o.payment_status !== 'pending' && o.payment_status !== 'failed' && <button className="btn" onClick={() => api.openPdf(`/orders/${id}/invoice`).catch((e) => toast(e.message, 'err'))}>GST invoice</button>}
      </PageHead>

      <div className="grid g-main">
        <div>
          <div className="card">
            <div className="inline" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
              <div className="inline"><Badge value={o.status} /><Badge value={o.payment_status} /></div>
              {!closed && (
                <div className="actions">
                  {o.next_statuses.slice(0, 1).map((s) => <button key={s} className="btn primary" disabled={busy} onClick={() => setStatus(s)}>Mark {label(s).toLowerCase()}</button>)}
                  {o.next_statuses.length > 1 && (
                    <select value="" onChange={(e) => e.target.value && setStatus(e.target.value)} style={{ width: 'auto' }} disabled={busy}>
                      <option value="">Set status…</option>{o.next_statuses.map((s) => <option key={s} value={s}>{label(s)}</option>)}
                    </select>
                  )}
                  <button className="btn danger" onClick={() => { setForm({ reason: '', refund: true }); setModal('cancel'); }}>Cancel order</button>
                </div>
              )}
              {o.status === 'delivered' && <button className="btn" onClick={() => window.confirm('Mark as returned and restock items?') && setStatus('returned')}>Mark returned</button>}
            </div>
            {o.cancel_reason && <p className="muted" style={{ marginBottom: 0 }}>Reason: {o.cancel_reason}</p>}
          </div>

          <div className="card" style={{ padding: 0 }}>
            <h2 style={{ padding: '16px 18px 0' }}>Items</h2>
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead><tr><th>Product</th><th>SKU</th><th className="num">Price</th><th className="num">Qty</th><th className="num">Total</th></tr></thead>
                <tbody>
                  {o.items.map((it) => (
                    <tr key={it.id}>
                      <td><div className="prod-cell">{it.image_url ? <img className="thumb" src={it.image_url} alt="" /> : <div className="thumb" />}
                        <div>{it.product_id ? <Link to={`/products/${it.product_id}`}>{it.product_name}</Link> : it.product_name}<div className="muted">{it.variant_label} · GST {it.gst_rate}%</div></div></div></td>
                      <td>{it.sku}</td>
                      <td className="num">{inr(it.unit_price)}{it.mrp > it.unit_price && <div className="muted" style={{ textDecoration: 'line-through', fontSize: 12 }}>{inr(it.mrp)}</div>}</td>
                      <td className="num">{it.quantity}</td>
                      <td className="num">{inr(it.line_total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ padding: 18, display: 'flex', justifyContent: 'flex-end' }}>
              <dl className="kv" style={{ minWidth: 280, margin: 0 }}>
                <dt>Subtotal</dt><dd>{inr(o.subtotal)}</dd>
                {o.discount > 0 && <><dt>Discount {o.coupon_code && `(${o.coupon_code})`}</dt><dd>− {inr(o.discount)}</dd></>}
                <dt>Delivery</dt><dd>{o.delivery_charge > 0 ? inr(o.delivery_charge) : 'Free'}</dd>
                <dt><strong>Total</strong></dt><dd className="total">{inr(o.total)}</dd>
                {o.refund_amount > 0 && <><dt>Refunded</dt><dd>− {inr(o.refund_amount)}</dd></>}
              </dl>
            </div>
          </div>

          <div className="card">
            <div className="inline" style={{ justifyContent: 'space-between' }}>
              <h2 style={{ margin: 0 }}>Payment</h2>
              <div className="actions">
                {['paid', 'partially_refunded'].includes(o.payment_status) && refundable > 0 && (
                  <button className="btn sm" onClick={() => { setForm({ amount: refundable }); setModal('refund'); }}>Issue refund</button>
                )}
                {['pending', 'failed'].includes(o.payment_status) && o.status !== 'pending_payment' && (
                  <button className="btn sm" onClick={() => window.confirm('Mark this order as paid manually?') && run(() => api.patch(`/orders/${id}/payment-status`, { payment_status: 'paid' }), 'Marked as paid')}>Mark paid</button>
                )}
              </div>
            </div>
            <dl className="kv" style={{ marginTop: 12 }}>
              <dt>Method</dt><dd>{o.payment_method.toUpperCase()}</dd>
              <dt>Razorpay order</dt><dd>{o.razorpay_order_id || '—'}</dd>
              <dt>Razorpay payment</dt><dd>{o.razorpay_payment_id || '—'}</dd>
              <dt>Invoice no.</dt><dd>{o.invoice_number || '—'}</dd>
            </dl>
          </div>
        </div>

        <div>
          <div className="card">
            <h2>Customer</h2>
            <p style={{ margin: 0 }}><Link to={`/customers/${o.customer_id}`}>{o.customer_name || 'Customer'}</Link></p>
            <p className="muted" style={{ margin: '4px 0 0' }}>{o.mobile}{o.email && ` · ${o.email}`}</p>
          </div>
          <CourierCard o={o} onSaved={reload} />
          <div className="card">
            <h2>Delivery address</h2>
            <p style={{ margin: 0, lineHeight: 1.6 }}>
              <strong>{a.name}</strong><br />{a.line1}{a.line2 && <>, {a.line2}</>}<br />{a.landmark && <>Near {a.landmark}<br /></>}
              {a.city}, {a.state} – {a.pincode}<br />Phone: {a.phone}
            </p>
          </div>
          <div className="card">
            <h2>Timeline</h2>
            <ul className="timeline">
              {o.timeline.map((t, i) => <li key={i}><strong>{label(t.status)}</strong><div className="muted" style={{ fontSize: 12 }}>{fmtDate(t.created_at)}{t.note && ` · ${t.note}`}</div></li>)}
            </ul>
          </div>
        </div>
      </div>

      {modal === 'cancel' && (
        <Modal title="Cancel order" onClose={() => setModal(null)}
          footer={<><button className="btn" onClick={() => setModal(null)}>Keep order</button>
            <button className="btn danger" disabled={busy || form.reason.trim().length < 2} onClick={() => run(() => api.post(`/orders/${id}/cancel`, form), 'Order cancelled')}>Cancel order</button></>}>
          <div className="stack">
            <Field label="Reason *"><input autoFocus value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} placeholder="e.g. Out of stock, customer request" /></Field>
            {o.payment_status === 'paid' && (
              <label className="inline"><input type="checkbox" style={{ width: 'auto' }} checked={form.refund} onChange={(e) => setForm({ ...form, refund: e.target.checked })} /> Refund {inr(refundable)} to the customer via Razorpay</label>
            )}
            <p className="hint">Stock for all items is returned to inventory and the customer gets a notification.</p>
          </div>
        </Modal>
      )}
      {modal === 'refund' && (
        <Modal title="Issue refund" onClose={() => setModal(null)}
          footer={<><button className="btn" onClick={() => setModal(null)}>Close</button>
            <button className="btn primary" disabled={busy || !(form.amount > 0)} onClick={() => run(() => api.post(`/orders/${id}/refund`, { amount: Number(form.amount) }), 'Refund issued')}>Refund {inr(form.amount)}</button></>}>
          <Field label="Amount (₹)" hint={`Up to ${inr(refundable)} can be refunded. Refunds go back to the original payment method via Razorpay.`}>
            <input type="number" step="0.01" min="1" max={refundable} value={form.amount} onChange={(e) => setForm({ amount: e.target.value })} />
          </Field>
        </Modal>
      )}
    </>
  );
}
