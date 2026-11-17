const router = require('express').Router();
const express = require('express');
const db = require('../config/db');
const razorpay = require('../utils/razorpay');
const orders = require('../services/orders');

/**
 * Razorpay webhook (configure in Razorpay Dashboard → Webhooks, events: payment.captured, payment.failed).
 * Acts as a safety net when the app closes before verify-payment is called.
 */
router.post('/razorpay', express.raw({ type: 'application/json' }), async (req, res) => {
  try {
    if (!razorpay.verifyWebhook(req.body, req.headers['x-razorpay-signature'])) return res.status(400).send('bad signature');
    const evt = JSON.parse(req.body.toString('utf8'));
    const p = evt.payload && evt.payload.payment && evt.payload.payment.entity;
    if (!p) return res.json({ ok: true });
    const o = await db.one('SELECT id FROM orders WHERE razorpay_order_id = ?', [p.order_id]);
    if (!o) return res.json({ ok: true });
    if (evt.event === 'payment.captured') await orders.markPaid(o.id, p.id, p);
    res.json({ ok: true });
  } catch (e) {
    console.error('[webhook]', e);
    res.status(500).json({ ok: false });
  }
});

module.exports = router;
