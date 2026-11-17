const crypto = require('crypto');
const Razorpay = require('razorpay');
const config = require('../config');

let client = null;
// Dev-only mock: when keys are placeholders, orders/refunds are simulated so the flow can be tested end-to-end.
const isMock = () => config.demoMode && (!config.razorpay.keyId || /x{6,}/.test(config.razorpay.keyId));
const rzp = () => {
  if (!client) client = new Razorpay({ key_id: config.razorpay.keyId, key_secret: config.razorpay.keySecret });
  return client;
};

/** Create a Razorpay order. Amount is in rupees. */
async function createOrder(amountRupees, receipt, notes = {}) {
  if (isMock()) return { id: `order_mock_${crypto.randomBytes(7).toString('hex')}`, amount: Math.round(amountRupees * 100), currency: 'INR', receipt, notes };
  return rzp().orders.create({ amount: Math.round(amountRupees * 100), currency: 'INR', receipt, notes });
}

/** Verify the signature returned by the Checkout SDK on the device. */
function verifyPaymentSignature(orderId, paymentId, signature) {
  const expected = crypto.createHmac('sha256', config.razorpay.keySecret).update(`${orderId}|${paymentId}`).digest('hex');
  return safeEqual(expected, signature);
}

/** Verify a webhook payload signature. */
function verifyWebhook(rawBody, signature) {
  if (!config.razorpay.webhookSecret) return false;
  const expected = crypto.createHmac('sha256', config.razorpay.webhookSecret).update(rawBody).digest('hex');
  return safeEqual(expected, signature || '');
}

function safeEqual(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

async function fetchPayment(paymentId) {
  return rzp().payments.fetch(paymentId);
}

async function refund(paymentId, amountRupees) {
  if (isMock()) return { id: `rfnd_mock_${Date.now()}`, amount: Math.round(amountRupees * 100) };
  return rzp().payments.refund(paymentId, { amount: Math.round(amountRupees * 100) });
}

module.exports = { isMock, createOrder, verifyPaymentSignature, verifyWebhook, fetchPayment, refund };
