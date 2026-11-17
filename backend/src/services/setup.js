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

/** Default app content (admin can edit it): onboarding slides, help FAQs and legal pages. */
async function seedContent() {
  if (!(await db.one('SELECT id FROM onboarding_slides LIMIT 1'))) {
    const slides = [
      ['Various Collections Of The Latest Products', 'Discover new arrivals every week from stores you love, all in one place.', 'https://images.unsplash.com/photo-1491637639811-60e2756cc1c7?w=800'],
      ['Complete Collection Of Colors And Sizes', 'Pick the exact colour and size you want, with live stock for every option.', 'https://images.unsplash.com/photo-1489987707025-afc232f7ea0f?w=800'],
      ['Find The Most Suitable Outfit For You', 'Save favourites, track orders and get support whenever you need it.', 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=800'],
    ];
    for (const [i, [t, s, img]] of slides.entries()) await db.query('INSERT INTO onboarding_slides (title, subtitle, image_url, display_order) VALUES (?,?,?,?)', [t, s, img, i]);
  }
  if (!(await db.one('SELECT id FROM faqs LIMIT 1'))) {
    const faqs = [
      ['How do I track my order?', 'Open My Order and tap Tracking on any order to see each step, from packing to delivery.'],
      ['How can I cancel an order?', 'You can cancel from the order details screen until the order is packed. Paid amounts are refunded to the original payment method within 5–7 working days.'],
      ['Which payment methods are accepted?', 'UPI, debit and credit cards, net banking and popular wallets, processed securely by Razorpay.'],
      ['How do coupons work?', 'Enter a promo code in My Cart. Each coupon shows its minimum order value and validity.'],
      ['How do I get a GST invoice?', 'After payment, open the order and tap Download GST Invoice. Add your GSTIN in Edit Profile to have it printed on invoices.'],
    ];
    for (const [i, [q, a]] of faqs.entries()) await db.query('INSERT INTO faqs (question, answer, display_order) VALUES (?,?,?)', [q, a, i]);
  }
  if (!(await db.one("SELECT slug FROM pages WHERE slug = 'terms'"))) {
    await db.query("INSERT INTO pages (slug, title, content) VALUES ('terms', 'Legal and Policies', ?)", [
      '## Terms\nBy using this app you agree to these terms. Prices include GST unless stated otherwise. Orders are confirmed once payment is received.\n\n'
      + '## Returns and Refunds\nCancelled or returned orders are refunded to the original payment method within 5–7 working days.\n\n'
      + '## Privacy\nWe use your details only to process orders, provide support and send updates you have allowed. See the full privacy policy on our website.\n\n'
      + '## Changes to the Service and/or Terms\nWe may update these terms from time to time. Continued use of the app means you accept the updated terms.',
    ]);
  }
}

/** Demo stores, colours and reviews for previews (only when SEED_DEMO=1 and no stores exist yet). */
async function seedDemoExtras() {
  if (await db.one('SELECT id FROM stores LIMIT 1')) return;
  const stores = [['Upbox Bag', 'upbox-bag', 'Mumbai', 1], ['Gazuna Resika', 'gazuna-resika', 'Delhi', 1], ['Lisa Robber', 'lisa-robber', 'Bengaluru', 0]];
  const ids = [];
  for (const [i, [name, slug, city, verified]] of stores.entries()) {
    const r = await db.query('INSERT INTO stores (name, slug, city, is_verified, display_order, logo_url, description) VALUES (?,?,?,?,?,?,?)',
      [name, slug, city, verified, i, `https://picsum.photos/seed/${slug}/200/200`, `${name} brings you quality products at great prices.`]);
    ids.push(r.insertId);
  }
  const products = await db.query('SELECT id FROM products ORDER BY id');
  for (const [i, p] of products.entries()) await db.query('UPDATE products SET store_id = ? WHERE id = ?', [ids[i % ids.length], p.id]);
  const palette = [['Brown', '#8B5A3C'], ['Black', '#111111'], ['Teal', '#2BA6B5'], ['Green', '#22C55E']];
  const variants = await db.query('SELECT id FROM product_variants ORDER BY id');
  for (const [i, v] of variants.entries()) await db.query('UPDATE product_variants SET color = ?, color_hex = ? WHERE id = ?', [...palette[i % palette.length], v.id]);
  await db.query('UPDATE products SET rating_avg = 4.8, rating_count = 320 WHERE id % 2 = 0');
  await db.query('UPDATE products SET rating_avg = 4.6, rating_count = 128 WHERE id % 2 = 1');
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
      await require('./migrations').migrate();
      await seedContent();
      const created = await seedAdmin();
      if (created) console.log(`[setup] admin created: ${created.email}`);
      if (process.env.SEED_DEMO === '1' && !(await db.one('SELECT id FROM products LIMIT 1'))) {
        console.log('[setup] adding demo catalog');
        await seedDemo();
      }
      if (process.env.SEED_DEMO === '1') await seedDemoExtras();
    })().catch((e) => { ready = null; throw e; });
  }
  return ready;
}

module.exports = { applySchema, seedAdmin, seedDemo, seedDemoExtras, seedContent, ensureReady };
