import { useState } from 'react';
import { api } from '../api';
import { Empty, ErrorBox, Field, ImageUpload, Loading, Modal, PageHead, Toggle, label, useLoad, useToast } from '../components/ui';

const blank = { title: '', description: '', image_url: '', redirect_type: 'none', redirect_value: '', display_order: 0, is_active: true };

export default function Banners() {
  const toast = useToast();
  const [rows, loading, error, reload] = useLoad(() => api.get('/banners'));
  const [cats] = useLoad(() => api.get('/categories'));
  const [edit, setEdit] = useState(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const { id, created_at, ...body } = edit;
    body.display_order = Number(body.display_order) || 0;
    if (body.redirect_type === 'none') body.redirect_value = null;
    try {
      if (id) await api.put(`/banners/${id}`, body); else await api.post('/banners', body);
      toast('Banner saved'); setEdit(null); reload();
    } catch (e) { toast(e.message, 'err'); } finally { setSaving(false); }
  };
  const toggle = async (b) => { try { await api.patch(`/banners/${b.id}/status`, { is_active: !b.is_active }); reload(); } catch (e) { toast(e.message, 'err'); } };
  const remove = async (b) => {
    if (!window.confirm(`Delete banner "${b.title}"?`)) return;
    try { await api.del(`/banners/${b.id}`); toast('Banner deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title="Banners" sub="Promotional banners on the app home screen (recommended size 1200 × 500)">
        <button className="btn primary" onClick={() => setEdit({ ...blank })}>+ Add banner</button>
      </PageHead>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !rows ? <Loading /> : rows && (
        rows.length === 0 ? <div className="card"><Empty>No banners yet.</Empty></div> : (
          <div className="grid g3">
            {rows.map((b) => (
              <div className="card" key={b.id} style={{ padding: 0, overflow: 'hidden' }}>
                <img src={b.image_url} alt="" style={{ width: '100%', aspectRatio: '12/5', objectFit: 'cover', display: 'block', background: '#eee', opacity: b.is_active ? 1 : 0.45 }} />
                <div style={{ padding: 14 }}>
                  <div className="inline" style={{ justifyContent: 'space-between' }}><strong>{b.title}</strong><Toggle checked={!!b.is_active} onChange={() => toggle(b)} /></div>
                  {b.description && <p className="muted" style={{ margin: '4px 0 0' }}>{b.description}</p>}
                  <p className="muted" style={{ fontSize: 12, margin: '8px 0' }}>Order {b.display_order} · Opens: {b.redirect_type === 'none' ? 'nothing' : `${label(b.redirect_type)} ${b.redirect_value || ''}`}</p>
                  <div className="actions"><button className="btn sm" onClick={() => setEdit({ ...b, description: b.description || '', redirect_value: b.redirect_value || '', is_active: !!b.is_active })}>Edit</button><button className="btn sm danger" onClick={() => remove(b)}>Delete</button></div>
                </div>
              </div>
            ))}
          </div>
        )
      )}

      {edit && (
        <Modal title={edit.id ? 'Edit banner' : 'Add banner'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={saving || !edit.title || !edit.image_url} onClick={save}>{saving ? 'Saving…' : 'Save'}</button></>}>
          <div className="stack">
            <div className="field"><span className="field-label">Image *</span>
              {edit.image_url && <img src={edit.image_url} alt="" style={{ width: '100%', aspectRatio: '12/5', objectFit: 'cover', borderRadius: 8 }} />}
              <div className="inline"><ImageUpload folder="banners" onUploaded={(u) => setEdit((x) => ({ ...x, image_url: u[0] }))}>{edit.image_url ? 'Replace image' : 'Upload image'}</ImageUpload>
                <input placeholder="…or paste image URL" value={edit.image_url} onChange={(e) => setEdit({ ...edit, image_url: e.target.value })} /></div>
            </div>
            <Field label="Title *"><input value={edit.title} onChange={(e) => setEdit({ ...edit, title: e.target.value })} /></Field>
            <Field label="Description"><input value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} /></Field>
            <Field label="When tapped, open">
              <select value={edit.redirect_type} onChange={(e) => setEdit({ ...edit, redirect_type: e.target.value, redirect_value: '' })}>
                <option value="none">Nothing</option><option value="category">A category</option><option value="product">A product (by ID)</option><option value="url">A web link</option>
              </select>
            </Field>
            {edit.redirect_type === 'category' && (
              <Field label="Category"><select value={edit.redirect_value} onChange={(e) => setEdit({ ...edit, redirect_value: e.target.value })}>
                <option value="">Choose…</option>{(cats || []).map((c) => <option key={c.id} value={c.id}>{c.parent_name ? `${c.parent_name} › ` : ''}{c.name}</option>)}</select></Field>
            )}
            {edit.redirect_type === 'product' && <Field label="Product ID" hint="Find the ID in the product's URL in this admin panel"><input type="number" value={edit.redirect_value} onChange={(e) => setEdit({ ...edit, redirect_value: e.target.value })} /></Field>}
            {edit.redirect_type === 'url' && <Field label="Link"><input type="url" placeholder="https://" value={edit.redirect_value} onChange={(e) => setEdit({ ...edit, redirect_value: e.target.value })} /></Field>}
            <Field label="Display order" hint="Lower numbers appear first"><input type="number" value={edit.display_order} onChange={(e) => setEdit({ ...edit, display_order: e.target.value })} /></Field>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Active</span>
          </div>
        </Modal>
      )}
    </>
  );
}
