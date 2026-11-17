const router = require('express').Router();
const { z } = require('zod');
const bcrypt = require('bcryptjs');
const db = require('../../config/db');
const { ah, validate, notFound, badRequest } = require('../../utils/http');
const { customerAuth } = require('../../middleware/auth');
const { imageUpload, storeImages } = require('../../middleware/upload');

const PROFILE_COLS = `id, mobile, name, email, gstin, avatar_url, created_at, password_hash IS NOT NULL AS has_password,
  notif_payment, notif_tracking, notif_complete, notif_general`;

router.use(customerAuth);

router.get('/', ah(async (req, res) => {
  const c = await db.one(`SELECT ${PROFILE_COLS} FROM customers WHERE id = ?`, [req.customer.id]);
  const stats = await db.one("SELECT COUNT(*) AS orders FROM orders WHERE customer_id = ? AND status <> 'pending_payment'", [req.customer.id]);
  const unread = await db.one('SELECT COUNT(*) AS n FROM notifications WHERE customer_id = ? AND is_read = 0', [req.customer.id]);
  res.json({ ...c, has_password: !!c.has_password, order_count: stats.orders, unread_notifications: unread.n });
}));

router.put('/', validate(z.object({
  name: z.string().trim().min(2).max(120),
  email: z.string().trim().email().max(190).optional().or(z.literal('')),
  gstin: z.string().trim().max(20).optional().or(z.literal('')),
})), ah(async (req, res) => {
  const { name, email, gstin } = req.body;
  if (email && (await db.one('SELECT id FROM customers WHERE email = ? AND id <> ?', [email.toLowerCase(), req.customer.id]))) {
    throw badRequest('This email is already used by another account');
  }
  await db.query('UPDATE customers SET name = ?, email = ?, gstin = ? WHERE id = ?', [name, email ? email.toLowerCase() : null, gstin || null, req.customer.id]);
  res.json(await db.one(`SELECT ${PROFILE_COLS} FROM customers WHERE id = ?`, [req.customer.id]));
}));

/** Profile photo (Edit Profile avatar). */
router.post('/avatar', imageUpload.single('file'), ah(async (req, res) => {
  if (!req.file) throw badRequest('Choose a photo');
  const [url] = await storeImages('avatars', [req.file]);
  await db.query('UPDATE customers SET avatar_url = ? WHERE id = ?', [url, req.customer.id]);
  res.json({ avatar_url: url });
}));

/** Change Password. Accounts created with OTP have no password yet, so "current" is only required when one exists. */
router.put('/password', validate(z.object({ current: z.string().optional(), password: z.string().min(8, 'Password must be at least 8 characters').max(100) })), ah(async (req, res) => {
  const c = await db.one('SELECT password_hash FROM customers WHERE id = ?', [req.customer.id]);
  if (c.password_hash) {
    if (!req.body.current || !(await bcrypt.compare(req.body.current, c.password_hash))) throw badRequest('Current password is incorrect');
  }
  await db.query('UPDATE customers SET password_hash = ? WHERE id = ?', [await bcrypt.hash(req.body.password, 10), req.customer.id]);
  res.json({ ok: true });
}));

/** Notifications settings toggles: Payment / Tracking / Complete Order / Notification. */
router.put('/notification-settings', validate(z.object({
  payment: z.boolean(), tracking: z.boolean(), complete_order: z.boolean(), general: z.boolean(),
})), ah(async (req, res) => {
  const b = req.body;
  await db.query('UPDATE customers SET notif_payment=?, notif_tracking=?, notif_complete=?, notif_general=? WHERE id=?',
    [b.payment ? 1 : 0, b.tracking ? 1 : 0, b.complete_order ? 1 : 0, b.general ? 1 : 0, req.customer.id]);
  res.json({ ok: true });
}));

router.put('/fcm-token', validate(z.object({ token: z.string().min(10) })), ah(async (req, res) => {
  await db.query('UPDATE customers SET fcm_token = ? WHERE id = ?', [req.body.token, req.customer.id]);
  res.json({ ok: true });
}));

/** Account deletion (required by Google Play policy). Anonymises the account; order records are retained for tax purposes. */
router.delete('/', ah(async (req, res) => {
  const id = req.customer.id;
  const c = await db.one('SELECT id FROM customers WHERE id = ?', [id]);
  if (!c) throw notFound();
  await db.query('DELETE FROM addresses WHERE customer_id = ?', [id]);
  await db.query('DELETE FROM cart_items WHERE customer_id = ?', [id]);
  await db.query('DELETE FROM wishlist_items WHERE customer_id = ?', [id]);
  await db.query("UPDATE customers SET mobile = CONCAT('deleted-', id), name = NULL, email = NULL, gstin = NULL, fcm_token = NULL, is_active = 0 WHERE id = ?", [id]);
  res.json({ message: 'Account deleted' });
}));

// ---- Addresses ----
const addressSchema = z.object({
  name: z.string().trim().min(2).max(120),
  phone: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit phone number'),
  line1: z.string().trim().min(3).max(255),
  line2: z.string().trim().max(255).optional().nullable(),
  landmark: z.string().trim().max(255).optional().nullable(),
  city: z.string().trim().min(2).max(100),
  state: z.string().trim().min(2).max(100),
  pincode: z.string().trim().regex(/^\d{6}$/, 'Enter a valid 6-digit pincode'),
  is_default: z.boolean().optional(),
});

router.get('/addresses', ah(async (req, res) => {
  res.json(await db.query('SELECT * FROM addresses WHERE customer_id = ? ORDER BY is_default DESC, id DESC', [req.customer.id]));
}));

router.post('/addresses', validate(addressSchema), ah(async (req, res) => {
  const a = req.body;
  const count = await db.one('SELECT COUNT(*) AS n FROM addresses WHERE customer_id = ?', [req.customer.id]);
  const makeDefault = a.is_default || count.n === 0;
  if (makeDefault) await db.query('UPDATE addresses SET is_default = 0 WHERE customer_id = ?', [req.customer.id]);
  const r = await db.query(
    'INSERT INTO addresses (customer_id, name, phone, line1, line2, landmark, city, state, pincode, is_default) VALUES (?,?,?,?,?,?,?,?,?,?)',
    [req.customer.id, a.name, a.phone, a.line1, a.line2 || null, a.landmark || null, a.city, a.state, a.pincode, makeDefault ? 1 : 0]
  );
  res.status(201).json(await db.one('SELECT * FROM addresses WHERE id = ?', [r.insertId]));
}));

router.put('/addresses/:id', validate(addressSchema), ah(async (req, res) => {
  const a = req.body;
  const ex = await db.one('SELECT id FROM addresses WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!ex) throw notFound('Address not found');
  if (a.is_default) await db.query('UPDATE addresses SET is_default = 0 WHERE customer_id = ?', [req.customer.id]);
  await db.query(
    'UPDATE addresses SET name=?, phone=?, line1=?, line2=?, landmark=?, city=?, state=?, pincode=?, is_default = IF(?, 1, is_default) WHERE id = ?',
    [a.name, a.phone, a.line1, a.line2 || null, a.landmark || null, a.city, a.state, a.pincode, a.is_default ? 1 : 0, ex.id]
  );
  res.json(await db.one('SELECT * FROM addresses WHERE id = ?', [ex.id]));
}));

router.delete('/addresses/:id', ah(async (req, res) => {
  const ex = await db.one('SELECT id, is_default FROM addresses WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!ex) throw notFound('Address not found');
  await db.query('DELETE FROM addresses WHERE id = ?', [ex.id]);
  if (ex.is_default) await db.query('UPDATE addresses SET is_default = 1 WHERE customer_id = ? ORDER BY id DESC LIMIT 1', [req.customer.id]);
  res.json({ ok: true });
}));

module.exports = router;
