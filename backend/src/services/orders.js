const crypto = require('crypto');
const config = require('../config');
const db = require('../config/db');
const { badRequest, notFound, round2 } = require('../utils/http');
const razorpay = require('../utils/razorpay');
const { notifyOrderStatus } = require('../utils/notify');

const CANCELLABLE_BY_CUSTOMER = ['pending_payment', 'placed', 'confirmed'];
const RESERVATION_MINUTES = 30;

/** Load the customer's cart with live product data. */
async function loadCart(q, customerId) {
  return q.query(
    `SELECT ci.id, ci.quantity, ci.selected, v.id AS variant_id, v.sku, v.label, v.color, v.color_hex, v.mrp, v.price, v.stock, v.is_active AS variant_active,
            p.id AS product_id, p.name, p.hsn_code, p.gst_rate, p.is_active AS product_active,
            (SELECT url FROM product_images pi WHERE pi.product_id = p.id ORDER BY sort_order, id LIMIT 1) AS image
     FROM cart_items ci
     JOIN product_variants v ON v.id = ci.variant_id
     JOIN products p ON p.id = v.product_id
     WHERE ci.customer_id = ? ORDER BY ci.id`,
    [customerId]
  );
}

/** Validate a coupon for a subtotal; returns { coupon, discount } or throws. */
async function evaluateCoupon(q, code, customerId, subtotal) {
  const c = await q.one('SELECT * FROM coupons WHERE code = ?', [String(code).trim().toUpperCase()]);
  if (!c || !c.is_active) throw badRequest('Invalid coupon code');
  const now = new Date();
  if (c.valid_from && new Date(c.valid_from) > now) throw badRequest('This coupon is not active yet');
  if (c.valid_to && new Date(c.valid_to) < now) throw badRequest('This coupon has expired');
  if (c.usage_limit !== null && c.used_count >= c.usage_limit) throw badRequest('This coupon has reached its usage limit');
  if (subtotal < Number(c.min_order_value)) throw badRequest(`Add items worth ₹${round2(c.min_order_value - subtotal)} more to use this coupon`);
  const used = await q.one('SELECT COUNT(*) AS n FROM coupon_redemptions WHERE coupon_id = ? AND customer_id = ?', [c.id, customerId]);
  if (used.n >= c.per_customer_limit) throw badRequest('You have already used this coupon');
  let discount = c.discount_type === 'percent' ? (subtotal * Number(c.discount_value)) / 100 : Number(c.discount_value);
  if (c.max_discount !== null) discount = Math.min(discount, Number(c.max_discount));
  discount = round2(Math.min(discount, subtotal));
  return { coupon: c, discount };
}

/** Compute a price summary from cart rows. */
async function quote(q, customerId, couponCode) {
  const items = await loadCart(q, customerId);
  const problems = [];
  let subtotal = 0;
  let mrpTotal = 0;
  // Only items ticked in My Cart count towards the bill and the order
  for (const it of items.filter((x) => x.selected)) {
    if (!it.product_active || !it.variant_active) problems.push({ variant_id: it.variant_id, message: `${it.name} is no longer available` });
    else if (it.stock < it.quantity) problems.push({ variant_id: it.variant_id, message: it.stock > 0 ? `Only ${it.stock} left for ${it.name} (${it.label})` : `${it.name} (${it.label}) is out of stock` });
    subtotal += Number(it.price) * it.quantity;
    mrpTotal += Number(it.mrp) * it.quantity;
  }
  subtotal = round2(subtotal);
  let discount = 0;
  let coupon = null;
  let couponError = null;
  if (couponCode) {
    try {
      ({ coupon, discount } = await evaluateCoupon(q, couponCode, customerId, subtotal));
    } catch (e) {
      couponError = e.message;
    }
  }
  const delivery = subtotal === 0 || subtotal - discount >= config.delivery.freeAbove ? 0 : config.delivery.charge;
  return {
    items: items.map((it) => ({ ...it, selected: !!it.selected, line_total: round2(it.price * it.quantity) })),
    problems,
    summary: {
      item_count: items.filter((x) => x.selected).reduce((a, b) => a + b.quantity, 0),
      cart_count: items.reduce((a, b) => a + b.quantity, 0),
      mrp_total: round2(mrpTotal),
      subtotal,
      savings: round2(mrpTotal - subtotal + discount),
      discount,
      coupon_code: coupon ? coupon.code : null,
      coupon_error: couponError,
      delivery_charge: delivery,
      free_delivery_above: config.delivery.freeAbove,
      total: round2(subtotal - discount + delivery),
    },
    coupon,
  };
}

const orderNumber = () => {
  const d = new Date();
  const ymd = `${d.getFullYear() % 100}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  return `OD${ymd}${crypto.randomInt(100000, 999999)}`;
};

/**
 * Create an order from the cart, reserve stock and create a Razorpay order.
 * Stock is released automatically if payment is not completed within RESERVATION_MINUTES.
 */
async function placeOrder(customerId, { address_id, coupon_code, preferred_method }) {
  // Free up stock from abandoned checkouts before reserving more (needed where no background job runs)
  await sweepStaleReservations().catch(() => {});
  const order = await db.tx(async (q) => {
    const addr = await q.one('SELECT * FROM addresses WHERE id = ? AND customer_id = ?', [address_id, customerId]);
    if (!addr) throw badRequest('Please select a valid delivery address');
    // Lock the variant rows to prevent overselling
    const cartRows = await q.query('SELECT variant_id FROM cart_items WHERE customer_id = ? AND selected = 1', [customerId]);
    if (!cartRows.length) throw badRequest('Select at least one item to check out');
    await q.query('SELECT id FROM product_variants WHERE id IN (?) FOR UPDATE', [cartRows.map((r) => r.variant_id)]);

    const qt = await quote(q, customerId, coupon_code);
    if (qt.problems.length) throw badRequest(qt.problems[0].message, qt.problems);
    if (coupon_code && qt.summary.coupon_error) throw badRequest(qt.summary.coupon_error);

    const s = qt.summary;
    const shipping = {
      name: addr.name, phone: addr.phone, line1: addr.line1, line2: addr.line2, landmark: addr.landmark,
      city: addr.city, state: addr.state, pincode: addr.pincode,
    };
    const ordered = qt.items.filter((it) => it.selected);
    const number = orderNumber();
    const res = await q.query(
      `INSERT INTO orders (order_number, customer_id, status, payment_status, subtotal, discount, delivery_charge, total,
                           coupon_id, coupon_code, shipping_address, preferred_method)
       VALUES (?, ?, 'pending_payment', 'pending', ?, ?, ?, ?, ?, ?, ?, ?)`,
      [number, customerId, s.subtotal, s.discount, s.delivery_charge, s.total, qt.coupon ? qt.coupon.id : null, s.coupon_code, JSON.stringify(shipping), preferred_method || null]
    );
    const orderId = res.insertId;
    for (const it of ordered) {
      await q.query(
        `INSERT INTO order_items (order_id, product_id, variant_id, product_name, variant_label, variant_color, sku, image_url, hsn_code, gst_rate, unit_price, mrp, quantity, line_total)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
        [orderId, it.product_id, it.variant_id, it.name, it.label, it.color || null, it.sku, it.image, it.hsn_code, it.gst_rate, it.price, it.mrp, it.quantity, it.line_total]
      );
      await q.query('UPDATE product_variants SET stock = stock - ? WHERE id = ?', [it.quantity, it.variant_id]);
    }
    await q.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [orderId, 'pending_payment', 'Awaiting payment']);
    return { id: orderId, order_number: number, total: s.total, customer_id: customerId, preferred_method: preferred_method || null };
  });

  try {
    const rz = await razorpay.createOrder(order.total, order.order_number, { order_id: String(order.id) });
    await db.query('UPDATE orders SET razorpay_order_id = ? WHERE id = ?', [rz.id, order.id]);
    await db.query('INSERT INTO payments (order_id, provider_order_id, amount, status) VALUES (?, ?, ?, ?)', [order.id, rz.id, order.total, 'created']);
    return { ...order, razorpay_order_id: rz.id, razorpay_key_id: config.razorpay.keyId, amount_paise: rz.amount, currency: 'INR' };
  } catch (e) {
    await releaseOrder(order.id, 'Payment gateway error');
    throw badRequest('Could not start payment. Please try again.');
  }
}

/** Mark an order paid (idempotent). Called from device verification and from the webhook. */
async function markPaid(orderId, paymentId, raw) {
  const changed = await db.tx(async (q) => {
    const o = await q.one('SELECT * FROM orders WHERE id = ? FOR UPDATE', [orderId]);
    if (!o) throw notFound('Order not found');
    if (o.payment_status === 'paid') return null;
    if (o.status === 'cancelled') {
      // Paid after reservation expired — re-reserve stock if possible, otherwise leave for admin refund.
      const items = await q.query('SELECT variant_id, quantity FROM order_items WHERE order_id = ?', [orderId]);
      for (const it of items) {
        if (it.variant_id) await q.query('UPDATE product_variants SET stock = stock - ? WHERE id = ?', [it.quantity, it.variant_id]);
      }
    }
    const invoice = `INV-${new Date().getFullYear()}-${String(orderId).padStart(6, '0')}`;
    await q.query(
      "UPDATE orders SET status = 'placed', payment_status = 'paid', razorpay_payment_id = ?, invoice_number = ?, cancel_reason = NULL WHERE id = ?",
      [paymentId, invoice, orderId]
    );
    await q.query("UPDATE payments SET provider_payment_id = ?, status = 'captured', raw = ? WHERE order_id = ? AND provider_order_id = ?",
      [paymentId, raw ? JSON.stringify(raw) : null, orderId, o.razorpay_order_id]);
    await q.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [orderId, 'placed', 'Payment received']);
    const items = await q.query('SELECT product_id, quantity FROM order_items WHERE order_id = ?', [orderId]);
    for (const it of items) if (it.product_id) await q.query('UPDATE products SET sold_count = sold_count + ? WHERE id = ?', [it.quantity, it.product_id]);
    if (o.coupon_id) {
      await q.query('UPDATE coupons SET used_count = used_count + 1 WHERE id = ?', [o.coupon_id]);
      await q.query('INSERT INTO coupon_redemptions (coupon_id, customer_id, order_id) VALUES (?, ?, ?)', [o.coupon_id, o.customer_id, orderId]);
    }
    const orderedVariants = (await q.query('SELECT variant_id FROM order_items WHERE order_id = ? AND variant_id IS NOT NULL', [orderId])).map((r) => r.variant_id);
    if (orderedVariants.length) await q.query('DELETE FROM cart_items WHERE customer_id = ? AND variant_id IN (?)', [o.customer_id, orderedVariants]);
    return o;
  });
  if (changed) notifyOrderStatus(changed, 'placed');
  return changed;
}

/** Release stock for an unpaid order and cancel it. */
async function releaseOrder(orderId, reason) {
  await db.tx(async (q) => {
    const o = await q.one('SELECT * FROM orders WHERE id = ? FOR UPDATE', [orderId]);
    if (!o || o.status !== 'pending_payment') return;
    const items = await q.query('SELECT variant_id, quantity FROM order_items WHERE order_id = ?', [orderId]);
    for (const it of items) if (it.variant_id) await q.query('UPDATE product_variants SET stock = stock + ? WHERE id = ?', [it.quantity, it.variant_id]);
    await q.query("UPDATE orders SET status = 'cancelled', payment_status = 'failed', cancel_reason = ? WHERE id = ?", [reason, orderId]);
    await q.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [orderId, 'cancelled', reason]);
  });
}

/** Cancel an order (customer or admin). Restocks items; refunds through Razorpay if paid. */
async function cancelOrder(orderId, { byCustomerId, reason, refund = true } = {}) {
  const o = await db.one('SELECT * FROM orders WHERE id = ?', [orderId]);
  if (!o || (byCustomerId && o.customer_id !== byCustomerId)) throw notFound('Order not found');
  if (o.status === 'pending_payment') return releaseOrder(orderId, reason || 'Cancelled');
  if (byCustomerId && !CANCELLABLE_BY_CUSTOMER.includes(o.status)) throw badRequest('This order can no longer be cancelled');
  if (['cancelled', 'delivered', 'returned'].includes(o.status)) throw badRequest(`Order is already ${o.status}`);

  await db.tx(async (q) => {
    const items = await q.query('SELECT variant_id, product_id, quantity FROM order_items WHERE order_id = ?', [orderId]);
    for (const it of items) {
      if (it.variant_id) await q.query('UPDATE product_variants SET stock = stock + ? WHERE id = ?', [it.quantity, it.variant_id]);
      if (it.product_id) await q.query('UPDATE products SET sold_count = GREATEST(sold_count - ?, 0) WHERE id = ?', [it.quantity, it.product_id]);
    }
    await q.query("UPDATE orders SET status = 'cancelled', cancel_reason = ? WHERE id = ?", [reason || null, orderId]);
    await q.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [orderId, 'cancelled', reason || null]);
  });

  if (refund && o.payment_status === 'paid' && o.razorpay_payment_id) {
    await refundOrder(orderId, Number(o.total));
  }
  notifyOrderStatus(o, 'cancelled');
}

async function refundOrder(orderId, amount) {
  const o = await db.one('SELECT * FROM orders WHERE id = ?', [orderId]);
  if (!o || o.payment_status === 'pending' || o.payment_status === 'failed') throw badRequest('Order has no captured payment');
  const remaining = round2(Number(o.total) - Number(o.refund_amount));
  amount = round2(Math.min(amount, remaining));
  if (amount <= 0) throw badRequest('Nothing left to refund');
  if (o.razorpay_payment_id) await razorpay.refund(o.razorpay_payment_id, amount);
  const newRefunded = round2(Number(o.refund_amount) + amount);
  const status = newRefunded >= Number(o.total) ? 'refunded' : 'partially_refunded';
  await db.query('UPDATE orders SET refund_amount = ?, payment_status = ? WHERE id = ?', [newRefunded, status, orderId]);
  await db.query('INSERT INTO order_status_history (order_id, status, note) VALUES (?, ?, ?)', [orderId, status, `Refunded ₹${amount}`]);
  return { refunded: amount, total_refunded: newRefunded, payment_status: status };
}

/** Release stock held by unpaid orders older than the reservation window. Returns how many were released. */
async function sweepStaleReservations() {
  const stale = await db.query(
    "SELECT id FROM orders WHERE status = 'pending_payment' AND created_at < (NOW() - INTERVAL ? MINUTE) LIMIT 200",
    [RESERVATION_MINUTES]
  );
  for (const o of stale) await releaseOrder(o.id, 'Payment not completed');
  return stale.length;
}

/** Long-running servers: sweep every 5 minutes. (On Vercel a cron route + lazy sweeps do this instead.) */
function startReservationSweeper() {
  const t = setInterval(() => sweepStaleReservations().catch((e) => console.warn('[sweeper]', e.message)), 5 * 60 * 1000);
  t.unref();
}

module.exports = { quote, placeOrder, markPaid, releaseOrder, cancelOrder, refundOrder, evaluateCoupon, sweepStaleReservations, startReservationSweeper, CANCELLABLE_BY_CUSTOMER };
