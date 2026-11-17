// Creates the database (if allowed) and applies schema.sql
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const config = require('../src/config');

(async () => {
  if (config.db.url) {
    // Hosted database (TiDB, Aiven, PlanetScale…): the database already exists
    const db = require('../src/config/db');
    await require('../src/services/setup').applySchema();
    console.log('Schema applied');
    return db.pool.end();
  }
  const { database, url, ssl, ...conn } = config.db;
  const c = await mysql.createConnection({ ...conn, multipleStatements: true });
  try {
    await c.query(`CREATE DATABASE IF NOT EXISTS \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
  } catch (e) {
    console.warn('Could not create database (it may already exist):', e.message);
  }
  await c.query(`USE \`${database}\``);
  await c.query(fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8'));
  console.log('Schema applied to', database);
  await c.end();
})().catch((e) => { console.error(e); process.exit(1); });
