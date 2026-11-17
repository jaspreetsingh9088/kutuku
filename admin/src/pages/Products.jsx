import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, qs } from '../api';
import { Badge, Empty, ErrorBox, Loading, PageHead, Pager, Toggle, inr, useDebounced, useLoad, useToast } from '../components/ui';

export default function Products() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const [q, setQ] = useState(sp.get('q') || '');
  const dq = useDebounced(q);
  const f = { q: dq, category_id: sp.get('category_id') || '', status: sp.get('status') || '', stock: sp.get('stock') || '', page: sp.get('page') || 1 };
  const set = (k, v) => { const n = new URLSearchParams(sp); v ? n.set(k, v) : n.delete(k); if (k !== 'page') n.delete('page'); setSp(n); };

  const [cats] = useLoad(() => api.get('/categories'));
  const [data, loading, error, reload, setData] = useLoad(() => api.get(`/products${qs(f)}`), [JSON.stringify(f)]);

  const toggle = async (p) => {
    try {
      await api.patch(`/products/${p.id}/status`, { is_active: !p.is_active });
      setData((d) => ({ ...d, data: d.data.map((x) => (x.id === p.id ? { ...x, is_active: x.is_active ? 0 : 1 } : x)) }));
    } catch (e) { toast(e.message, 'err'); }
  };
  const remove = async (p) => {
    if (!window.confirm(`Delete "${p.name}"? Products with past orders are deactivated instead.`)) return;
    try { const r = await api.del(`/products/${p.id}`); toast(r.message || 'Product deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title="Products" sub="Manage your catalog, variants, prices and stock">
        <button className="btn" onClick={() => api.download('/products-export', 'products.xlsx').catch((e) => toast(e.message, 'err'))}>Export</button>
        <Link className="btn" to="/products/import">Bulk upload</Link>
        <Link className="btn primary" to="/products/new">+ Add product</Link>
      </PageHead>
      <div className="filters">
        <input type="search" placeholder="Search name or SKU…" value={q} onChange={(e) => { setQ(e.target.value); set('page', ''); }} />
        <select value={f.category_id} onChange={(e) => set('category_id', e.target.value)}>
          <option value="">All categories</option>
          {(cats || []).map((c) => <option key={c.id} value={c.id}>{c.parent_name ? `${c.parent_name} › ` : ''}{c.name}</option>)}
        </select>
        <select value={f.status} onChange={(e) => set('status', e.target.value)}>
          <option value="">Any status</option><option value="active">Active</option><option value="inactive">Inactive</option>
        </select>
        <select value={f.stock} onChange={(e) => set('stock', e.target.value)}>
          <option value="">Any stock</option><option value="low">Low stock</option><option value="out">Out of stock</option>
        </select>
      </div>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !data ? <Loading /> : data && (
        <>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Product</th><th>Category</th><th>Variants</th><th className="num">Price</th><th className="num">Stock</th><th className="num">Sold</th><th>Active</th><th /></tr></thead>
              <tbody>
                {data.data.map((p) => {
                  const low = p.variants.some((v) => v.stock <= v.low_stock_threshold);
                  const prices = p.variants.map((v) => v.price);
                  return (
                    <tr key={p.id}>
                      <td><div className="prod-cell">{p.image ? <img className="thumb" src={p.image} alt="" /> : <div className="thumb" />}
                        <div><Link to={`/products/${p.id}`}>{p.name}</Link>{p.is_featured ? <> <Badge value="Featured" tone="violet" /></> : null}
                          <div className="muted" style={{ fontSize: 12 }}>{p.variants.map((v) => v.sku).join(', ')}</div></div></div></td>
                      <td>{p.category_name || <span className="muted">—</span>}</td>
                      <td>{p.variants.map((v) => v.label).join(', ')}</td>
                      <td className="num">{prices.length ? (Math.min(...prices) === Math.max(...prices) ? inr(prices[0]) : `${inr(Math.min(...prices))} – ${inr(Math.max(...prices))}`) : '—'}</td>
                      <td className="num">{p.total_stock} {low && <Badge value={p.total_stock === 0 ? 'Out' : 'Low'} tone={p.total_stock === 0 ? 'red' : 'amber'} />}</td>
                      <td className="num">{p.sold_count}</td>
                      <td><Toggle checked={!!p.is_active} onChange={() => toggle(p)} /></td>
                      <td><div className="actions"><Link className="btn sm" to={`/products/${p.id}`}>Edit</Link><button className="btn sm danger" onClick={() => remove(p)}>Delete</button></div></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {data.data.length === 0 && <Empty>No products match these filters.</Empty>}
          </div>
          <Pager meta={data.meta} onPage={(p) => set('page', p)} />
        </>
      )}
    </>
  );
}
