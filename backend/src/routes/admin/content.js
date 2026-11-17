const router = require('express').Router();
const { z } = require('zod');
const db = require('../../config/db');
const { ah, validate, paging, pageMeta, notFound, badRequest, slugify } = require('../../utils/http');
const { adminAuth } = require('../../middleware/auth');
const { bust } = require('../../utils/cache');
const { notifyCustomer } = require('../../utils/notify');
const { refreshRating } = require('../app/catalog');

router.use(adminAuth());

const bool = z.union([z.boolean(), z.number(), z.string()]).transform((v) => v === true || v === 1 || v === '1' || v === 'true');
const url = z.string().url().nullable().optional().or(z.literal(''));

// ================= Stores =================
const storeSchema = z.object({
  name: z.string().trim().min(2).max(150),
  logo_url: url,
  description: z.string().max(5000).optional().nullable(),
  city: z.string().trim().max(100).optional().nullable(),
  is_verified: bool.default(false),
  is_active: bool.default(true),
  display_order: z.coerce.number().int().default(0),
});

router.get('/stores', ah(async (_req, res) => {
  res.json(await db.query(`SELECT s.*,
      (SELECT COUNT(*) FROM products p WHERE p.store_id = s.id) AS product_count,
      (SELECT COUNT(*) FROM store_followers f WHERE f.store_id = s.id) AS follower_count
    FROM stores s ORDER BY s.display_order, s.name`));
}));

async function uniqueStoreSlug(name, exceptId = 0) {
  let slug = slugify(name) || 'store';
  let i = 1;
  // eslint-disable-next-line no-await-in-loop
  while (await db.one('SELECT id FROM stores WHERE slug = ? AND id <> ?', [slug, exceptId])) slug = `${slugify(name)}-${++i}`;
  return slug;
}

router.post('/stores', validate(storeSchema), ah(async (req, res) => {
  const b = req.body;
  const r = await db.query('INSERT INTO stores (name, slug, logo_url, description, city, is_verified, is_active, display_order) VALUES (?,?,?,?,?,?,?,?)',
    [b.name, await uniqueStoreSlug(b.name), b.logo_url || null, b.description || null, b.city || null, b.is_verified ? 1 : 0, b.is_active ? 1 : 0, b.display_order]);
  bust('home', 'filters');
  res.status(201).json({ id: r.insertId });
}));

router.put('/stores/:id', validate(storeSchema), ah(async (req, res) => {
  const b = req.body;
  if (!(await db.one('SELECT id FROM stores WHERE id = ?', [req.params.id]))) throw notFound();
  await db.query('UPDATE stores SET name=?, slug=?, logo_url=?, description=?, city=?, is_verified=?, is_active=?, display_order=? WHERE id=?',
    [b.name, await uniqueStoreSlug(b.name, Number(req.params.id)), b.logo_url || null, b.description || null, b.city || null, b.is_verified ? 1 : 0, b.is_active ? 1 : 0, b.display_order, req.params.id]);
  bust('home', 'filters');
  res.json({ ok: true });
}));

router.delete('/stores/:id', ah(async (req, res) => {
  const n = await db.one('SELECT COUNT(*) AS n FROM products WHERE store_id = ?', [req.params.id]);
  if (n.n > 0) throw badRequest(`Move the ${n.n} product(s) of this store to another store first, or deactivate it`);
  await db.query('DELETE FROM store_followers WHERE store_id = ?', [req.params.id]);
  await db.query('DELETE FROM stores WHERE id = ?', [req.params.id]);
  res.json({ ok: true });
}));

// ================= Reviews =================
router.get('/reviews', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 25, 100);
  const where = ['1=1']; const params = [];
  if (req.query.status === 'approved') where.push('r.is_approved = 1');
  if (req.query.status === 'hidden') where.push('r.is_approved = 0');
  if (req.query.rating) { where.push('r.rating = ?'); params.push(Number(req.query.rating)); }
  if (req.query.q) { where.push('(p.name LIKE ? OR r.comment LIKE ? OR c.name LIKE ?)'); params.push(`%${req.query.q}%`, `%${req.query.q}%`, `%${req.query.q}%`); }
  const w = where.join(' AND ');
  const base = 'FROM reviews r JOIN products p ON p.id = r.product_id JOIN customers c ON c.id = r.customer_id';
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total ${base} WHERE ${w}`, params);
  const rows = await db.query(`SELECT r.*, p.name AS product_name, c.name AS customer_name, c.mobile, c.email ${base} WHERE ${w} ORDER BY r.id DESC LIMIT ? OFFSET ?`, [...params, limit, offset]);
  res.json({ data: rows, meta: pageMeta(page, limit, total) });
}));

router.patch('/reviews/:id', validate(z.object({ is_approved: bool })), ah(async (req, res) => {
  const r = await db.one('SELECT product_id FROM reviews WHERE id = ?', [req.params.id]);
  if (!r) throw notFound();
  await db.query('UPDATE reviews SET is_approved = ? WHERE id = ?', [req.body.is_approved ? 1 : 0, req.params.id]);
  await refreshRating(r.product_id);
  res.json({ ok: true });
}));

router.delete('/reviews/:id', ah(async (req, res) => {
  const r = await db.one('SELECT product_id FROM reviews WHERE id = ?', [req.params.id]);
  if (!r) throw notFound();
  await db.query('DELETE FROM reviews WHERE id = ?', [req.params.id]);
  await refreshRating(r.product_id);
  res.json({ ok: true });
}));

// ================= Messages (support inbox) =================
router.get('/conversations', ah(async (req, res) => {
  const where = req.query.unread === '1' ? 'WHERE cv.admin_unread > 0' : '';
  res.json(await db.query(
    `SELECT cv.*, COALESCE(c.name, c.mobile, c.email) AS customer_name, c.mobile, c.email, c.avatar_url,
       COALESCE(s.name, 'Customer Support') AS store_name
     FROM conversations cv JOIN customers c ON c.id = cv.customer_id LEFT JOIN stores s ON s.id = cv.store_id
     ${where} ORDER BY COALESCE(cv.last_message_at, cv.created_at) DESC LIMIT 200`
  ));
}));

router.get('/conversations/unread-count', ah(async (_req, res) => {
  res.json(await db.one('SELECT COALESCE(SUM(admin_unread),0) AS unread FROM conversations'));
}));

router.get('/conversations/:id', ah(async (req, res) => {
  const cv = await db.one(
    `SELECT cv.*, COALESCE(c.name, c.mobile, c.email) AS customer_name, c.id AS customer_id, c.mobile, c.email, COALESCE(s.name, 'Customer Support') AS store_name
     FROM conversations cv JOIN customers c ON c.id = cv.customer_id LEFT JOIN stores s ON s.id = cv.store_id WHERE cv.id = ?`, [req.params.id]
  );
  if (!cv) throw notFound();
  const after = Number(req.query.after_id) || 0;
  const msgs = await db.query(
    `SELECT m.*, p.name AS product_name, o.order_number FROM messages m
     LEFT JOIN products p ON p.id = m.product_id LEFT JOIN orders o ON o.id = m.order_id
     WHERE m.conversation_id = ? AND m.id > ? ORDER BY m.id LIMIT 500`, [cv.id, after]
  );
  await db.query('UPDATE conversations SET admin_unread = 0 WHERE id = ?', [cv.id]);
  res.json({ ...cv, messages: msgs });
}));

router.post('/conversations/:id/messages', validate(z.object({ body: z.string().trim().min(1).max(2000) })), ah(async (req, res) => {
  const cv = await db.one('SELECT * FROM conversations WHERE id = ?', [req.params.id]);
  if (!cv) throw notFound();
  const r = await db.query("INSERT INTO messages (conversation_id, sender, body) VALUES (?, 'store', ?)", [cv.id, req.body.body]);
  await db.query('UPDATE conversations SET last_message = ?, last_message_at = NOW(), customer_unread = customer_unread + 1 WHERE id = ?', [req.body.body.slice(0, 500), cv.id]);
  const store = cv.store_id ? await db.one('SELECT name FROM stores WHERE id = ?', [cv.store_id]) : null;
  notifyCustomer(cv.customer_id, `${store ? store.name : 'Support'} sent you a message`, req.body.body.slice(0, 140), { conversation_id: cv.id }, 'message');
  res.status(201).json(await db.one('SELECT * FROM messages WHERE id = ?', [r.insertId]));
}));

// ================= Onboarding slides =================
const slideSchema = z.object({
  title: z.string().trim().min(2).max(150), subtitle: z.string().max(300).optional().nullable(),
  image_url: z.string().url(), display_order: z.coerce.number().int().default(0), is_active: bool.default(true),
});
router.get('/onboarding', ah(async (_req, res) => res.json(await db.query('SELECT * FROM onboarding_slides ORDER BY display_order, id'))));
router.post('/onboarding', validate(slideSchema), ah(async (req, res) => {
  const b = req.body;
  const r = await db.query('INSERT INTO onboarding_slides (title, subtitle, image_url, display_order, is_active) VALUES (?,?,?,?,?)', [b.title, b.subtitle || null, b.image_url, b.display_order, b.is_active ? 1 : 0]);
  bust('onboarding');
  res.status(201).json({ id: r.insertId });
}));
router.put('/onboarding/:id', validate(slideSchema), ah(async (req, res) => {
  const b = req.body;
  await db.query('UPDATE onboarding_slides SET title=?, subtitle=?, image_url=?, display_order=?, is_active=? WHERE id=?', [b.title, b.subtitle || null, b.image_url, b.display_order, b.is_active ? 1 : 0, req.params.id]);
  bust('onboarding');
  res.json({ ok: true });
}));
router.delete('/onboarding/:id', ah(async (req, res) => { await db.query('DELETE FROM onboarding_slides WHERE id = ?', [req.params.id]); bust('onboarding'); res.json({ ok: true }); }));

// ================= FAQs (Help and Support) =================
const faqSchema = z.object({ question: z.string().trim().min(3).max(255), answer: z.string().trim().min(2).max(5000), display_order: z.coerce.number().int().default(0), is_active: bool.default(true) });
router.get('/faqs', ah(async (_req, res) => res.json(await db.query('SELECT * FROM faqs ORDER BY display_order, id'))));
router.post('/faqs', validate(faqSchema), ah(async (req, res) => {
  const b = req.body;
  const r = await db.query('INSERT INTO faqs (question, answer, display_order, is_active) VALUES (?,?,?,?)', [b.question, b.answer, b.display_order, b.is_active ? 1 : 0]);
  bust('faqs');
  res.status(201).json({ id: r.insertId });
}));
router.put('/faqs/:id', validate(faqSchema), ah(async (req, res) => {
  const b = req.body;
  await db.query('UPDATE faqs SET question=?, answer=?, display_order=?, is_active=? WHERE id=?', [b.question, b.answer, b.display_order, b.is_active ? 1 : 0, req.params.id]);
  bust('faqs');
  res.json({ ok: true });
}));
router.delete('/faqs/:id', ah(async (req, res) => { await db.query('DELETE FROM faqs WHERE id = ?', [req.params.id]); bust('faqs'); res.json({ ok: true }); }));

// ================= Pages (Legal and Policies) =================
router.get('/pages', ah(async (_req, res) => res.json(await db.query('SELECT slug, title, updated_at FROM pages ORDER BY slug'))));
router.get('/pages/:slug', ah(async (req, res) => {
  const p = await db.one('SELECT * FROM pages WHERE slug = ?', [req.params.slug]);
  if (!p) throw notFound();
  res.json(p);
}));
router.put('/pages/:slug', validate(z.object({ title: z.string().trim().min(2).max(150), content: z.string().max(200000) })), ah(async (req, res) => {
  await db.query('INSERT INTO pages (slug, title, content) VALUES (?,?,?) ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content)',
    [req.params.slug, req.body.title, req.body.content]);
  res.json({ ok: true });
}));

// ================= Popular searches =================
router.get('/searches', ah(async (_req, res) => {
  res.json(await db.query('SELECT term, total_count, day_count, day, last_searched_at FROM search_terms ORDER BY total_count DESC LIMIT 200'));
}));

module.exports = router;
