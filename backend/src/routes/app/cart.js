const router = require('express').Router();
const { z } = require('zod');
const db = require('../../config/db');
const { ah, validate, badRequest, notFound } = require('../../utils/http');
const { customerAuth } = require('../../middleware/auth');
const { quote } = require('../../services/orders');
const { hydrate } = require('../../services/catalog');

router.use(['/cart', '/wishlist', '/coupons'], customerAuth);

const q = { query: db.query, one: db.one };

// ---- Cart ----
router.get('/cart', ah(async (req, res) => {
  const r = await quote(q, req.customer.id, req.query.coupon);
  delete r.coupon;
  res.json(r);
}));

router.post('/cart', validate(z.object({ variant_id: z.number().int().positive(), quantity: z.number().int().min(1).max(50).default(1) })), ah(async (req, res) => {
  const { variant_id, quantity } = req.body;
  const v = await db.one('SELECT v.id, v.stock, v.is_active, p.is_active AS p_active FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.id = ?', [variant_id]);
  if (!v || !v.is_active || !v.p_active) throw notFound('Product not available');
  const existing = await db.one('SELECT quantity FROM cart_items WHERE customer_id = ? AND variant_id = ?', [req.customer.id, variant_id]);
  const newQty = (existing ? existing.quantity : 0) + quantity;
  if (newQty > v.stock) throw badRequest(v.stock > 0 ? `Only ${v.stock} in stock` : 'Out of stock');
  await db.query(
    'INSERT INTO cart_items (customer_id, variant_id, quantity) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE quantity = ?',
    [req.customer.id, variant_id, newQty, newQty]
  );
  const r = await quote(q, req.customer.id);
  delete r.coupon;
  res.json(r);
}));

router.put('/cart/:variantId', validate(z.object({ quantity: z.number().int().min(0).max(50) })), ah(async (req, res) => {
  const variantId = Number(req.params.variantId);
  if (req.body.quantity === 0) {
    await db.query('DELETE FROM cart_items WHERE customer_id = ? AND variant_id = ?', [req.customer.id, variantId]);
  } else {
    const v = await db.one('SELECT stock FROM product_variants WHERE id = ?', [variantId]);
    if (!v) throw notFound();
    if (req.body.quantity > v.stock) throw badRequest(v.stock > 0 ? `Only ${v.stock} in stock` : 'Out of stock');
    await db.query('UPDATE cart_items SET quantity = ? WHERE customer_id = ? AND variant_id = ?', [req.body.quantity, req.customer.id, variantId]);
  }
  const r = await quote(q, req.customer.id);
  delete r.coupon;
  res.json(r);
}));

router.delete('/cart/:variantId', ah(async (req, res) => {
  await db.query('DELETE FROM cart_items WHERE customer_id = ? AND variant_id = ?', [req.customer.id, Number(req.params.variantId)]);
  const r = await quote(q, req.customer.id);
  delete r.coupon;
  res.json(r);
}));

router.delete('/cart', ah(async (req, res) => {
  await db.query('DELETE FROM cart_items WHERE customer_id = ?', [req.customer.id]);
  res.json({ ok: true });
}));

/** Validate a coupon against the current cart. */
router.post('/cart/apply-coupon', validate(z.object({ code: z.string().trim().min(1) })), ah(async (req, res) => {
  const r = await quote(q, req.customer.id, req.body.code);
  if (r.summary.coupon_error) throw badRequest(r.summary.coupon_error);
  delete r.coupon;
  res.json(r);
}));

/** Coupons the customer can see in the app. */
router.get('/coupons', ah(async (_req, res) => {
  res.json(await db.query(
    `SELECT code, description, discount_type, discount_value, max_discount, min_order_value, valid_to FROM coupons
     WHERE is_active = 1 AND (valid_from IS NULL OR valid_from <= NOW()) AND (valid_to IS NULL OR valid_to >= NOW())
       AND (usage_limit IS NULL OR used_count < usage_limit) ORDER BY min_order_value`
  ));
}));

// ---- Wishlist ----
router.get('/wishlist', ah(async (req, res) => {
  const rows = await db.query(
    `SELECT p.id, p.category_id, p.name, p.slug, p.is_featured, p.gst_rate, p.created_at, p.sold_count
     FROM wishlist_items w JOIN products p ON p.id = w.product_id WHERE w.customer_id = ? AND p.is_active = 1 ORDER BY w.id DESC`,
    [req.customer.id]
  );
  res.json((await hydrate(rows)).map((p) => ({ ...p, wishlisted: true })));
}));

router.post('/wishlist/:productId', ah(async (req, res) => {
  const p = await db.one('SELECT id FROM products WHERE id = ?', [req.params.productId]);
  if (!p) throw notFound('Product not found');
  await db.query('INSERT IGNORE INTO wishlist_items (customer_id, product_id) VALUES (?, ?)', [req.customer.id, p.id]);
  res.json({ wishlisted: true });
}));

router.delete('/wishlist/:productId', ah(async (req, res) => {
  await db.query('DELETE FROM wishlist_items WHERE customer_id = ? AND product_id = ?', [req.customer.id, Number(req.params.productId)]);
  res.json({ wishlisted: false });
}));

module.exports = router;
