import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { Empty, ErrorBox, Loading, PageHead, fmtDate, useLoad, useToast } from '../components/ui';

/** Support inbox: customer conversations with stores / support; staff reply as the store. */
export default function Messages() {
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const active = sp.get('c');
  const [list, loading, error, reload] = useLoad(() => api.get('/conversations'));
  const [convo, setConvo] = useState(null);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const endRef = useRef(null);

  // Load the open conversation and poll for new messages every 5 s
  useEffect(() => {
    if (!active) { setConvo(null); return undefined; }
    let stop = false;
    const load = () => api.get(`/conversations/${active}`).then((c) => { if (!stop) setConvo(c); }).catch((e) => toast(e.message, 'err'));
    load();
    const t = setInterval(() => { load(); reload(); }, 5000);
    return () => { stop = true; clearInterval(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);
  useEffect(() => { endRef.current?.scrollIntoView({ block: 'end' }); }, [convo?.messages?.length]);

  const send = async (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    setSending(true);
    try {
      await api.post(`/conversations/${active}/messages`, { body: text.trim() });
      setText('');
      setConvo(await api.get(`/conversations/${active}`));
      reload();
    } catch (err) { toast(err.message, 'err'); } finally { setSending(false); }
  };

  return (
    <>
      <PageHead title="Messages" sub="Chats from the app's Message screen. Replies are sent as the store and notify the customer." />
      {error && <ErrorBox error={error} onRetry={reload} />}
      <div className="grid" style={{ gridTemplateColumns: 'minmax(260px, 340px) 1fr', alignItems: 'start' }}>
        <div className="card" style={{ padding: 0, maxHeight: '72vh', overflowY: 'auto' }}>
          {loading && !list ? <Loading /> : (list || []).length === 0 ? <Empty>No conversations yet.</Empty> : list.map((c) => (
            <button key={c.id} onClick={() => setSp({ c: c.id })}
              style={{ display: 'block', width: '100%', textAlign: 'left', border: 0, borderBottom: '1px solid var(--line)', padding: '12px 14px', cursor: 'pointer',
                background: String(c.id) === String(active) ? 'var(--brand-soft)' : '#fff', font: 'inherit' }}>
              <div className="inline" style={{ justifyContent: 'space-between' }}>
                <strong>{c.customer_name}</strong>
                {c.admin_unread > 0 && <span className="badge blue">{c.admin_unread}</span>}
              </div>
              <div className="muted" style={{ fontSize: 12 }}>to {c.store_name} · {c.last_message_at ? fmtDate(c.last_message_at) : 'new'}</div>
              <div style={{ fontSize: 13, marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.last_message || '—'}</div>
            </button>
          ))}
        </div>
        <div className="card" style={{ display: 'flex', flexDirection: 'column', height: '72vh', padding: 0 }}>
          {!convo ? <Empty>Select a conversation</Empty> : (
            <>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--line)' }}>
                <strong><Link to={`/customers/${convo.customer_id}`}>{convo.customer_name}</Link></strong>
                <span className="muted"> · {convo.mobile || convo.email} · chatting with {convo.store_name}</span>
              </div>
              <div style={{ flex: 1, overflowY: 'auto', padding: 16, background: '#fafbfc' }}>
                {convo.messages.map((m) => {
                  const mine = m.sender === 'store';
                  return (
                    <div key={m.id} style={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start', marginBottom: 10 }}>
                      <div style={{ maxWidth: '70%', padding: '9px 13px', borderRadius: 14, background: mine ? 'var(--brand)' : '#fff', color: mine ? '#fff' : 'inherit', border: mine ? 0 : '1px solid var(--line)' }}>
                        {m.product_name && <div style={{ fontSize: 12, opacity: 0.85 }}>🛍 Product: <Link style={{ color: 'inherit', textDecoration: 'underline' }} to={`/products/${m.product_id}`}>{m.product_name}</Link></div>}
                        {m.order_number && <div style={{ fontSize: 12, opacity: 0.85 }}>📦 Order: <Link style={{ color: 'inherit', textDecoration: 'underline' }} to={`/orders/${m.order_id}`}>{m.order_number}</Link></div>}
                        {m.body && <div style={{ whiteSpace: 'pre-wrap' }}>{m.body}</div>}
                        <div style={{ fontSize: 11, opacity: 0.7, marginTop: 3, textAlign: 'right' }}>{fmtDate(m.created_at)}</div>
                      </div>
                    </div>
                  );
                })}
                <div ref={endRef} />
              </div>
              <form onSubmit={send} className="inline" style={{ padding: 12, borderTop: '1px solid var(--line)' }}>
                <input placeholder="Type a reply…" value={text} onChange={(e) => setText(e.target.value)} />
                <button className="btn primary" disabled={sending || !text.trim()}>{sending ? 'Sending…' : 'Send'}</button>
              </form>
            </>
          )}
        </div>
      </div>
    </>
  );
}
