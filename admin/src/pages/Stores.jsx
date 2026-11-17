import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Badge, Empty, ErrorBox, Field, ImageUpload, Loading, Modal, PageHead, Toggle, useLoad, useToast } from '../components/ui';

const blank = { name: '', logo_url: '', description: '', city: '', is_verified: false, is_active: true, display_order: 0 };

export default function Stores() {
  const toast = useToast();
  const [rows, loading, error, reload] = useLoad(() => api.get('/stores'));
  const [edit, setEdit] = useState(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const { id, slug, created_at, product_count, follower_count, ...body } = edit;
    body.display_order = Number(body.display_order) || 0;
    body.logo_url = body.logo_url || null;
    try {
      if (id) await api.put(`/stores/${id}`, body); else await api.post('/stores', body);
      toast('Store saved'); setEdit(null); reload();
    } catch (e) { toast(e.message, 'err'); } finally { setSaving(false); }
  };
  const remove = async (s) => {
    if (!window.confirm(`Delete store "${s.name}"?`)) return;
    try { await api.del(`/stores/${s.id}`); toast('Store deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };
  const toggle = async (s) => {
    try {
      await api.put(`/stores/${s.id}`, { name: s.name, logo_url: s.logo_url || null, description: s.description, city: s.city, is_verified: !!s.is_verified, is_active: !s.is_active, display_order: s.display_order });
      reload();
    } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title="Stores" sub="Brands / sellers shown on products, the store page and search results">
        <button className="btn primary" onClick={() => setEdit({ ...blank })}>+ Add store</button>
      </PageHead>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !rows ? <Loading /> : rows && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Store</th><th>City</th><th className="num">Products</th><th className="num">Followers</th><th>Verified</th><th>Active</th><th /></tr></thead>
            <tbody>
              {rows.map((s) => (
                <tr key={s.id}>
                  <td><div className="prod-cell">{s.logo_url ? <img className="thumb" style={{ borderRadius: '50%' }} src={s.logo_url} alt="" /> : <div className="thumb" style={{ borderRadius: '50%' }} />}
                    <div><strong>{s.name}</strong>{s.description && <div className="muted" style={{ fontSize: 12, maxWidth: 360, whiteSpace: 'normal' }}>{s.description.slice(0, 90)}</div>}</div></div></td>
                  <td>{s.city || <span className="muted">—</span>}</td>
                  <td className="num"><Link to={`/products?store_id=${s.id}`}>{s.product_count}</Link></td>
                  <td className="num">{s.follower_count}</td>
                  <td>{s.is_verified ? <Badge value="Verified" tone="blue" /> : <span className="muted">—</span>}</td>
                  <td><Toggle checked={!!s.is_active} onChange={() => toggle(s)} /></td>
                  <td><div className="actions"><button className="btn sm" onClick={() => setEdit({ ...s, logo_url: s.logo_url || '', description: s.description || '', city: s.city || '', is_verified: !!s.is_verified, is_active: !!s.is_active })}>Edit</button>
                    <button className="btn sm danger" onClick={() => remove(s)}>Delete</button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
          {rows.length === 0 && <Empty>No stores yet. Add one, then assign products to it.</Empty>}
        </div>
      )}
      {edit && (
        <Modal title={edit.id ? 'Edit store' : 'Add store'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={saving || !edit.name.trim()} onClick={save}>{saving ? 'Saving…' : 'Save'}</button></>}>
          <div className="stack">
            <div className="field"><span className="field-label">Logo</span>
              <div className="inline">
                {edit.logo_url && <img src={edit.logo_url} alt="" style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover' }} />}
                <ImageUpload folder="stores" onUploaded={(u) => setEdit((x) => ({ ...x, logo_url: u[0] }))}>{edit.logo_url ? 'Replace' : 'Upload logo'}</ImageUpload>
              </div>
            </div>
            <Field label="Store name *"><input value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="City" hint="Used by the Location filter in the app"><input value={edit.city} onChange={(e) => setEdit({ ...edit, city: e.target.value })} /></Field>
            <Field label="Description"><textarea rows={3} value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} /></Field>
            <Field label="Display order"><input type="number" value={edit.display_order} onChange={(e) => setEdit({ ...edit, display_order: e.target.value })} /></Field>
            <span className="inline"><Toggle checked={edit.is_verified} onChange={(v) => setEdit({ ...edit, is_verified: v })} /> Verified badge (blue tick)</span>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Active</span>
          </div>
        </Modal>
      )}
    </>
  );
}
