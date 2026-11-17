const router = require('express').Router();
const { z } = require('zod');
const XLSX = require('xlsx');
const db = require('../../config/db');
const { ah, validate, paging, pageMeta, notFound, badRequest } = require('../../utils/http');
const { adminAuth } = require('../../middleware/auth');
const orders = require('../../services/orders');
const { notifyOrderStatus, broadcast, isEnabled } = require('../../utils/notify');
const { renderInvoice } = require('../../utils/invoice');
const { bust } = require('../../utils/cache');

router.use(adminAuth());

const bool = z.union([z.boolean(), z.number(), z.string()]).transform((v) => v === true || v === 1 || v === '1' || v === 'true');
const parseAddr = (o) => ({ ...o, shipping_address: typeof o.shipping_address === 'string' ? JSON.parse(o.shipping_address) : o.shipping_address });

function sendSheet(res, name, rows, header) {
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rows, header ? { header } : undefined), 'Report');
  const fmt = res.req.query.format === 'csv' ? 'csv' : 'xlsx';
  res.setHeader('Content-Disposition', `attachment; filename="${name}-${new Date().toISOString().slice(0, 10)}.${fmt}"`);
  if (fmt === 'csv') return res.type('text/csv').send(XLSX.write(wb, { type: 'string', bookType: 'csv' }));
  res.type('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet').send(XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }));
}

// ================= Dashboard =================
router.get('/dashboard', ah(async (_req, res) => {
  const PAID = "payment_status IN ('paid','partially_refunded')";
  const [totals, today, month, customers, catalog, lowStock, recent, daily, statusCounts] = await Promise.all([
    db.one(`SELECT COUNT(*) AS orders, COALESCE(SUM(total - refund_amount),0) AS sales FROM orders WHERE ${PAID}`),
    db.one(`SELECT COUNT(*) AS orders, COALESCE(SUM(total - refund_amount),0) AS sales FROM orders WHERE ${PAID} AND DATE(created_at) = CURDATE()`),
    db.one(`SELECT COUNT(*) AS orders, COALESCE(SUM(total - refund_amount),0) AS sales FROM orders WHERE ${PAID} AND created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')`),
    db.one('SELECT COUNT(*) AS total, SUM(created_at >= CURDATE() - INTERVAL 7 DAY) AS new_7d FROM customers WHERE is_active = 1'),
    db.one('SELECT (SELECT COUNT(*) FROM products) AS products, (SELECT COUNT(*) FROM products WHERE is_active = 1) AS active_products, (SELECT COUNT(*) FROM categories) AS categories'),
    db.query(`SELECT v.id, v.sku, v.label, v.stock, v.low_stock_threshold, p.id AS product_id, p.name
              FROM product_variants v JOIN products p ON p.id = v.product_id
              WHERE v.is_active = 1 AND p.is_active = 1 AND v.stock <= v.low_stock_threshold ORDER BY v.stock, p.name LIMIT 10`),
    db.query(`SELECT o.id, o.order_number, o.status, o.payment_status, o.total, o.created_at, c.name AS customer_name, c.mobile
              FROM orders o JOIN customers c ON c.id = o.customer_id WHERE o.status <> 'pending_payment' ORDER BY o.id DESC LIMIT 8`),
    db.query(`SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS orders, SUM(total - refund_amount) AS sales
              FROM orders WHERE ${PAID} AND created_at >= CURDATE() - INTERVAL 29 DAY GROUP BY day ORDER BY day`),
    db.query("SELECT status, COUNT(*) AS n FROM orders WHERE status <> 'pending_payment' GROUP BY status"),
  ]);
  const lowCount = await db.one('SELECT COUNT(*) AS n FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.is_active = 1 AND p.is_active = 1 AND v.stock <= v.low_stock_threshold');
  // Fill gaps so the chart has 30 points
  const map = Object.fromEntries(daily.map((d) => [d.day, d]));
  const series = [];
  for (let i = 29; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i);
    const key = d.toISOString().slice(0, 10);
    series.push({ day: key, orders: map[key] ? Number(map[key].orders) : 0, sales: map[key] ? Number(map[key].sales) : 0 });
  }
  res.json({
    totals, today, month, customers: { total: customers.total, new_7d: Number(customers.new_7d || 0) }, catalog,
    low_stock: { count: lowCount.n, items: lowStock }, recent_orders: recent, sales_30d: series,
    status_counts: Object.fromEntries(statusCounts.map((s) => [s.status, s.n])),
  });
}));

// ================= Orders =================
const ORDER_FLOW = ['placed', 'confirmed', 'packed', 'shipped', 'out_for_delivery', 'delivered'];

function orderFilters(q) {
  const where = ["o.status <> 'pending_payment'"];
  const params = [];
  if (q.include_unpaid === '1') where.shift();
  if (q.status) { where.push('o.status = ?'); params.push(q.status); }
  if (q.payment_status) { where.push('o.payment_status = ?'); params.push(q.payment_status); }
  if (q.from) { where.push('o.created_at >= ?'); params.push(`${q.from} 00:00:00`); }
  if (q.to) { where.push('o.created_at <= ?'); params.push(`${q.to} 23:59:59`); }
  if (q.q) { where.push('(o.order_number LIKE ? OR c.mobile LIKE ? OR c.name LIKE ?)'); params.push(`%${q.q}%`, `%${q.q}%`, `%${q.q}%`); }
  if (q.customer_id) { where.push('o.customer_id = ?'); params.push(q.customer_id); }
  return { w: where.join(' AND ') || '1=1', params };
}

router.get('/orders', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 25, 200);
  const { w, params } = orderFilters(req.query);
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM orders o JOIN customers c ON c.id = o.customer_id WHERE ${w}`, params);
  const rows = await db.query(
    `SELECT o.id, o.order_number, o.status, o.payment_status, o.total, o.created_at, o.customer_id, c.name AS customer_name, c.mobile,
       (SELECT SUM(quantity) FROM order_items oi WHERE oi.order_id = o.id) AS items
     FROM orders o JOIN customers c ON c.id = o.customer_id WHERE ${w} ORDER BY o.id DESC LIMIT ? OFFSET ?`,
    [...params, limit, offset]
  );
  res.json({ data: rows, meta: pageMeta(page, limit, total) });
}));

router.get('/orders-export', ah(async (req, res) => {
  const { w, params } = orderFilters(req.query);
  const rows = await db.query(
    `SELECT o.order_number, DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS date, c.name AS customer, c.mobile, o.status, o.payment_status,
       o.subtotal, o.discount, o.coupon_code, o.delivery_charge, o.total, o.refund_amount, o.razorpay_payment_id, o.invoice_number,
       JSON_UNQUOTE(JSON_EXTRACT(o.shipping_address, '$.city')) AS city, JSON_UNQUOTE(JSON_EXTRACT(o.shipping_address, '$.state')) AS state,
       JSON_UNQUOTE(JSON_EXTRACT(o.shipping_address, '$.pincode')) AS pincode
     FROM orders o JOIN customers c ON c.id = o.customer_id WHERE ${w} ORDER BY o.id DESC LIMIT 50000`,
    params
  );
  sendSheet(res, 'orders', rows);
}));

router.get('/orders/:id', ah(async (req, res) => {
  const o = await db.one('SELECT o.*, c.name AS customer_name, c.mobile, c.email FROM orders o JOIN customers c ON c.id = o.customer_id WHERE o.id = ?', [req.params.id]);
  if (!o) throw notFound();
  const [items, timeline, payments] = await Promise.all([
    db.query('SELECT * FROM order_items WHERE order_id = ?', [o.id]),
    db.query('SELECT status, note, created_at FROM order_status_history WHERE order_id = ? ORDER BY id', [o.id]),
    db.query('SELECT id, provider_order_id, provider_payment_id, amount, status, created_at FROM payments WHERE order_id = ? ORDER BY id', [o.id]),
  ]);
  const idx = ORDER_FLOW.indexOf(o.status);
  const next = idx >= 0 && idx < ORDER_FLOW.length - 1 ? ORDER_FLOW.slice(idx + 1) : [];
  res.json({ ...parseAddr(o), items, timeline, payments, next_statuses: next });
}));

router.patch('/orders/:id/status', validate(z.object({ status: z.enum(['confirmed', 'packed', 'shipped', 'out_for_delivery', 'delivered', 'returned']), note: z.string().max(255).optional() })), ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ?', [req.params.id]);
  if (!o) throw notFound();
  const { status, note } = req.body;
  if (['pending_payment', 'cancelled'].includes(o.status)) throw badRequest(`Cannot update a ${o.status.replace('_', ' ')} order`);
  if (status === 'returned' && o.status !== 'delivered') throw badRequest('Only delivered orders can be marked returned');
  if (status !== 'returned' && ORDER_FLOW.indexOf(status) <= ORDER_FLOW.indexOf(o.status)) throw badRequest(`Order is already ${o.status.replace(/_/g, ' ')}`);
  await db.query('UPDATE orders SET status = ? WHERE id = ?', [status, o.id]);
  await db.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [o.id, status, note || `Updated by ${req.admin.name}`]);
  if (status === 'returned') {
    const items = await db.query('SELECT variant_id, quantity FROM order_items WHERE order_id = ?', [o.id]);
    for (const it of items) if (it.variant_id) await db.query('UPDATE product_variants SET stock = stock + ? WHERE id = ?', [it.quantity, it.variant_id]);
  }
  notifyOrderStatus(o, status);
  res.json({ ok: true });
}));

router.patch('/orders/:id/payment-status', validate(z.object({ payment_status: z.enum(['pending', 'paid', 'failed']), note: z.string().max(255).optional() })), ah(async (req, res) => {
  const o = await db.one('SELECT id FROM orders WHERE id = ?', [req.params.id]);
  if (!o) throw notFound();
  if (req.body.payment_status === 'paid') {
    await orders.markPaid(o.id, null, { manual: true, by: req.admin.email });
  } else {
    await db.query('UPDATE orders SET payment_status = ? WHERE id = ?', [req.body.payment_status, o.id]);
  }
  await db.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [o.id, `payment_${req.body.payment_status}`, req.body.note || `Set by ${req.admin.name}`]);
  res.json({ ok: true });
}));

router.post('/orders/:id/cancel', validate(z.object({ reason: z.string().trim().min(2).max(255), refund: z.boolean().default(true) })), ah(async (req, res) => {
  await orders.cancelOrder(Number(req.params.id), { reason: req.body.reason, refund: req.body.refund });
  res.json({ ok: true });
}));

router.patch('/orders/:id/courier', validate(z.object({
  courier_name: z.string().trim().max(120).optional().nullable(),
  courier_phone: z.string().trim().max(20).optional().nullable(),
  tracking_url: z.string().trim().url().max(512).optional().nullable().or(z.literal('')),
})), ah(async (req, res) => {
  const b = req.body;
  await db.query('UPDATE orders SET courier_name = ?, courier_phone = ?, tracking_url = ? WHERE id = ?', [b.courier_name || null, b.courier_phone || null, b.tracking_url || null, req.params.id]);
  res.json({ ok: true });
}));

router.post('/orders/:id/refund', validate(z.object({ amount: z.coerce.number().positive() })), ah(async (req, res) => {
  res.json(await orders.refundOrder(Number(req.params.id), req.body.amount));
}));

router.get('/orders/:id/invoice', ah(async (req, res) => {
  const o = await db.one('SELECT * FROM orders WHERE id = ?', [req.params.id]);
  if (!o) throw notFound();
  const items = await db.query('SELECT * FROM order_items WHERE order_id = ?', [o.id]);
  const c = await db.one('SELECT * FROM customers WHERE id = ?', [o.customer_id]);
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `inline; filename="invoice-${o.order_number}.pdf"`);
  renderInvoice(o, items, c, res);
}));

// ================= Coupons =================
const couponSchema = z.object({
  code: z.string().trim().min(3).max(40).transform((s) => s.toUpperCase()).refine((s) => /^[A-Z0-9_-]+$/.test(s), 'Use letters, numbers, - or _ only'),
  description: z.string().max(255).optional().nullable(),
  discount_type: z.enum(['fixed', 'percent']),
  discount_value: z.coerce.number().positive(),
  max_discount: z.coerce.number().positive().nullable().optional(),
  min_order_value: z.coerce.number().min(0).default(0),
  valid_from: z.string().nullable().optional(),
  valid_to: z.string().nullable().optional(),
  usage_limit: z.coerce.number().int().positive().nullable().optional(),
  per_customer_limit: z.coerce.number().int().positive().default(1),
  is_active: bool.default(true),
}).refine((c) => c.discount_type !== 'percent' || c.discount_value <= 100, { message: 'Percentage cannot exceed 100', path: ['discount_value'] });

const couponVals = (c) => [c.code, c.description || null, c.discount_type, c.discount_value, c.max_discount || null, c.min_order_value,
  c.valid_from || null, c.valid_to || null, c.usage_limit || null, c.per_customer_limit, c.is_active ? 1 : 0];

router.get('/coupons', ah(async (_req, res) => res.json(await db.query('SELECT * FROM coupons ORDER BY id DESC'))));

router.post('/coupons', validate(couponSchema), ah(async (req, res) => {
  if (await db.one('SELECT id FROM coupons WHERE code = ?', [req.body.code])) throw badRequest('Coupon code already exists');
  const r = await db.query('INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_value, valid_from, valid_to, usage_limit, per_customer_limit, is_active) VALUES (?,?,?,?,?,?,?,?,?,?,?)', couponVals(req.body));
  res.status(201).json(await db.one('SELECT * FROM coupons WHERE id = ?', [r.insertId]));
}));

router.put('/coupons/:id', validate(couponSchema), ah(async (req, res) => {
  if (await db.one('SELECT id FROM coupons WHERE code = ? AND id <> ?', [req.body.code, req.params.id])) throw badRequest('Coupon code already exists');
  await db.query('UPDATE coupons SET code=?, description=?, discount_type=?, discount_value=?, max_discount=?, min_order_value=?, valid_from=?, valid_to=?, usage_limit=?, per_customer_limit=?, is_active=? WHERE id=?', [...couponVals(req.body), req.params.id]);
  res.json(await db.one('SELECT * FROM coupons WHERE id = ?', [req.params.id]));
}));

router.patch('/coupons/:id/status', validate(z.object({ is_active: bool })), ah(async (req, res) => {
  await db.query('UPDATE coupons SET is_active = ? WHERE id = ?', [req.body.is_active ? 1 : 0, req.params.id]);
  res.json({ ok: true });
}));

router.delete('/coupons/:id', ah(async (req, res) => {
  const used = await db.one('SELECT used_count FROM coupons WHERE id = ?', [req.params.id]);
  if (used && used.used_count > 0) throw badRequest('This coupon has been used — disable it instead of deleting');
  await db.query('DELETE FROM coupons WHERE id = ?', [req.params.id]);
  res.json({ ok: true });
}));

// ================= Banners =================
const bannerSchema = z.object({
  title: z.string().trim().min(2).max(150),
  description: z.string().max(255).optional().nullable(),
  image_url: z.string().url(),
  redirect_type: z.enum(['none', 'product', 'category', 'url']).default('none'),
  redirect_value: z.string().max(512).optional().nullable(),
  display_order: z.coerce.number().int().default(0),
  is_active: bool.default(true),
});
const bannerVals = (b) => [b.title, b.description || null, b.image_url, b.redirect_type, b.redirect_value || null, b.display_order, b.is_active ? 1 : 0];

router.get('/banners', ah(async (_req, res) => res.json(await db.query('SELECT * FROM banners ORDER BY display_order, id'))));
router.post('/banners', validate(bannerSchema), ah(async (req, res) => {
  const r = await db.query('INSERT INTO banners (title, description, image_url, redirect_type, redirect_value, display_order, is_active) VALUES (?,?,?,?,?,?,?)', bannerVals(req.body));
  bust('home');
  res.status(201).json({ id: r.insertId });
}));
router.put('/banners/:id', validate(bannerSchema), ah(async (req, res) => {
  await db.query('UPDATE banners SET title=?, description=?, image_url=?, redirect_type=?, redirect_value=?, display_order=?, is_active=? WHERE id=?', [...bannerVals(req.body), req.params.id]);
  bust('home');
  res.json({ ok: true });
}));
router.patch('/banners/:id/status', validate(z.object({ is_active: bool })), ah(async (req, res) => {
  await db.query('UPDATE banners SET is_active = ? WHERE id = ?', [req.body.is_active ? 1 : 0, req.params.id]);
  bust('home');
  res.json({ ok: true });
}));
router.delete('/banners/:id', ah(async (req, res) => {
  await db.query('DELETE FROM banners WHERE id = ?', [req.params.id]);
  bust('home');
  res.json({ ok: true });
}));

// ================= Customers =================
router.get('/customers', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 25, 200);
  const where = ["c.mobile NOT LIKE 'deleted-%'"];
  const params = [];
  if (req.query.q) { where.push('(c.name LIKE ? OR c.mobile LIKE ? OR c.email LIKE ?)'); params.push(`%${req.query.q}%`, `%${req.query.q}%`, `%${req.query.q}%`); }
  if (req.query.status === 'active') where.push('c.is_active = 1');
  if (req.query.status === 'blocked') where.push('c.is_active = 0');
  const w = where.join(' AND ');
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM customers c WHERE ${w}`, params);
  const rows = await db.query(
    `SELECT c.id, c.mobile, c.name, c.email, c.is_active, c.created_at,
       (SELECT COUNT(*) FROM orders o WHERE o.customer_id = c.id AND o.payment_status IN ('paid','partially_refunded','refunded')) AS orders,
       (SELECT COALESCE(SUM(total - refund_amount),0) FROM orders o WHERE o.customer_id = c.id AND o.payment_status IN ('paid','partially_refunded')) AS spent
     FROM customers c WHERE ${w} ORDER BY c.id DESC LIMIT ? OFFSET ?`,
    [...params, limit, offset]
  );
  res.json({ data: rows, meta: pageMeta(page, limit, total) });
}));

router.get('/customers/:id', ah(async (req, res) => {
  const c = await db.one('SELECT id, mobile, name, email, gstin, is_active, created_at FROM customers WHERE id = ?', [req.params.id]);
  if (!c) throw notFound();
  const [addresses, orderRows, stats] = await Promise.all([
    db.query('SELECT * FROM addresses WHERE customer_id = ?', [c.id]),
    db.query("SELECT id, order_number, status, payment_status, total, created_at FROM orders WHERE customer_id = ? AND status <> 'pending_payment' ORDER BY id DESC LIMIT 100", [c.id]),
    db.one("SELECT COUNT(*) AS orders, COALESCE(SUM(total - refund_amount),0) AS spent FROM orders WHERE customer_id = ? AND payment_status IN ('paid','partially_refunded')", [c.id]),
  ]);
  res.json({ ...c, addresses, orders: orderRows, stats });
}));

router.patch('/customers/:id/status', validate(z.object({ is_active: bool })), ah(async (req, res) => {
  await db.query('UPDATE customers SET is_active = ? WHERE id = ?', [req.body.is_active ? 1 : 0, req.params.id]);
  res.json({ ok: true });
}));

// ================= Reports =================
const PAID = "o.payment_status IN ('paid','partially_refunded')";
function range(q) {
  const to = q.to || new Date().toISOString().slice(0, 10);
  const from = q.from || new Date(Date.now() - 29 * 864e5).toISOString().slice(0, 10);
  return [`${from} 00:00:00`, `${to} 23:59:59`, from, to];
}

async function salesReport(q) {
  const [from, to] = range(q);
  const fmt = { daily: '%Y-%m-%d', weekly: '%x-W%v', monthly: '%Y-%m' }[q.period] || '%Y-%m-%d';
  return db.query(
    `SELECT DATE_FORMAT(o.created_at, '${fmt}') AS period, COUNT(*) AS orders, SUM(o.subtotal) AS gross_sales, SUM(o.discount) AS discounts,
       SUM(o.delivery_charge) AS delivery, SUM(o.refund_amount) AS refunds, SUM(o.total - o.refund_amount) AS net_sales,
       ROUND(AVG(o.total), 2) AS avg_order_value
     FROM orders o WHERE ${PAID} AND o.created_at BETWEEN ? AND ? GROUP BY period ORDER BY period`,
    [from, to]
  );
}
async function productReport(q) {
  const [from, to] = range(q);
  return db.query(
    `SELECT oi.product_name AS product, oi.variant_label AS variant, oi.sku, SUM(oi.quantity) AS units_sold, SUM(oi.line_total) AS revenue, COUNT(DISTINCT o.id) AS orders
     FROM order_items oi JOIN orders o ON o.id = oi.order_id
     WHERE ${PAID} AND o.status NOT IN ('cancelled','returned') AND o.created_at BETWEEN ? AND ?
     GROUP BY oi.product_name, oi.variant_label, oi.sku ORDER BY revenue DESC LIMIT 1000`,
    [from, to]
  );
}
async function orderReport(q) {
  const [from, to] = range(q);
  return db.query(
    `SELECT o.status, o.payment_status, COUNT(*) AS orders, SUM(o.total) AS value
     FROM orders o WHERE o.status <> 'pending_payment' AND o.created_at BETWEEN ? AND ? GROUP BY o.status, o.payment_status ORDER BY orders DESC`,
    [from, to]
  );
}
async function stockReport(q) {
  const low = q.type === 'low';
  return db.query(
    `SELECT p.name AS product, v.label AS variant, v.sku, c.name AS category, v.mrp, v.price, v.stock, v.low_stock_threshold,
       ROUND(v.stock * v.price, 2) AS stock_value, IF(v.stock = 0, 'Out of stock', IF(v.stock <= v.low_stock_threshold, 'Low', 'OK')) AS stock_status
     FROM product_variants v JOIN products p ON p.id = v.product_id LEFT JOIN categories c ON c.id = p.category_id
     WHERE v.is_active = 1 ${low ? 'AND v.stock <= v.low_stock_threshold' : ''} ORDER BY v.stock, p.name`
  );
}

const REPORTS = { sales: salesReport, products: productReport, orders: orderReport, stock: stockReport };

router.get('/reports/:type(sales|products|orders|stock)', ah(async (req, res) => {
  const rows = await REPORTS[req.params.type](req.query);
  if (req.query.export) return sendSheet(res, `${req.params.type}-report`, rows);
  const totals = {};
  if (rows.length) {
    Object.keys(rows[0]).forEach((k) => {
      if (typeof rows[0][k] === 'number' && !['low_stock_threshold', 'mrp', 'price', 'avg_order_value'].includes(k)) {
        totals[k] = Math.round(rows.reduce((a, r) => a + Number(r[k] || 0), 0) * 100) / 100;
      }
    });
  }
  res.json({ rows, totals });
}));

// ================= Push notifications =================
router.post('/notifications/broadcast', validate(z.object({ title: z.string().min(2).max(100), body: z.string().min(2).max(300), product_id: z.number().int().optional() })), ah(async (req, res) => {
  const data = {};
  if (req.body.product_id) data.product_id = req.body.product_id;
  res.json(await broadcast(req.body.title, req.body.body, data));
}));

module.exports = router;
