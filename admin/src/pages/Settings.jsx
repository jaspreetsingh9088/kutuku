import { useState } from 'react';
import { api } from '../api';
import { useAdmin } from '../adminContext';
import { Badge, Field, Modal, PageHead, Toggle, fmtDate, label, useLoad, useToast } from '../components/ui';

function ChangePassword() {
  const toast = useToast();
  const [f, setF] = useState({ current: '', next: '', confirm: '' });
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (f.next !== f.confirm) return toast('New passwords do not match', 'err');
    setBusy(true);
    try { await api.post('/auth/change-password', { current: f.current, next: f.next }); toast('Password changed'); setF({ current: '', next: '', confirm: '' }); }
    catch (err) { toast(err.message, 'err'); } finally { setBusy(false); }
  };
  return (
    <form className="card stack" onSubmit={submit}>
      <h2 style={{ margin: 0 }}>Change password</h2>
      <Field label="Current password"><input type="password" required value={f.current} onChange={(e) => setF({ ...f, current: e.target.value })} /></Field>
      <Field label="New password" hint="At least 8 characters"><input type="password" minLength={8} required value={f.next} onChange={(e) => setF({ ...f, next: e.target.value })} /></Field>
      <Field label="Confirm new password"><input type="password" required value={f.confirm} onChange={(e) => setF({ ...f, confirm: e.target.value })} /></Field>
      <div><button className="btn primary" disabled={busy}>Update password</button></div>
    </form>
  );
}

function PushNotification() {
  const toast = useToast();
  const [f, setF] = useState({ title: '', body: '', product_id: '' });
  const [busy, setBusy] = useState(false);
  const send = async (e) => {
    e.preventDefault();
    if (!window.confirm('Send this notification to all app users?')) return;
    setBusy(true);
    try {
      await api.post('/notifications/broadcast', { title: f.title, body: f.body, ...(f.product_id ? { product_id: Number(f.product_id) } : {}) });
      toast('Notification sent'); setF({ title: '', body: '', product_id: '' });
    } catch (err) { toast(err.message, 'err'); } finally { setBusy(false); }
  };
  return (
    <form className="card stack" onSubmit={send}>
      <h2 style={{ margin: 0 }}>Send promotional notification</h2>
      <p className="muted" style={{ margin: 0 }}>Goes to every app user who allowed notifications. Order updates are sent automatically.</p>
      <Field label="Title"><input required maxLength={100} value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} placeholder="Weekend sale is live!" /></Field>
      <Field label="Message"><textarea required maxLength={300} rows={3} value={f.body} onChange={(e) => setF({ ...f, body: e.target.value })} /></Field>
      <Field label="Open product (ID, optional)"><input type="number" value={f.product_id} onChange={(e) => setF({ ...f, product_id: e.target.value })} /></Field>
      <div><button className="btn primary" disabled={busy}>Send to all users</button></div>
    </form>
  );
}

function Staff() {
  const toast = useToast();
  const me = useAdmin();
  const [rows, , , reload] = useLoad(() => api.get('/auth/users'));
  const [add, setAdd] = useState(null);
  const create = async () => {
    try { await api.post('/auth/users', add); toast('Staff account created'); setAdd(null); reload(); } catch (e) { toast(e.message, 'err'); }
  };
  const patch = async (u, body) => { try { await api.patch(`/auth/users/${u.id}`, body); reload(); } catch (e) { toast(e.message, 'err'); } };
  return (
    <div className="card" style={{ padding: 0 }}>
      <div className="inline" style={{ justifyContent: 'space-between', padding: '16px 18px 4px' }}>
        <h2 style={{ margin: 0 }}>Admin users</h2>
        <button className="btn sm" onClick={() => setAdd({ name: '', email: '', password: '', role: 'manager' })}>+ Add user</button>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Added</th><th>Active</th></tr></thead>
          <tbody>
            {(rows || []).map((u) => (
              <tr key={u.id}>
                <td>{u.name} {u.id === me.id && <Badge value="You" tone="blue" />}</td><td>{u.email}</td>
                <td>{u.id === me.id ? label(u.role) : (
                  <select value={u.role} style={{ width: 'auto' }} onChange={(e) => patch(u, { role: e.target.value })}>
                    <option value="superadmin">Superadmin</option><option value="manager">Manager</option><option value="staff">Staff</option>
                  </select>)}</td>
                <td>{fmtDate(u.created_at, false)}</td>
                <td><Toggle checked={!!u.is_active} disabled={u.id === me.id} onChange={(v) => patch(u, { is_active: v })} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {add && (
        <Modal title="Add admin user" onClose={() => setAdd(null)} footer={<><button className="btn" onClick={() => setAdd(null)}>Cancel</button><button className="btn primary" onClick={create}>Create</button></>}>
          <div className="stack">
            <Field label="Name"><input value={add.name} onChange={(e) => setAdd({ ...add, name: e.target.value })} /></Field>
            <Field label="Email"><input type="email" value={add.email} onChange={(e) => setAdd({ ...add, email: e.target.value })} /></Field>
            <Field label="Temporary password" hint="At least 8 characters; ask them to change it after first login"><input value={add.password} onChange={(e) => setAdd({ ...add, password: e.target.value })} /></Field>
            <Field label="Role" hint="Only superadmins can manage admin users">
              <select value={add.role} onChange={(e) => setAdd({ ...add, role: e.target.value })}><option value="manager">Manager</option><option value="staff">Staff</option><option value="superadmin">Superadmin</option></select>
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}

export default function Settings() {
  const me = useAdmin();
  return (
    <>
      <PageHead title="Settings" sub={`Signed in as ${me.email} (${label(me.role)})`} />
      <div className="grid g2">
        <ChangePassword />
        <PushNotification />
      </div>
      {me.role === 'superadmin' && <div style={{ marginTop: 16 }}><Staff /></div>}
    </>
  );
}
