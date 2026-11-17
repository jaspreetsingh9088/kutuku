import { useState } from 'react';
import { api } from '../api';
import { Badge, Empty, ErrorBox, Field, Loading, Modal, PageHead, Toggle, fmtDate, inr, useLoad, useToast } from '../components/ui';

const blank = { code: '', description: '', discount_type: 'percent', discount_value: '', max_discount: '', min_order_value: 0, valid_from: '', valid_to: '', usage_limit: '', per_customer_limit: 1, is_active: true };
const toLocal = (d) => (d ? new Date(new Date(d).getTime() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : '');
const toSql = (v) => (v ? new Date(v).toISOString().slice(0, 19).replace('T', ' ') : null);

function couponState(c) {
  const now = new Date();
  if (!c.is_active) return ['Disabled', 'gray'];
  if (c.valid_to && new Date(c.valid_to) < now) return ['Expired', 'red'];
  if (c.valid_from && new Date(c.valid_from) > now) return ['Scheduled', 'blue'];
  if (c.usage_limit && c.used_count >= c.usage_limit) return ['Used up', 'amber'];
  return ['Live', 'green'];
}

export default function Coupons() {
  const toast = useToast();
  const [rows, loading, error, reload] = useLoad(() => api.get('/coupons'));
  const [edit, setEdit] = useState(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    const e = edit;
    const body = {
      code: e.code, description: e.description || null, discount_type: e.discount_type, discount_value: Number(e.discount_value),
      max_discount: e.discount_type === 'percent' && e.max_discount ? Number(e.max_discount) : null, min_order_value: Number(e.min_order_value) || 0,
      valid_from: toSql(e.valid_from), valid_to: toSql(e.valid_to), usage_limit: e.usage_limit ? Number(e.usage_limit) : null,
      per_customer_limit: Number(e.per_customer_limit) || 1, is_active: e.is_active,
    };
    try {
      if (e.id) await api.put(`/coupons/${e.id}`, body); else await api.post('/coupons', body);
      toast('Coupon saved'); setEdit(null); reload();
    } catch (err) { toast(err.message, 'err'); } finally { setSaving(false); }
  };
  const toggle = async (c) => { try { await api.patch(`/coupons/${c.id}/status`, { is_active: !c.is_active }); reload(); } catch (e) { toast(e.message, 'err'); } };
  const remove = async (c) => {
    if (!window.confirm(`Delete coupon ${c.code}?`)) return;
    try { await api.del(`/coupons/${c.id}`); toast('Coupon deleted'); reload(); } catch (e) { toast(e.message, 'err'); }
  };

  return (
    <>
      <PageHead title="Coupons & discounts" sub="Promo codes customers can apply at checkout">
        <button className="btn primary" onClick={() => setEdit({ ...blank })}>+ Create coupon</button>
      </PageHead>
      {error && <ErrorBox error={error} onRetry={reload} />}
      {loading && !rows ? <Loading /> : rows && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Code</th><th>Discount</th><th className="num">Min. order</th><th>Validity</th><th className="num">Used</th><th>Status</th><th>Enabled</th><th /></tr></thead>
            <tbody>
              {rows.map((c) => {
                const [st, tone] = couponState(c);
                return (
                  <tr key={c.id}>
                    <td><strong>{c.code}</strong>{c.description && <div className="muted" style={{ fontSize: 12 }}>{c.description}</div>}</td>
                    <td>{c.discount_type === 'percent' ? `${c.discount_value}%${c.max_discount ? ` (max ${inr(c.max_discount)})` : ''}` : inr(c.discount_value)}</td>
                    <td className="num">{inr(c.min_order_value)}</td>
                    <td>{c.valid_from || c.valid_to ? `${c.valid_from ? fmtDate(c.valid_from, false) : '…'} → ${c.valid_to ? fmtDate(c.valid_to, false) : '…'}` : <span className="muted">No limit</span>}</td>
                    <td className="num">{c.used_count}{c.usage_limit ? ` / ${c.usage_limit}` : ''}</td>
                    <td><Badge value={st} tone={tone} /></td>
                    <td><Toggle checked={!!c.is_active} onChange={() => toggle(c)} /></td>
                    <td><div className="actions"><button className="btn sm" onClick={() => setEdit({ ...c, description: c.description || '', max_discount: c.max_discount || '', usage_limit: c.usage_limit || '', valid_from: toLocal(c.valid_from), valid_to: toLocal(c.valid_to), is_active: !!c.is_active })}>Edit</button>
                      <button className="btn sm danger" onClick={() => remove(c)}>Delete</button></div></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {rows.length === 0 && <Empty>No coupons yet.</Empty>}
        </div>
      )}

      {edit && (
        <Modal wide title={edit.id ? `Edit ${edit.code}` : 'Create coupon'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={saving || !edit.code || !edit.discount_value} onClick={save}>{saving ? 'Saving…' : 'Save coupon'}</button></>}>
          <div className="form-grid">
            <Field label="Coupon code *"><input value={edit.code} onChange={(e) => setEdit({ ...edit, code: e.target.value.toUpperCase().replace(/\s/g, '') })} placeholder="e.g. WELCOME10" /></Field>
            <Field label="Description" hint="Shown to customers in the app"><input value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} /></Field>
            <Field label="Discount type">
              <select value={edit.discount_type} onChange={(e) => setEdit({ ...edit, discount_type: e.target.value })}><option value="percent">Percentage (%)</option><option value="fixed">Fixed amount (₹)</option></select>
            </Field>
            <Field label={edit.discount_type === 'percent' ? 'Discount % *' : 'Discount amount (₹) *'}><input type="number" min="0" step="0.01" value={edit.discount_value} onChange={(e) => setEdit({ ...edit, discount_value: e.target.value })} /></Field>
            {edit.discount_type === 'percent' && <Field label="Maximum discount (₹)" hint="Optional cap"><input type="number" min="0" value={edit.max_discount} onChange={(e) => setEdit({ ...edit, max_discount: e.target.value })} /></Field>}
            <Field label="Minimum order value (₹)"><input type="number" min="0" value={edit.min_order_value} onChange={(e) => setEdit({ ...edit, min_order_value: e.target.value })} /></Field>
            <Field label="Valid from"><input type="datetime-local" value={edit.valid_from} onChange={(e) => setEdit({ ...edit, valid_from: e.target.value })} /></Field>
            <Field label="Valid until"><input type="datetime-local" value={edit.valid_to} onChange={(e) => setEdit({ ...edit, valid_to: e.target.value })} /></Field>
            <Field label="Total usage limit" hint="Leave empty for unlimited"><input type="number" min="1" value={edit.usage_limit} onChange={(e) => setEdit({ ...edit, usage_limit: e.target.value })} /></Field>
            <Field label="Uses per customer"><input type="number" min="1" value={edit.per_customer_limit} onChange={(e) => setEdit({ ...edit, per_customer_limit: e.target.value })} /></Field>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Enabled</span>
          </div>
        </Modal>
      )}
    </>
  );
}
