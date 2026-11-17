import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, qs } from '../api';
import { Badge, Empty, ErrorBox, Loading, PageHead, Pager, Toggle, fmtDate, useDebounced, useLoad, useToast } from '../components/ui';

const Stars = ({ n }) => <span style={{ color: '#f5a524', letterSpacing: 1 }}>{'★'.repeat(n)}<span style={{ color: '#d6d9df' }}>{'★'.repeat(5 - n)}</span></span>;

export default function Reviews() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const [q, setQ] = useState(sp.get('q') || '');
  const dq = useDebounced(q);
  const f = { q: dq, status: sp.get('status') || '', rating: sp.get('rating') || '', page: sp.get('page') || 1 };
  const set = (k, v) => { const n = new URLSearchParams(sp); v ? n.set(k, v) : n.delete(k); if (k !== 'page') n.delete('page'); setSp(n); };
  const [data, loading, error, reload, setData] = useLoad(() => api.get(`/reviews${qs(f)}`), [JSON.stringify(f)]);

  const toggle = async (r) => {
    try {
      await api.patch(`/reviews/${r.id}`, { is_approved: !r.is_approved });
      setData((d) => ({ ...d, data: d.data.map((x) => (x.id === r.id ? { ...x, is_approved: x.is_approved ? 0 : 1 } : x)) }));
    } catch (e) { toast(e.message, 'err'); }
  };
  const remove = async (r) => {
    if (!window.confirm('Delete this review permanently?')) return;
    try { await api.del(`/reviews/${r.id}`); toast('Review deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title="Reviews" sub="Ratings customers leave after delivery. Hidden reviews don't count towards a product's rating." />
      <div className="filters">
        <input type="search" placeholder="Search product, customer or text…" value={q} onChange={(e) => { setQ(e.target.value); set('page', ''); }} />
        <select value={f.status} onChange={(e) => set('status', e.target.value)}><option value="">All</option><option value="approved">Visible</option><option value="hidden">Hidden</option></select>
        <select value={f.rating} onChange={(e) => set('rating', e.target.value)}><option value="">Any rating</option>{[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} stars</option>)}</select>
      </div>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !data ? <Loading /> : data && (
        <>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Product</th><th>Customer</th><th>Rating</th><th>Review</th><th>Date</th><th>Visible</th><th /></tr></thead>
              <tbody>
                {data.data.map((r) => (
                  <tr key={r.id}>
                    <td><Link to={`/products/${r.product_id}`}>{r.product_name}</Link></td>
                    <td><Link to={`/customers/${r.customer_id}`}>{r.customer_name || r.mobile || r.email}</Link></td>
                    <td><Stars n={r.rating} /></td>
                    <td className="wrap" style={{ maxWidth: 380 }}>{r.comment || <span className="muted">—</span>}</td>
                    <td>{fmtDate(r.created_at, false)}</td>
                    <td><Toggle checked={!!r.is_approved} onChange={() => toggle(r)} /></td>
                    <td><button className="btn sm danger" onClick={() => remove(r)}>Delete</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.data.length === 0 && <Empty>No reviews yet.</Empty>}
          </div>
          <Pager meta={data.meta} onPage={(p) => set('page', p)} />
          <p className="muted" style={{ fontSize: 12 }}>{data.data.filter((r) => !r.is_approved).length > 0 && <Badge value="Some reviews on this page are hidden" tone="amber" />}</p>
        </>
      )}
    </>
  );
}
