const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '') + '/api/v1/admin';
const TOKEN_KEY = 'admin_token';

export const auth = {
  get token() { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } },
  set(token) { try { localStorage.setItem(TOKEN_KEY, token); } catch { /* ignore */ } },
  clear() { try { localStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ } },
};

export class ApiError extends Error {
  constructor(message, status, details) { super(message); this.status = status; this.details = details; }
}

async function request(method, path, body, { raw } = {}) {
  const headers = {};
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`;
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch(BASE + path, { method, headers, body: payload });
  if (res.status === 401 && !path.startsWith('/auth/login')) {
    auth.clear();
    window.location.assign('/login');
    throw new ApiError('Session expired', 401);
  }
  if (raw) {
    if (!res.ok) throw new ApiError((await res.json().catch(() => ({}))).message || 'Download failed', res.status);
    return res;
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const detail = data.details && data.details.length ? `: ${data.details.map((d) => `${d.path ? d.path + ' – ' : ''}${d.message}`).join('; ')}` : '';
    throw new ApiError((data.message || 'Request failed') + detail, res.status, data.details);
  }
  return data;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b ?? {}),
  put: (p, b) => request('PUT', p, b),
  patch: (p, b) => request('PATCH', p, b),
  del: (p) => request('DELETE', p),
  upload: (p, formData) => request('POST', p, formData),
  /** Download a file (xlsx/csv/pdf) with auth and trigger a browser save. */
  async download(p, fallbackName) {
    const res = await request('GET', p, undefined, { raw: true });
    const cd = res.headers.get('Content-Disposition') || '';
    const name = (cd.match(/filename="?([^"]+)"?/) || [])[1] || fallbackName;
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  },
  /** Open a PDF in a new tab with auth. */
  async openPdf(p) {
    const w = window.open('', '_blank');
    const res = await request('GET', p, undefined, { raw: true });
    const url = URL.createObjectURL(await res.blob());
    if (w) w.location.href = url; else window.location.href = url;
  },
};

export const qs = (o) => {
  const s = new URLSearchParams(Object.entries(o).filter(([, v]) => v !== '' && v !== undefined && v !== null)).toString();
  return s ? `?${s}` : '';
};
