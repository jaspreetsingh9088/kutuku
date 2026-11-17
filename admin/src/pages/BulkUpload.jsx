import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox, PageHead, useToast } from '../components/ui';

export default function BulkUpload() {
  const toast = useToast();
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const send = async (dry) => {
    if (!file) return;
    setBusy(true); setError('');
    const fd = new FormData();
    fd.append('file', file);
    try {
      const r = await api.upload(`/products-import${dry ? '?dry_run=1' : ''}`, fd);
      if (dry) { setPreview(r); setResult(null); } else { setResult(r); setPreview(null); toast(`Import done: ${r.summary.created} created, ${r.summary.updated} updated`); }
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  const downloadErrors = (errors) => {
    const csv = ['row,sku,error', ...errors.map((e) => `${e.row},"${String(e.sku).replace(/"/g, '""')}","${e.message.replace(/"/g, '""')}"`)].join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = 'import-errors.csv'; a.click();
  };

  const report = preview || result;
  return (
    <>
      <PageHead title="Bulk product upload" sub="Create or update many products at once from Excel or CSV">
        <Link className="btn" to="/products">Back to products</Link>
      </PageHead>

      <div className="grid g2">
        <div className="card">
          <h2>1. Get the template</h2>
          <p className="muted">One row per variant. Rows sharing a product name become one product with several variants. Existing SKUs are updated, so you can also use this to change prices and stock in bulk.</p>
          <div className="actions">
            <button className="btn" onClick={() => api.download('/products-import/template', 'template.xlsx').catch((e) => toast(e.message, 'err'))}>Download template</button>
            <button className="btn" onClick={() => api.download('/products-export', 'products.xlsx').catch((e) => toast(e.message, 'err'))}>Export current catalog</button>
          </div>
          <p className="hint" style={{ marginTop: 12 }}>Tip: to update prices/stock, export the current catalog, edit the mrp, price and stock columns, and upload it back.</p>
        </div>
        <div className="card">
          <h2>2. Upload your file</h2>
          <input type="file" accept=".xlsx,.xls,.csv" onChange={(e) => { setFile(e.target.files[0] || null); setPreview(null); setResult(null); }} />
          <div className="actions" style={{ marginTop: 12 }}>
            <button className="btn" disabled={!file || busy} onClick={() => send(true)}>{busy ? 'Checking…' : 'Validate'}</button>
            <button className="btn primary" disabled={!file || busy || !preview || preview.summary.valid_rows === 0} onClick={() => send(false)}>
              {busy ? 'Importing…' : preview ? `Import ${preview.summary.valid_rows} valid rows` : 'Import'}
            </button>
          </div>
          <p className="hint" style={{ marginTop: 8 }}>Validate first to see what will change. Rows with errors are skipped; valid rows are imported.</p>
        </div>
      </div>

      {error && <div style={{ marginTop: 16 }}><ErrorBox error={error} /></div>}

      {report && (
        <div className="card" style={{ marginTop: 16 }}>
          <h2>{preview ? 'Validation result' : 'Import complete'}</h2>
          <div className="grid g4">
            <div className="stat"><div className="k">Rows in file</div><div className="v">{report.summary.total_rows}</div></div>
            <div className="stat"><div className="k">{preview ? 'New variants' : 'Created'}</div><div className="v">{preview ? report.summary.to_create : report.summary.created}</div></div>
            <div className="stat"><div className="k">{preview ? 'Variants to update' : 'Updated'}</div><div className="v">{preview ? report.summary.to_update : report.summary.updated}</div></div>
            <div className={`stat ${report.errors.length ? 'warn' : ''}`}><div className="k">Rows with errors</div><div className="v">{report.errors.length}</div></div>
          </div>
          {report.errors.length > 0 && (
            <>
              <div className="inline" style={{ justifyContent: 'space-between', margin: '18px 0 8px' }}>
                <h3>Error report</h3><button className="btn sm" onClick={() => downloadErrors(report.errors)}>Download errors (CSV)</button>
              </div>
              <div className="table-wrap" style={{ maxHeight: 360, overflowY: 'auto' }}>
                <table><thead><tr><th>Row</th><th>SKU</th><th>Problem</th></tr></thead>
                  <tbody>{report.errors.map((e, i) => <tr key={i}><td>{e.row}</td><td>{e.sku || '—'}</td><td className="wrap">{e.message}</td></tr>)}</tbody></table>
              </div>
            </>
          )}
        </div>
      )}
    </>
  );
}
