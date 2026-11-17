const fs = require('fs');
const config = require('../config');
const db = require('../config/db');

let messaging = null;
try {
  if (config.firebaseServiceAccount && fs.existsSync(config.firebaseServiceAccount)) {
    const admin = require('firebase-admin');
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(config.firebaseServiceAccount, 'utf8'))) });
    messaging = admin.messaging();
    console.log('[FCM] enabled');
  } else if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    // Hosted environments (e.g. Vercel) can pass the service-account JSON as an env var
    const admin = require('firebase-admin');
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON)) });
    messaging = admin.messaging();
    console.log('[FCM] enabled');
  }
} catch (e) {
  console.warn('[FCM] disabled:', e.message);
}

const strData = (data) => Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]));

/** Which customer preference controls each notification type (Notifications settings screen). */
const PREF = { payment: 'notif_payment', order: 'notif_tracking', shipping: 'notif_tracking', complete: 'notif_complete', message: 'notif_general', promo: 'notif_general', general: 'notif_general' };

/**
 * Save a notification to the customer's inbox and push it to their phone.
 * Never throws — notifications must not break order flow.
 */
async function notifyCustomer(customerId, title, body, data = {}, type = 'general') {
  try {
    const c = await db.one('SELECT * FROM customers WHERE id = ?', [customerId]);
    if (!c) return;
    const pref = PREF[type] || 'notif_general';
    if (c[pref] === 0) return;
    await db.query('INSERT INTO notifications (customer_id, type, title, body, data) VALUES (?,?,?,?,?)',
      [customerId, type, title, body, JSON.stringify(data)]);
    if (messaging && c.fcm_token) {
      await messaging.send({ token: c.fcm_token, notification: { title, body }, data: strData({ ...data, type }), android: { priority: 'high' } });
    }
  } catch (e) {
    console.warn('[notify] failed:', e.message);
  }
}

/** Promotional broadcast: inbox entry for every customer who allows it, plus a push to the "promotions" topic. */
async function broadcast(title, body, data = {}) {
  await db.query(
    "INSERT INTO notifications (customer_id, type, title, body, data) SELECT id, 'promo', ?, ?, ? FROM customers WHERE is_active = 1 AND notif_general = 1",
    [title, body, JSON.stringify(data)]
  );
  if (messaging) await messaging.send({ topic: 'promotions', notification: { title, body }, data: strData({ ...data, type: 'promo' }) });
  return { sent: true, push: !!messaging };
}

const STATUS_MESSAGES = {
  placed: ['Purchase Completed!', 'Your order #{n} has been placed successfully. Thank you and wait for your package to arrive ✨', 'payment'],
  confirmed: ['Order Confirmed', 'Your order #{n} has been confirmed by the store.', 'order'],
  packed: ['Order Packed', 'Your order #{n} is packed and ready to ship.', 'order'],
  shipped: ['Package Sent', 'Your order #{n} has been sent and is on its way.', 'shipping'],
  out_for_delivery: ['Out for Delivery', 'Your order #{n} will reach you today.', 'shipping'],
  delivered: ['Order Delivered', 'Your order #{n} has been delivered. Thank you for shopping!', 'complete'],
  cancelled: ['Order Cancelled', 'Your order #{n} has been cancelled.', 'order'],
};

function notifyOrderStatus(order, status) {
  const m = STATUS_MESSAGES[status];
  if (!m) return;
  return notifyCustomer(order.customer_id, m[0], m[1].replace('{n}', order.order_number), { order_id: order.id }, m[2]);
}

module.exports = { notifyCustomer, notifyOrderStatus, broadcast, isEnabled: () => !!messaging };
