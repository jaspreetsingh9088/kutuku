const path = require('path');
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const compression = require('compression');
const morgan = require('morgan');
const config = require('./config');
const { UPLOAD_ROOT } = require('./middleware/upload');

const app = express();
app.set('trust proxy', 1);
app.disable('x-powered-by');

app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' },
  contentSecurityPolicy: {
    useDefaults: true,
    // Product/banner images may come from a CDN or external URLs
    directives: { 'img-src': ["'self'", 'data:', 'blob:', 'https:'], 'frame-src': ["'self'", 'blob:'] },
  },
}));
app.use(cors({ origin: config.corsOrigins.includes('*') ? true : config.corsOrigins, credentials: false }));
app.use(compression());
if (config.env !== 'test') app.use(morgan(config.env === 'production' ? 'combined' : 'dev'));

// Create tables / first admin on the first API request (makes fresh deployments work without a manual migration)
const { ensureReady } = require('./services/setup');
app.use('/api', (_req, _res, next) => ensureReady().then(() => next(), next));

// Scheduled job (Vercel Cron or any external cron): release stock held by abandoned checkouts
app.get('/api/v1/cron/sweep', async (req, res, next) => {
  try {
    if (config.cronSecret && req.headers.authorization !== `Bearer ${config.cronSecret}`) return res.status(401).json({ message: 'Unauthorized' });
    res.json({ released: await require('./services/orders').sweepStaleReservations() });
  } catch (e) { next(e); }
});

// Webhooks need the raw body, so mount before express.json()
app.use('/api/v1/webhooks', require('./routes/webhooks'));
app.use(express.json({ limit: '1mb' }));

app.use('/uploads', express.static(UPLOAD_ROOT, { maxAge: '30d', immutable: true }));

// Public privacy policy page (required for the Play Store listing)
app.get(['/privacy', '/privacy-policy'], (_req, res) => res.sendFile(path.join(__dirname, '..', 'static', 'privacy.html')));

app.get('/health', (_req, res) => res.json({ ok: true, time: new Date().toISOString() }));

// Customer app API
app.use('/api/v1/auth', require('./routes/app/auth'));
app.use('/api/v1/profile', require('./routes/app/profile'));
app.use('/api/v1', require('./routes/app/catalog'));
app.use('/api/v1', require('./routes/app/cart'));
app.use('/api/v1/orders', require('./routes/app/orders'));
app.use('/api/v1/inbox', require('./routes/app/inbox'));

// Admin panel API
app.use('/api/v1/admin/auth', require('./routes/admin/auth'));
app.use('/api/v1/admin', require('./routes/admin/catalog'));
app.use('/api/v1/admin', require('./routes/admin/operations'));
app.use('/api/v1/admin', require('./routes/admin/content'));

// Serve the built admin panel (optional): copy admin/dist to backend/public
app.use(express.static(path.join(__dirname, '..', 'public')));
app.get(/^\/(?!api|uploads|privacy).*/, (req, res, next) => {
  const index = path.join(__dirname, '..', 'public', 'index.html');
  require('fs').existsSync(index) ? res.sendFile(index) : next();
});

app.use('/api', (_req, res) => res.status(404).json({ message: 'Route not found' }));

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, _next) => {
  if (err.name === 'MulterError') return res.status(400).json({ message: err.message });
  if (err.code === 'ER_DUP_ENTRY') return res.status(409).json({ message: 'A record with these details already exists' });
  const status = err.status || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ message: status >= 500 ? 'Something went wrong. Please try again.' : err.message, details: err.details });
});

module.exports = app;
