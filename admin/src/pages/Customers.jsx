import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, qs } from '../api';
import { Badge, Empty, ErrorBox, Loading, PageHead, Pager, fmtDate, inr, useDebounced, useLoad } from '../components/ui';

export default function Customers() {
  const [sp, setSp] = useSearchParams();
  const [q, setQ] = useState(sp.get('q') || '');
  const dq = useDebounced(q);
  const f = { q: dq, status: sp.get('status') || '', page: sp.get('page') || 1 };
  const set = (k, v) => { const n = new URLSearchParams(sp); v ? n.set(k, v) : n.delete(k); if (k !== 'page') n.delete('page'); setSp(n); };
  const [data, loading, error, reload] = useLoad(() => api.get(`/customers${qs(f)}`), [JSON.stringify(f)]);

  return (
    <>
      <PageHead title="Customers" sub="Registered app users" />
      <div className="filters">
        <input type="search" placeholder="Search by name, mobile or email…" value={q} onChange={(e) => { setQ(e.target.value); set('page', ''); }} />
        <select value={f.status} onChange={(e) => set('status', e.target.value)}><option value="">All</option><option value="active">Active</option><option value="blocked">Blocked</option></select>
      </div>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !data ? <Loading /> : data && (
        <>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Mobile</th><th>Email</th><th>Joined</th><th className="num">Orders</th><th className="num">Spent</th><th>Status</th></tr></thead>
              <tbody>
                {data.data.map((c) => (
                  <tr key={c.id}>
                    <td><Link to={`/customers/${c.id}`}>{c.name || 'Unnamed'}</Link></td>
                    <td>{c.mobile}</td>
                    <td>{c.email || <span className="muted">—</span>}</td>
                    <td>{fmtDate(c.created_at, false)}</td>
                    <td className="num">{c.orders}</td>
                    <td className="num">{inr(c.spent)}</td>
                    <td><Badge value={c.is_active ? 'active' : 'Blocked'} tone={c.is_active ? 'green' : 'red'} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.data.length === 0 && <Empty>No customers found.</Empty>}
          </div>
          <Pager meta={data.meta} onPage={(p) => set('page', p)} />
        </>
      )}
    </>
  );
}
