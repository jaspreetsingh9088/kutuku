import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Empty, ErrorBox, Field, ImageUpload, Loading, Modal, PageHead, Toggle, useLoad, useToast } from '../components/ui';

const blank = { name: '', parent_id: '', image_url: '', display_order: 0, is_active: true };

export default function Categories() {
  const toast = useToast();
  const [cats, loading, error, reload] = useLoad(() => api.get('/categories'));
  const [edit, setEdit] = useState(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const body = { ...edit, parent_id: edit.parent_id ? Number(edit.parent_id) : null, display_order: Number(edit.display_order) || 0, image_url: edit.image_url || null };
    delete body.id; delete body.slug; delete body.parent_name; delete body.product_count; delete body.created_at;
    try {
      if (edit.id) await api.put(`/categories/${edit.id}`, body); else await api.post('/categories', body);
      toast('Category saved'); setEdit(null); reload();
    } catch (e) { toast(e.message, 'err'); } finally { setSaving(false); }
  };
  const remove = async (c) => {
    if (!window.confirm(`Delete category "${c.name}"?`)) return;
    try { await api.del(`/categories/${c.id}`); toast('Category deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };
  const toggle = async (c) => {
    try {
      await api.put(`/categories/${c.id}`, { name: c.name, parent_id: c.parent_id, image_url: c.image_url || null, display_order: c.display_order, is_active: !c.is_active });
      reload();
    } catch (e) { toast(e.message, 'err'); }
  };

  const parents = (cats || []).filter((c) => !c.parent_id && (!edit || c.id !== edit.id));

  return (
    <>
      <PageHead title="Categories" sub="Organise products into categories and subcategories">
        <button className="btn primary" onClick={() => setEdit({ ...blank })}>+ Add category</button>
      </PageHead>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !cats ? <Loading /> : cats && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Parent</th><th className="num">Products</th><th className="num">Order</th><th>Active</th><th /></tr></thead>
            <tbody>
              {cats.map((c) => (
                <tr key={c.id}>
                  <td><div className="prod-cell" style={{ paddingLeft: c.parent_id ? 28 : 0 }}>
                    {c.image_url ? <img className="thumb" src={c.image_url} alt="" /> : <div className="thumb" />}
                    <span>{c.parent_id ? '↳ ' : ''}<strong style={{ fontWeight: c.parent_id ? 400 : 600 }}>{c.name}</strong></span></div></td>
                  <td>{c.parent_name || <span className="muted">Top level</span>}</td>
                  <td className="num"><Link to={`/products?category_id=${c.id}`}>{c.product_count}</Link></td>
                  <td className="num">{c.display_order}</td>
                  <td><Toggle checked={!!c.is_active} onChange={() => toggle(c)} /></td>
                  <td><div className="actions"><button className="btn sm" onClick={() => setEdit({ ...c, parent_id: c.parent_id || '', is_active: !!c.is_active })}>Edit</button><button className="btn sm danger" onClick={() => remove(c)}>Delete</button></div></td>
                </tr>
              ))}
            </tbody>
          </table>
          {cats.length === 0 && <Empty>No categories yet.</Empty>}
        </div>
      )}

      {edit && (
        <Modal title={edit.id ? 'Edit category' : 'Add category'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={saving || !edit.name.trim()} onClick={save}>{saving ? 'Saving…' : 'Save'}</button></>}>
          <div className="stack">
            <Field label="Name *"><input autoFocus value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></Field>
            <Field label="Parent category" hint="Leave empty for a top-level category">
              <select value={edit.parent_id} onChange={(e) => setEdit({ ...edit, parent_id: e.target.value })}>
                <option value="">— Top level —</option>
                {parents.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Display order" hint="Lower numbers appear first"><input type="number" value={edit.display_order} onChange={(e) => setEdit({ ...edit, display_order: e.target.value })} /></Field>
            <div className="field"><span className="field-label">Image</span>
              <div className="inline">
                {edit.image_url && <img className="thumb" src={edit.image_url} alt="" style={{ width: 64, height: 64 }} />}
                <ImageUpload folder="categories" onUploaded={(u) => setEdit((x) => ({ ...x, image_url: u[0] }))}>{edit.image_url ? 'Replace' : 'Upload'}</ImageUpload>
                {edit.image_url && <button className="btn sm" onClick={() => setEdit({ ...edit, image_url: '' })}>Remove</button>}
              </div>
            </div>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Visible in app</span>
          </div>
        </Modal>
      )}
    </>
  );
}
