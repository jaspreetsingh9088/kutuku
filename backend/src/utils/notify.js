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
  }
} catch (e) {
  console.warn('[FCM] disabled:', e.message);
}

/** Send a push to one customer. Never throws — notifications must not break order flow. */
async function notifyCustomer(customerId, title, body, data = {}) {
  try {
    if (!messaging) return;
    const c = await db.one('SELECT fcm_token FROM customers WHERE id = ?', [customerId]);
    if (!c || !c.fcm_token) return;
    const strData = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]));
    await messaging.send({ token: c.fcm_token, notification: { title, body }, data: strData, android: { priority: 'high' } });
  } catch (e) {
    console.warn('[FCM] send failed:', e.message);
  }
}

/** Broadcast a promotional notification to a topic all app installs subscribe to. */
async function broadcast(title, body, data = {}) {
  if (!messaging) return { sent: false, reason: 'FCM not configured' };
  const strData = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]));
  await messaging.send({ topic: 'promotions', notification: { title, body }, data: strData });
  return { sent: true };
}

const STATUS_MESSAGES = {
  placed: ['Order placed', 'We have received your order #{n}.'],
  confirmed: ['Order confirmed', 'Your order #{n} has been confirmed.'],
  packed: ['Order packed', 'Your order #{n} is packed and ready to ship.'],
  shipped: ['Order dispatched', 'Your order #{n} is on its way.'],
  out_for_delivery: ['Out for delivery', 'Your order #{n} will reach you today.'],
  delivered: ['Order delivered', 'Your order #{n} has been delivered. Thank you for shopping!'],
  cancelled: ['Order cancelled', 'Your order #{n} has been cancelled.'],
};

function notifyOrderStatus(order, status) {
  const m = STATUS_MESSAGES[status];
  if (!m) return;
  return notifyCustomer(order.customer_id, m[0], m[1].replace('{n}', order.order_number), {
    type: 'order', order_id: order.id,
  });
}

module.exports = { notifyCustomer, notifyOrderStatus, broadcast, isEnabled: () => !!messaging };
