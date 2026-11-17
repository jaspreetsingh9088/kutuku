const mysql = require('mysql2/promise');
const config = require('./index');

const { url, ssl, ...fields } = config.db;
const base = url ? { uri: url.replace(/[?&]ssl-mode=[^&]*/i, '') } : fields;

const pool = mysql.createPool({
  ...base,
  ...(ssl ? { ssl: { minVersion: 'TLSv1.2', rejectUnauthorized: true } } : {}),
  waitForConnections: true,
  // Serverless functions run many small instances; keep each pool small
  connectionLimit: config.onVercel ? 3 : 15,
  maxIdle: config.onVercel ? 1 : 15,
  enableKeepAlive: true,
  decimalNumbers: true,
  dateStrings: false,
  timezone: 'Z',
  charset: 'utf8mb4',
});

/** Run a query and return rows. */
async function query(sql, params = []) {
  const [rows] = await pool.query(sql, params);
  return rows;
}

/** Run a query and return the first row or null. */
async function one(sql, params = []) {
  const rows = await query(sql, params);
  return rows[0] || null;
}

/** Run fn inside a transaction; fn receives a connection with the same query helpers. */
async function tx(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const helpers = {
      query: async (sql, params = []) => (await conn.query(sql, params))[0],
      one: async (sql, params = []) => (await conn.query(sql, params))[0][0] || null,
    };
    const result = await fn(helpers);
    await conn.commit();
    return result;
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

module.exports = { pool, query, one, tx };
