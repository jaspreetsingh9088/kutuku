const router = require('express').Router();
const { z } = require('zod');
const db = require('../../config/db');
const { ah, validate, paging, pageMeta, notFound, badRequest } = require('../../utils/http');
const { customerAuth } = require('../../middleware/auth');
const { hydrate } = require('../../services/catalog');

router.use(customerAuth);

// ---------- Notifications inbox ----------
router.get('/notifications', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 30, 100);
  const [{ total }] = await db.query('SELECT COUNT(*) AS total FROM notifications WHERE customer_id = ?', [req.customer.id]);
  const rows = await db.query('SELECT id, type, title, body, image_url, data, is_read, created_at FROM notifications WHERE customer_id = ? ORDER BY id DESC LIMIT ? OFFSET ?',
    [req.customer.id, limit, offset]);
  const unread = await db.one('SELECT COUNT(*) AS n FROM notifications WHERE customer_id = ? AND is_read = 0', [req.customer.id]);
  res.json({
    data: rows.map((n) => ({ ...n, is_read: !!n.is_read, data: typeof n.data === 'string' ? JSON.parse(n.data || '{}') : (n.data || {}) })),
    meta: pageMeta(page, limit, total), unread: unread.n,
  });
}));

router.post('/notifications/read-all', ah(async (req, res) => {
  await db.query('UPDATE notifications SET is_read = 1 WHERE customer_id = ? AND is_read = 0', [req.customer.id]);
  res.json({ ok: true });
}));

router.post('/notifications/:id/read', ah(async (req, res) => {
  await db.query('UPDATE notifications SET is_read = 1 WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  res.json({ ok: true });
}));

// ---------- Messages (chat with a store, or with support when store_id = 0) ----------
const convoSelect = `SELECT cv.id, cv.store_id, cv.last_message, cv.last_message_at, cv.customer_unread AS unread,
    COALESCE(s.name, 'Customer Support') AS name, s.logo_url AS avatar_url, COALESCE(s.is_verified, 1) AS is_verified
  FROM conversations cv LEFT JOIN stores s ON s.id = cv.store_id`;

router.get('/conversations', ah(async (req, res) => {
  const rows = await db.query(`${convoSelect} WHERE cv.customer_id = ? ORDER BY COALESCE(cv.last_message_at, cv.created_at) DESC`, [req.customer.id]);
  res.json(rows.map((r) => ({ ...r, is_verified: !!r.is_verified })));
}));

/** Open (or create) the conversation with a store. store_id 0 = customer support. */
router.post('/conversations', validate(z.object({ store_id: z.number().int().min(0).default(0) })), ah(async (req, res) => {
  const storeId = req.body.store_id;
  if (storeId && !(await db.one('SELECT id FROM stores WHERE id = ? AND is_active = 1', [storeId]))) throw notFound('Store not found');
  await db.query('INSERT IGNORE INTO conversations (customer_id, store_id) VALUES (?, ?)', [req.customer.id, storeId]);
  const c = await db.one(`${convoSelect} WHERE cv.customer_id = ? AND cv.store_id = ?`, [req.customer.id, storeId]);
  res.json({ ...c, is_verified: !!c.is_verified });
}));

async function ownConversation(id, customerId) {
  const c = await db.one(`${convoSelect} WHERE cv.id = ? AND cv.customer_id = ?`, [id, customerId]);
  if (!c) throw notFound('Conversation not found');
  return c;
}

/** Messages, oldest first. Pass ?after_id= to poll for new ones. */
router.get('/conversations/:id/messages', ah(async (req, res) => {
  const c = await ownConversation(req.params.id, req.customer.id);
  const after = Number(req.query.after_id) || 0;
  const rows = await db.query(
    `SELECT id, sender, body, product_id, order_id, created_at FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id DESC LIMIT 100`,
    [c.id, after]
  );
  rows.reverse();
  // Attach product cards (the "Bix Bag Limited..." bubble in the design)
  const pids = [...new Set(rows.map((m) => m.product_id).filter(Boolean))];
  const products = pids.length ? await hydrate(await db.query('SELECT id, name, store_id, rating_avg FROM products WHERE id IN (?)', [pids])) : [];
  const byId = Object.fromEntries(products.map((p) => [p.id, { id: p.id, name: p.name, image: p.image, price: p.price, color: p.variants[0] && p.variants[0].color }]));
  await db.query('UPDATE conversations SET customer_unread = 0 WHERE id = ?', [c.id]);
  res.json({ conversation: { ...c, is_verified: !!c.is_verified }, data: rows.map((m) => ({ ...m, product: m.product_id ? byId[m.product_id] || null : null })) });
}));

router.post('/conversations/:id/messages', validate(z.object({
  body: z.string().trim().max(2000).optional().default(''),
  product_id: z.number().int().positive().optional(),
  order_id: z.number().int().positive().optional(),
})), ah(async (req, res) => {
  const c = await ownConversation(req.params.id, req.customer.id);
  const { body, product_id, order_id } = req.body;
  if (!body && !product_id && !order_id) throw badRequest('Type a message');
  if (order_id && !(await db.one('SELECT id FROM orders WHERE id = ? AND customer_id = ?', [order_id, req.customer.id]))) throw notFound('Order not found');
  const r = await db.query('INSERT INTO messages (conversation_id, sender, body, product_id, order_id) VALUES (?,?,?,?,?)',
    [c.id, 'customer', body || null, product_id || null, order_id || null]);
  const preview = body || (product_id ? 'Sent a product' : 'Sent an order');
  await db.query('UPDATE conversations SET last_message = ?, last_message_at = NOW(), admin_unread = admin_unread + 1 WHERE id = ?', [preview.slice(0, 500), c.id]);
  res.status(201).json(await db.one('SELECT id, sender, body, product_id, order_id, created_at FROM messages WHERE id = ?', [r.insertId]));
}));

module.exports = router;
