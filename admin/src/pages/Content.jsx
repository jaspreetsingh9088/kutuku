import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { Empty, ErrorBox, Field, ImageUpload, Loading, Modal, PageHead, Toggle, fmtDate, useLoad, useToast } from '../components/ui';

const TABS = [['onboarding', 'Onboarding slides'], ['faqs', 'Help & Support FAQs'], ['legal', 'Legal & Policies'], ['searches', 'Popular searches']];

export default function Content() {
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') || 'onboarding';
  return (
    <>
      <PageHead title="App content" sub="Text and images shown in the app's onboarding, Help and Support and Legal screens" />
      <div className="tabs">{TABS.map(([k, t]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => setSp({ tab: k })}>{t}</button>)}</div>
      {tab === 'onboarding' && <Onboarding />}
      {tab === 'faqs' && <Faqs />}
      {tab === 'legal' && <Legal />}
      {tab === 'searches' && <Searches />}
    </>
  );
}

function Onboarding() {
  const toast = useToast();
  const [rows, loading, error, reload] = useLoad(() => api.get('/onboarding'));
  const [edit, setEdit] = useState(null);
  const save = async () => {
    const { id, ...b } = edit;
    b.display_order = Number(b.display_order) || 0;
    try { if (id) await api.put(`/onboarding/${id}`, b); else await api.post('/onboarding', b); toast('Slide saved'); setEdit(null); reload(); } catch (e) { toast(e.message, 'err'); }
  };
  const remove = async (s) => { if (window.confirm('Delete this slide?')) { await api.del(`/onboarding/${s.id}`).catch((e) => toast(e.message, 'err')); reload(); } };
  if (error) return <ErrorBox error={error} onRetry={reload} />;
  if (loading && !rows) return <Loading />;
  return (
    <>
      <div className="actions" style={{ marginBottom: 12 }}><button className="btn primary" onClick={() => setEdit({ title: '', subtitle: '', image_url: '', display_order: rows.length, is_active: true })}>+ Add slide</button></div>
      <div className="grid g3">
        {rows.map((s) => (
          <div key={s.id} className="card" style={{ padding: 0, overflow: 'hidden', opacity: s.is_active ? 1 : 0.55 }}>
            <img src={s.image_url} alt="" style={{ width: '100%', aspectRatio: '4/3', objectFit: 'cover', display: 'block' }} />
            <div style={{ padding: 14 }}>
              <strong>{s.title}</strong>
              <p className="muted" style={{ margin: '6px 0 10px' }}>{s.subtitle}</p>
              <div className="actions"><button className="btn sm" onClick={() => setEdit({ ...s, subtitle: s.subtitle || '', is_active: !!s.is_active })}>Edit</button><button className="btn sm danger" onClick={() => remove(s)}>Delete</button></div>
            </div>
          </div>
        ))}
      </div>
      {rows.length === 0 && <Empty>No slides — the app skips onboarding.</Empty>}
      {edit && (
        <Modal title={edit.id ? 'Edit slide' : 'Add slide'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={!edit.title || !edit.image_url} onClick={save}>Save</button></>}>
          <div className="stack">
            <div className="field"><span className="field-label">Image *</span>
              {edit.image_url && <img src={edit.image_url} alt="" style={{ width: '100%', aspectRatio: '4/3', objectFit: 'cover', borderRadius: 12 }} />}
              <div className="inline"><ImageUpload folder="onboarding" onUploaded={(u) => setEdit((x) => ({ ...x, image_url: u[0] }))}>Upload</ImageUpload>
                <input placeholder="…or image URL" value={edit.image_url} onChange={(e) => setEdit({ ...edit, image_url: e.target.value })} /></div>
            </div>
            <Field label="Title *"><input value={edit.title} onChange={(e) => setEdit({ ...edit, title: e.target.value })} /></Field>
            <Field label="Subtitle"><textarea rows={2} value={edit.subtitle} onChange={(e) => setEdit({ ...edit, subtitle: e.target.value })} /></Field>
            <Field label="Order"><input type="number" value={edit.display_order} onChange={(e) => setEdit({ ...edit, display_order: e.target.value })} /></Field>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Show in app</span>
          </div>
        </Modal>
      )}
    </>
  );
}

function Faqs() {
  const toast = useToast();
  const [rows, loading, error, reload] = useLoad(() => api.get('/faqs'));
  const [edit, setEdit] = useState(null);
  const save = async () => {
    const { id, ...b } = edit;
    b.display_order = Number(b.display_order) || 0;
    try { if (id) await api.put(`/faqs/${id}`, b); else await api.post('/faqs', b); toast('FAQ saved'); setEdit(null); reload(); } catch (e) { toast(e.message, 'err'); }
  };
  const remove = async (f) => { if (window.confirm('Delete this FAQ?')) { await api.del(`/faqs/${f.id}`).catch((e) => toast(e.message, 'err')); reload(); } };
  if (error) return <ErrorBox error={error} onRetry={reload} />;
  if (loading && !rows) return <Loading />;
  return (
    <>
      <div className="actions" style={{ marginBottom: 12 }}><button className="btn primary" onClick={() => setEdit({ question: '', answer: '', display_order: rows.length, is_active: true })}>+ Add FAQ</button></div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Question</th><th>Answer</th><th className="num">Order</th><th>Visible</th><th /></tr></thead>
          <tbody>
            {rows.map((f) => (
              <tr key={f.id}>
                <td className="wrap"><strong>{f.question}</strong></td>
                <td className="wrap muted" style={{ maxWidth: 460 }}>{f.answer}</td>
                <td className="num">{f.display_order}</td>
                <td>{f.is_active ? 'Yes' : 'No'}</td>
                <td><div className="actions"><button className="btn sm" onClick={() => setEdit({ ...f, is_active: !!f.is_active })}>Edit</button><button className="btn sm danger" onClick={() => remove(f)}>Delete</button></div></td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <Empty>No FAQs yet.</Empty>}
      </div>
      {edit && (
        <Modal wide title={edit.id ? 'Edit FAQ' : 'Add FAQ'} onClose={() => setEdit(null)}
          footer={<><button className="btn" onClick={() => setEdit(null)}>Cancel</button><button className="btn primary" disabled={!edit.question || !edit.answer} onClick={save}>Save</button></>}>
          <div className="stack">
            <Field label="Question *"><input value={edit.question} onChange={(e) => setEdit({ ...edit, question: e.target.value })} /></Field>
            <Field label="Answer *"><textarea rows={5} value={edit.answer} onChange={(e) => setEdit({ ...edit, answer: e.target.value })} /></Field>
            <Field label="Order"><input type="number" value={edit.display_order} onChange={(e) => setEdit({ ...edit, display_order: e.target.value })} /></Field>
            <span className="inline"><Toggle checked={edit.is_active} onChange={(v) => setEdit({ ...edit, is_active: v })} /> Show in app</span>
          </div>
        </Modal>
      )}
    </>
  );
}

function Legal() {
  const toast = useToast();
  const [page, setPage] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => { api.get('/pages/terms').then(setPage).catch(() => setPage({ title: 'Legal and Policies', content: '' })).catch((e) => setError(e.message)); }, []);
  if (error) return <ErrorBox error={error} />;
  if (!page) return <Loading />;
  const save = async () => {
    setSaving(true);
    try { await api.put('/pages/terms', { title: page.title, content: page.content }); toast('Saved — the app shows it immediately'); } catch (e) { toast(e.message, 'err'); } finally { setSaving(false); }
  };
  return (
    <div className="card stack">
      <Field label="Title"><input value={page.title} onChange={(e) => setPage({ ...page, title: e.target.value })} /></Field>
      <Field label="Content" hint="Start a line with ## to make a section heading (e.g. ## Terms). Leave a blank line between paragraphs.">
        <textarea rows={18} value={page.content} onChange={(e) => setPage({ ...page, content: e.target.value })} style={{ fontFamily: 'ui-monospace, monospace', fontSize: 13 }} />
      </Field>
      <div className="inline" style={{ justifyContent: 'space-between' }}>
        <span className="muted">{page.updated_at ? `Last updated ${fmtDate(page.updated_at)}` : ''}</span>
        <button className="btn primary" disabled={saving} onClick={save}>{saving ? 'Saving…' : 'Save'}</button>
      </div>
    </div>
  );
}

function Searches() {
  const [rows, loading, error, reload] = useLoad(() => api.get('/searches'));
  if (error) return <ErrorBox error={error} onRetry={reload} />;
  if (loading && !rows) return <Loading />;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Search term</th><th className="num">All time</th><th className="num">Today</th><th>Last searched</th></tr></thead>
        <tbody>{rows.map((r) => <tr key={r.term}><td>{r.term}</td><td className="num">{r.total_count}</td><td className="num">{r.day && new Date(r.day).toDateString() === new Date().toDateString() ? r.day_count : 0}</td><td>{fmtDate(r.last_searched_at)}</td></tr>)}</tbody>
      </table>
      {rows.length === 0 && <Empty>No searches yet. The app's "Popular Search" list is built from these.</Empty>}
    </div>
  );
}
