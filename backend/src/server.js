const app = require('./app');
const { ensureReady } = require('./services/setup');
const config = require('./config');
const { pool } = require('./config/db');
const { startReservationSweeper } = require('./services/orders');

const server = app.listen(config.port, () => {
  console.log(`API listening on :${config.port} (${config.env})`);
  startReservationSweeper();
  ensureReady().catch((e) => console.error('[setup] database not ready:', e.message));
});

const shutdown = () => {
  server.close(() => pool.end().finally(() => process.exit(0)));
  setTimeout(() => process.exit(1), 10000).unref();
};
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
