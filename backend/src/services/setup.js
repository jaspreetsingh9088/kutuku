const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const db = require('../config/db');

const SCHEMA = path.join(__dirname, '..', '..', 'database', 'schema.sql');

/** Apply schema.sql (idempotent: every table uses CREATE TABLE IF NOT EXISTS). */
async function applySchema() {
  const sql = fs.readFileSync(SCHEMA, 'utf8')
    .split('\n').filter((l) => !l.trim().startsWith('--')).join('\n');
  const statements = sql.split(/;\s*(?:\n|$)/).map((s) => s.trim()).filter(Boolean);
  const conn = await db.pool.getConnection();
  try {
    for (const st of statements) await conn.query(st);
  } finally {
    conn.release();
  }
}

async function seedAdmin() {
  const email = (process.env.ADMIN_EMAIL || 'admin@example.com').toLowerCase();
  const pass = process.env.ADMIN_PASSWORD || 'Admin@123';
  if (await db.one('SELECT id FROM admins WHERE email = ?', [email])) return false;
  await db.query("INSERT INTO admins (name, email, password_hash, role) VALUES ('Administrator', ?, ?, 'superadmin')", [email, await bcrypt.hash(pass, 10)]);
  return { email, pass };
}

/** Sample catalog for previews: categories, 28 products with 2 variants each, a coupon and banners. */
async function seedDemo() {
  const img = (seed) => `https://picsum.photos/seed/${encodeURIComponent(seed)}/600/600`;
  const slug = (s) => s.toLowerCase().replace(/\W+/g, '-');
  const cats = [['Groceries', ['Rice & Grains', 'Pulses', 'Spices']], ['Snacks', ['Namkeen', 'Biscuits']], ['Beverages', ['Tea', 'Coffee']]];
  let sku = 1000;
  for (const [i, [name, subs]] of cats.entries()) {
    await db.query('INSERT IGNORE INTO categories (name, slug, display_order, image_url) VALUES (?, ?, ?, ?)', [name, slug(name), i, img(name)]);
    const parentId = (await db.one('SELECT id FROM categories WHERE slug = ?', [slug(name)])).id;
    for (const sub of subs) {
      await db.query('INSERT IGNORE INTO categories (name, slug, parent_id, image_url) VALUES (?, ?, ?, ?)', [sub, slug(sub), parentId, img(sub)]);
      const subId = (await db.one('SELECT id FROM categories WHERE slug = ?', [slug(sub)])).id;
      for (let k = 1; k <= 4; k++) {
        const pname = `${sub} Item ${k}`;
        sku += 2;
        if (await db.one('SELECT id FROM products WHERE slug = ?', [slug(pname)])) continue;
        const p = await db.query('INSERT INTO products (category_id, name, slug, description, hsn_code, gst_rate, is_featured, specifications) VALUES (?,?,?,?,?,?,?,?)',
          [subId, pname, slug(pname), `Fresh, high-quality ${sub.toLowerCase()} sourced from trusted suppliers.`, '0000', 5, k === 1 ? 1 : 0,
            JSON.stringify([{ key: 'Shelf life', value: '6 months' }, { key: 'Origin', value: 'India' }])]);
        await db.query('INSERT INTO product_images (product_id, url, sort_order) VALUES (?,?,0),(?,?,1)', [p.insertId, img(slug(pname)), p.insertId, img(`${slug(pname)}-2`)]);
        const base = 50 + k * 30;
        await db.query('INSERT INTO product_variants (product_id, sku, label, mrp, price, stock) VALUES (?,?,?,?,?,?),(?,?,?,?,?,?)',
          [p.insertId, `SKU${sku - 1}`, '500 g', base + 10, base, 50, p.insertId, `SKU${sku}`, '1 kg', base * 2 + 15, base * 2 - 5, k === 4 ? 3 : 40]);
      }
    }
  }
  await db.query("INSERT IGNORE INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_value, per_customer_limit) VALUES ('WELCOME10', '10% off your first order', 'percent', 10, 100, 199, 1)");
  if (!(await db.one('SELECT id FROM banners LIMIT 1'))) {
    await db.query("INSERT INTO banners (title, description, image_url, display_order) VALUES ('Mega Sale', 'Up to 30% off on groceries', ?, 0), ('Fresh Arrivals', 'New snacks every week', ?, 1)",
      ['https://picsum.photos/seed/banner1/1200/500', 'https://picsum.photos/seed/banner2/1200/500']);
  }
}

let ready = null;
/**
 * Make sure the database is initialised. Runs once per server instance:
 * creates missing tables, the first admin, and (with SEED_DEMO=1 on an empty catalog) sample data.
 */
function ensureReady() {
  if (!ready) {
    ready = (async () => {
      const t = await db.one("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'settings'");
      if (!t.n) {
        console.log('[setup] creating tables');
        await applySchema();
      }
      const created = await seedAdmin();
      if (created) console.log(`[setup] admin created: ${created.email}`);
      if (process.env.SEED_DEMO === '1' && !(await db.one('SELECT id FROM products LIMIT 1'))) {
        console.log('[setup] adding demo catalog');
        await seedDemo();
      }
    })().catch((e) => { ready = null; throw e; });
  }
  return ready;
}

module.exports = { applySchema, seedAdmin, seedDemo, ensureReady };
