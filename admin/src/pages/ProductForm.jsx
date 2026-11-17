import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox, Field, ImageUpload, Loading, PageHead, Toggle, useToast } from '../components/ui';

const blankVariant = () => ({ sku: '', label: '', mrp: '', price: '', stock: '', low_stock_threshold: 5, is_active: true });
const GST_RATES = [0, 5, 12, 18, 28];

export default function ProductForm() {
  const { id } = useParams();
  const isNew = !id;
  const nav = useNavigate();
  const toast = useToast();
  const [cats, setCats] = useState([]);
  const [p, setP] = useState(isNew ? {
    name: '', category_id: '', description: '', hsn_code: '', gst_rate: 5, is_featured: false, is_active: true,
    images: [], specifications: [], variants: [blankVariant()],
  } : null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [urlInput, setUrlInput] = useState('');

  useEffect(() => { api.get('/categories').then(setCats).catch(() => {}); }, []);
  useEffect(() => {
    if (isNew) return;
    api.get(`/products/${id}`).then((r) => setP({
      ...r,
      category_id: r.category_id || '',
      is_featured: !!r.is_featured, is_active: !!r.is_active,
      images: r.images.map((i) => i.url),
      specifications: Array.isArray(r.specifications) ? r.specifications : [],
      variants: r.variants.map((v) => ({ ...v, is_active: !!v.is_active })),
    })).catch((e) => setError(e.message));
  }, [id, isNew]);

  if (!p) return error ? <ErrorBox error={error} /> : <Loading />;

  const set = (k, v) => setP((x) => ({ ...x, [k]: v }));
  const setVariant = (i, k, v) => setP((x) => ({ ...x, variants: x.variants.map((vv, j) => (j === i ? { ...vv, [k]: v } : vv)) }));
  const setSpec = (i, k, v) => setP((x) => ({ ...x, specifications: x.specifications.map((s, j) => (j === i ? { ...s, [k]: v } : s)) }));
  const moveImage = (i, d) => setP((x) => {
    const imgs = [...x.images]; const j = i + d;
    if (j < 0 || j >= imgs.length) return x;
    [imgs[i], imgs[j]] = [imgs[j], imgs[i]];
    return { ...x, images: imgs };
  });

  const save = async (e) => {
    e.preventDefault();
    setError(''); setSaving(true);
    const body = {
      name: p.name, category_id: p.category_id ? Number(p.category_id) : null, description: p.description || null,
      hsn_code: p.hsn_code || null, gst_rate: Number(p.gst_rate), is_featured: p.is_featured, is_active: p.is_active,
      images: p.images, specifications: p.specifications.filter((s) => s.key.trim() && s.value.trim()),
      variants: p.variants.map((v) => ({
        ...(v.id ? { id: v.id } : {}), sku: v.sku, label: v.label, mrp: Number(v.mrp), price: Number(v.price),
        stock: Number(v.stock), low_stock_threshold: Number(v.low_stock_threshold || 0), is_active: v.is_active,
      })),
    };
    try {
      const r = isNew ? await api.post('/products', body) : await api.put(`/products/${id}`, body);
      toast(isNew ? 'Product created' : 'Product saved');
      if (isNew) nav(`/products/${r.id}`, { replace: true });
    } catch (err) { setError(err.message); window.scrollTo({ top: 0, behavior: 'smooth' }); } finally { setSaving(false); }
  };

  return (
    <form onSubmit={save}>
      <PageHead title={isNew ? 'Add product' : p.name} sub={isNew ? 'Create a new product with one or more variants' : `Product #${id}`}>
        <Link className="btn" to="/products">Back to products</Link>
      </PageHead>
      {error && <ErrorBox error={error} />}

      <div className="grid g-main">
        <div>
          <div className="card">
            <h2>Basic details</h2>
            <div className="form-grid">
              <Field label="Product name *" span={2}><input required value={p.name} onChange={(e) => set('name', e.target.value)} /></Field>
              <Field label="Category">
                <select value={p.category_id} onChange={(e) => set('category_id', e.target.value)}>
                  <option value="">— None —</option>
                  {cats.map((c) => <option key={c.id} value={c.id}>{c.parent_name ? `${c.parent_name} › ` : ''}{c.name}</option>)}
                </select>
              </Field>
              <Field label="HSN code"><input value={p.hsn_code || ''} onChange={(e) => set('hsn_code', e.target.value)} /></Field>
              <Field label="GST rate" hint="Prices entered below are treated as GST-inclusive">
                <select value={p.gst_rate} onChange={(e) => set('gst_rate', e.target.value)}>{GST_RATES.map((r) => <option key={r} value={r}>{r}%</option>)}</select>
              </Field>
              <div className="field" style={{ justifyContent: 'flex-end', gap: 12 }}>
                <span className="inline"><Toggle checked={p.is_active} onChange={(v) => set('is_active', v)} /> Active (visible in app)</span>
                <span className="inline"><Toggle checked={p.is_featured} onChange={(v) => set('is_featured', v)} /> Featured on home screen</span>
              </div>
              <Field label="Description" span={2}><textarea rows={5} value={p.description || ''} onChange={(e) => set('description', e.target.value)} /></Field>
            </div>
          </div>

          <div className="card">
            <div className="inline" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
              <h2 style={{ margin: 0 }}>Variants, prices &amp; stock</h2>
              <button type="button" className="btn sm" onClick={() => set('variants', [...p.variants, blankVariant()])}>+ Add variant</button>
            </div>
            <div style={{ overflowX: 'auto' }}>
              <table className="variant-table">
                <thead><tr><th>Weight / qty *</th><th>SKU *</th><th>MRP *</th><th>Sale price *</th><th>Stock *</th><th>Low alert</th><th>Active</th><th /></tr></thead>
                <tbody>
                  {p.variants.map((v, i) => (
                    <tr key={v.id || `n${i}`}>
                      <td><input required placeholder="e.g. 500 g" value={v.label} onChange={(e) => setVariant(i, 'label', e.target.value)} style={{ minWidth: 100 }} /></td>
                      <td><input required value={v.sku} onChange={(e) => setVariant(i, 'sku', e.target.value.toUpperCase())} style={{ minWidth: 110 }} /></td>
                      <td><input required type="number" step="0.01" min="0" value={v.mrp} onChange={(e) => setVariant(i, 'mrp', e.target.value)} style={{ width: 90 }} /></td>
                      <td><input required type="number" step="0.01" min="0" value={v.price} onChange={(e) => setVariant(i, 'price', e.target.value)} style={{ width: 90 }} /></td>
                      <td><input required type="number" min="0" value={v.stock} onChange={(e) => setVariant(i, 'stock', e.target.value)} style={{ width: 80 }} /></td>
                      <td><input type="number" min="0" value={v.low_stock_threshold} onChange={(e) => setVariant(i, 'low_stock_threshold', e.target.value)} style={{ width: 70 }} /></td>
                      <td><Toggle checked={v.is_active} onChange={(x) => setVariant(i, 'is_active', x)} /></td>
                      <td>{p.variants.length > 1 && <button type="button" className="icon-btn" title="Remove" onClick={() => set('variants', p.variants.filter((_, j) => j !== i))}>✕</button>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="hint" style={{ marginTop: 8 }}>The app shows the lowest-priced active variant on product cards. Discount % is calculated from MRP and sale price.</p>
          </div>

          <div className="card">
            <div className="inline" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
              <h2 style={{ margin: 0 }}>Specifications</h2>
              <button type="button" className="btn sm" onClick={() => set('specifications', [...p.specifications, { key: '', value: '' }])}>+ Add row</button>
            </div>
            {p.specifications.length === 0 && <p className="muted">e.g. Brand, Shelf life, Country of origin, Ingredients</p>}
            {p.specifications.map((s, i) => (
              <div key={i} className="inline" style={{ marginBottom: 8 }}>
                <input placeholder="Name" value={s.key} onChange={(e) => setSpec(i, 'key', e.target.value)} />
                <input placeholder="Value" value={s.value} onChange={(e) => setSpec(i, 'value', e.target.value)} />
                <button type="button" className="icon-btn" onClick={() => set('specifications', p.specifications.filter((_, j) => j !== i))}>✕</button>
              </div>
            ))}
          </div>
        </div>

        <div>
          <div className="card">
            <h2>Images</h2>
            <div className="images">
              {p.images.map((url, i) => (
                <div className="img-tile" key={url + i}>
                  <img src={url} alt="" />
                  <button type="button" className="x" onClick={() => set('images', p.images.filter((_, j) => j !== i))}>✕</button>
                  {i === 0 ? <div className="first">Main</div> : (
                    <button type="button" className="x" style={{ left: 4, right: 'auto' }} title="Move left" onClick={() => moveImage(i, -1)}>‹</button>
                  )}
                </div>
              ))}
            </div>
            <div className="actions" style={{ marginTop: 12 }}>
              <ImageUpload folder="products" multiple onUploaded={(urls) => set('images', [...p.images, ...urls].slice(0, 10))}>Upload images</ImageUpload>
            </div>
            <div className="inline" style={{ marginTop: 10 }}>
              <input placeholder="…or paste an image URL" value={urlInput} onChange={(e) => setUrlInput(e.target.value)} />
              <button type="button" className="btn sm" onClick={() => { if (/^https?:\/\//.test(urlInput)) { set('images', [...p.images, urlInput]); setUrlInput(''); } }}>Add</button>
            </div>
            <p className="hint" style={{ marginTop: 8 }}>Up to 10 images. JPG, PNG or WEBP, max 4 MB each. The first image is the main one.</p>
          </div>
        </div>
      </div>

      <div className="sticky-foot">
        <Link className="btn" to="/products">Cancel</Link>
        <button className="btn primary" disabled={saving}>{saving ? 'Saving…' : isNew ? 'Create product' : 'Save changes'}</button>
      </div>
    </form>
  );
}
