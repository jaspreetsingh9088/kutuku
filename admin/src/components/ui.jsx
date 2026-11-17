import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api';

export const inr = (n) => `₹${Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
export const fmtDate = (d, time = true) =>
  d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', ...(time ? { hour: '2-digit', minute: '2-digit' } : {}) }) : '—';
export const label = (s) => String(s || '').replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

const STATUS_TONE = {
  placed: 'blue', confirmed: 'blue', packed: 'violet', shipped: 'violet', out_for_delivery: 'amber', delivered: 'green',
  cancelled: 'red', returned: 'gray', pending_payment: 'gray',
  paid: 'green', pending: 'amber', failed: 'red', refunded: 'gray', partially_refunded: 'amber',
  active: 'green', inactive: 'gray',
};
export function Badge({ value, tone }) {
  return <span className={`badge ${tone || STATUS_TONE[value] || 'gray'}`}>{label(value)}</span>;
}

/** Load data from an async fn; returns [data, loading, error, reload, setData]. */
export function useLoad(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const load = useCallback(() => {
    setState((s) => ({ ...s, loading: true, error: null }));
    return fn().then((data) => setState({ data, loading: false, error: null }))
      .catch((e) => setState({ data: null, loading: false, error: e.message }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { load(); }, [load]);
  return [state.data, state.loading, state.error, load, (d) => setState((s) => ({ ...s, data: typeof d === 'function' ? d(s.data) : d }))];
}

// ---- Toasts ----
const ToastCtx = createContext(() => {});
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  const push = useCallback((msg, type = 'ok') => {
    const id = Math.random();
    setItems((x) => [...x, { id, msg, type }]);
    setTimeout(() => setItems((x) => x.filter((t) => t.id !== id)), 4000);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts">{items.map((t) => <div key={t.id} className={`toast ${t.type}`}>{t.msg}</div>)}</div>
    </ToastCtx.Provider>
  );
}

export function Modal({ title, onClose, children, footer, wide }) {
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="modal-bg" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`}>
        <div className="modal-head"><h3>{title}</h3><button className="icon-btn" onClick={onClose} aria-label="Close">✕</button></div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Pager({ meta, onPage }) {
  if (!meta || meta.pages <= 1) return meta ? <div className="pager muted">{meta.total} records</div> : null;
  return (
    <div className="pager">
      <span className="muted">{meta.total} records · page {meta.page} of {meta.pages}</span>
      <button className="btn sm" disabled={meta.page <= 1} onClick={() => onPage(meta.page - 1)}>‹ Prev</button>
      <button className="btn sm" disabled={meta.page >= meta.pages} onClick={() => onPage(meta.page + 1)}>Next ›</button>
    </div>
  );
}

export function Field({ label: l, children, hint, span }) {
  return (
    <label className={`field ${span ? `span-${span}` : ''}`}>
      <span className="field-label">{l}</span>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </label>
  );
}

export function Toggle({ checked, onChange, disabled }) {
  return (
    <button type="button" role="switch" aria-checked={!!checked} disabled={disabled}
      className={`toggle ${checked ? 'on' : ''}`} onClick={() => onChange(!checked)}><span /></button>
  );
}

export function Empty({ children }) { return <div className="empty">{children}</div>; }
export function Loading() { return <div className="empty"><div className="spinner" /></div>; }
export function ErrorBox({ error, onRetry }) {
  return <div className="error-box">{error} {onRetry && <button className="btn sm" onClick={onRetry}>Retry</button>}</div>;
}

export function PageHead({ title, sub, children }) {
  return (
    <div className="page-head">
      <div><h1>{title}</h1>{sub && <p className="muted">{sub}</p>}</div>
      <div className="actions">{children}</div>
    </div>
  );
}

/** Debounced value for search boxes. */
export function useDebounced(value, ms = 350) {
  const [v, setV] = useState(value);
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t); }, [value, ms]);
  return v;
}

/** Image uploader that posts to /uploads/:folder and returns URLs. */
export function ImageUpload({ folder, multiple, onUploaded, children = 'Upload image' }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <label className={`btn ${busy ? 'disabled' : ''}`}>
      {busy ? 'Uploading…' : children}
      <input type="file" accept="image/jpeg,image/png,image/webp" multiple={multiple} hidden disabled={busy}
        onChange={async (e) => {
          const files = [...e.target.files];
          e.target.value = '';
          if (!files.length) return;
          setBusy(true);
          // One file per request keeps each request under hosting body-size limits (e.g. 4.5 MB on Vercel)
          const urls = [];
          try {
            for (const f of files) {
              if (f.size > 4 * 1024 * 1024) { toast(`${f.name} is larger than 4 MB — please compress it`, 'err'); continue; }
              const fd = new FormData();
              fd.append('files', f);
              urls.push(...(await api.upload(`/uploads/${folder}`, fd)).urls);
            }
          } catch (err) { toast(err.message, 'err'); } finally { setBusy(false); if (urls.length) onUploaded(urls); }
        }} />
    </label>
  );
}
