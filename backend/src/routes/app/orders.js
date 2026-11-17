const router = require('express').Router();
const { z } = require('zod');
const db = require('../../config/db');
const { ah, validate, paging, pageMeta, notFound, badRequest } = require('../../utils/http');
const { customerAuth } = require('../../middleware/auth');
const orders = require('../../services/orders');
const razorpay = require('../../utils/razorpay');
const { renderInvoice } = require('../../utils/invoice');

router.use(customerAuth);

const parseAddr = (o) => ({ ...o, shipping_address: typeof o.shipping_address === 'string' ? JSON.parse(o.shipping_address) : o.shipping_address });

/** Step 1: create order from cart → returns Razorpay order details for the Checkout SDK. */
router.post('/', validate(z.object({
  address_id: z.number().int().positive(),
  coupon_code: z.string().trim().optional().nullable(),
  preferred_method: z.enum(['upi', 'card', 'netbanking', 'wallet']).optional().nullable(),
})), ah(async (req, res) => {
  const r = await orders.placeOrder(req.customer.id, req.body);
  const c = await db.one('SELECT mobile, name, email FROM customers WHERE id = ?', [req.customer.id]);
  res.status(201).json({ ...r, prefill: { contact: c.mobile, name: c.name || '', email: c.email || '' } });
}));

/** Step 2: verify payment signature returned by Razorpay Checkout on the device. */
router.post('/:id/verify-payment', validate(z.object({
  razorpay_order_id: z.string(), razorpay_payment_id: z.string(), razorpay_signature: z.string(),
})), ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = req.body;
  if (razorpay_order_id !== o.razorpay_order_id) throw badRequest('Payment does not match this order');
  if (!razorpay.verifyPaymentSignature(razorpay_order_id, razorpay_payment_id, razorpay_signature)) {
    throw badRequest('Payment verification failed');
  }
  await orders.markPaid(o.id, razorpay_payment_id, req.body);
  res.json({ success: true, order: parseAddr(await db.one('SELECT * FROM orders WHERE id = ?', [o.id])) });
}));

/** Payment cancelled/failed on the device → release reserved stock. */
router.post('/:id/payment-failed', validate(z.object({ reason: z.string().max(255).optional() })), ah(async (req, res) => {
  const o = await db.one('SELECT id, status FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  if (o.status === 'pending_payment') {
    await db.query("UPDATE payments SET status = 'failed' WHERE order_id = ?", [o.id]);
    await orders.releaseOrder(o.id, req.body.reason || 'Payment failed or cancelled');
  }
  res.json({ ok: true });
}));

router.get('/', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 15);
  const filter = req.query.type === 'current'
    ? "AND status IN ('placed','confirmed','packed','shipped','out_for_delivery')"
    : req.query.type === 'past' ? "AND status IN ('delivered','cancelled','returned')" : '';
  const where = `customer_id = ? AND status <> 'pending_payment' ${filter}`;
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM orders WHERE ${where}`, [req.customer.id]);
  const rows = await db.query(
    `SELECT id, order_number, status, payment_status, total, created_at,
       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) AS item_count,
       (SELECT image_url FROM order_items oi WHERE oi.order_id = o.id ORDER BY id LIMIT 1) AS image,
       (SELECT product_name FROM order_items oi WHERE oi.order_id = o.id ORDER BY id LIMIT 1) AS first_item,
       (SELECT variant_label FROM order_items oi WHERE oi.order_id = o.id ORDER BY id LIMIT 1) AS first_variant,
       (SELECT quantity FROM order_items oi WHERE oi.order_id = o.id ORDER BY id LIMIT 1) AS first_quantity,
       (SELECT variant_color FROM order_items oi WHERE oi.order_id = o.id ORDER BY id LIMIT 1) AS first_color
     FROM orders o WHERE ${where} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
    [req.customer.id, limit, offset]
  );
  res.json({ data: rows, meta: pageMeta(page, limit, total) });
}));

router.get('/:id', ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  const [items, timeline] = await Promise.all([
    db.query('SELECT * FROM order_items WHERE order_id = ?', [o.id]),
    db.query('SELECT status, note, created_at FROM order_status_history WHERE order_id = ? ORDER BY id', [o.id]),
  ]);
  res.json({ ...parseAddr(o), items, timeline, can_cancel: orders.CANCELLABLE_BY_CUSTOMER.includes(o.status) && o.status !== 'pending_payment' });
}));

router.post('/:id/cancel', validate(z.object({ reason: z.string().trim().max(255).optional() })), ah(async (req, res) => {
  await orders.cancelOrder(Number(req.params.id), { byCustomerId: req.customer.id, reason: req.body.reason || 'Cancelled by customer' });
  res.json({ ok: true });
}));

/** Add all still-available items from a past order back into the cart. */
/** "Received Order" / "Mark as Done": the customer confirms a shipped order has arrived. */
router.post('/:id/received', ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  if (o.status === 'delivered') return res.json({ ok: true });
  if (!['shipped', 'out_for_delivery'].includes(o.status)) throw badRequest('You can confirm delivery once the order has been shipped');
  await db.query("UPDATE orders SET status = 'delivered' WHERE id = ?", [o.id]);
  await db.query("INSERT INTO order_status_history (order_id, status, note) VALUES (?, 'delivered', 'Confirmed by customer')", [o.id]);
  require('../../utils/notify').notifyOrderStatus(o, 'delivered');
  res.json({ ok: true });
}));

router.post('/:id/reorder', ah(async (req, res) => {
  const o = await db.one('SELECT id FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  const items = await db.query(
    `SELECT oi.variant_id, oi.quantity, v.stock FROM order_items oi
     JOIN product_variants v ON v.id = oi.variant_id JOIN products p ON p.id = v.product_id
     WHERE oi.order_id = ? AND v.is_active = 1 AND p.is_active = 1 AND v.stock > 0`,
    [o.id]
  );
  for (const it of items) {
    const qty = Math.min(it.quantity, it.stock);
    await db.query('INSERT INTO cart_items (customer_id, variant_id, quantity) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE quantity = LEAST(quantity + VALUES(quantity), ?)',
      [req.customer.id, it.variant_id, qty, it.stock]);
  }
  res.json({ added: items.length });
}));

router.get('/:id/invoice', ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  if (!o) throw notFound('Order not found');
  if (o.payment_status === 'pending' || o.payment_status === 'failed') throw badRequest('Invoice is available after payment');
  const items = await db.query('SELECT * FROM order_items WHERE order_id = ?', [o.id]);
  const c = await db.one('SELECT * FROM customers WHERE id = ?', [o.customer_id]);
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `attachment; filename="invoice-${o.order_number}.pdf"`);
  renderInvoice(o, items, c, res);
}));

/** Payment history. */
router.get('/payments/history', ah(async (req, res) => {
  res.json(await db.query(
    `SELECT p.id, p.provider_payment_id, p.amount, p.status, p.created_at, o.id AS order_id, o.order_number
     FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.customer_id = ? ORDER BY p.id DESC LIMIT 100`,
    [req.customer.id]
  ));
}));

module.exports = router;
