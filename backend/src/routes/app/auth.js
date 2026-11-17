const router = require('express').Router();
const rateLimit = require('express-rate-limit');
const { z } = require('zod');
const db = require('../../config/db');
const config = require('../../config');
const { ah, validate } = require('../../utils/http');
const { sendOtp, verifyOtp } = require('../../utils/otp');
const { sign, customerAuth } = require('../../middleware/auth');

const mobile = z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number');
const otpLimiter = rateLimit({ windowMs: 10 * 60 * 1000, limit: 20, standardHeaders: true, legacyHeaders: false });

router.post('/otp/send', otpLimiter, validate(z.object({ mobile })), ah(async (req, res) => {
  await sendOtp(req.body.mobile);
  res.json({ message: 'OTP sent', ttl_minutes: config.otp.ttlMinutes });
}));

router.post('/otp/verify', otpLimiter, validate(z.object({ mobile, otp: z.string().regex(/^\d{6}$/, 'OTP must be 6 digits'), fcm_token: z.string().optional() })), ah(async (req, res) => {
  const { mobile: m, otp, fcm_token } = req.body;
  await verifyOtp(m, otp);
  let c = await db.one('SELECT * FROM customers WHERE mobile = ?', [m]);
  let isNew = false;
  if (!c) {
    const r = await db.query('INSERT INTO customers (mobile) VALUES (?)', [m]);
    c = await db.one('SELECT * FROM customers WHERE id = ?', [r.insertId]);
    isNew = true;
  }
  if (!c.is_active) return res.status(403).json({ message: 'Your account has been disabled. Please contact support.' });
  if (fcm_token) await db.query('UPDATE customers SET fcm_token = ? WHERE id = ?', [fcm_token, c.id]);
  const token = sign({ sub: c.id, typ: 'customer' }, config.jwt.expiresIn);
  res.json({ token, is_new: isNew || !c.name, customer: { id: c.id, mobile: c.mobile, name: c.name, email: c.email } });
}));

router.post('/logout', customerAuth, ah(async (req, res) => {
  await db.query('UPDATE customers SET fcm_token = NULL WHERE id = ?', [req.customer.id]);
  res.json({ message: 'Logged out' });
}));

module.exports = router;
