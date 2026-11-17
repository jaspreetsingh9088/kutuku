const router = require('express').Router();
const rateLimit = require('express-rate-limit');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { z } = require('zod');
const db = require('../../config/db');
const config = require('../../config');
const { ah, validate, badRequest, unauthorized } = require('../../utils/http');
const { sendOtp, verifyOtp, kindOf } = require('../../utils/otp');
const { sign, customerAuth } = require('../../middleware/auth');

const limiter = rateLimit({ windowMs: 10 * 60 * 1000, limit: 30, standardHeaders: true, legacyHeaders: false });

const mobile = z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number');
/** Email or 10-digit phone ("Email or Phone Number" field in the design). */
const identifier = z.string().trim().toLowerCase().refine(
  (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) || /^[6-9]\d{9}$/.test(v),
  'Enter a valid email or 10-digit phone number'
);
const password = z.string().min(8, 'Password must be at least 8 characters').max(100);

const findByIdentifier = (id) =>
  db.one(kindOf(id) === 'email' ? 'SELECT * FROM customers WHERE email = ?' : 'SELECT * FROM customers WHERE mobile = ?', [id]);

async function issue(res, c, extra = {}) {
  if (!c.is_active) return res.status(403).json({ message: 'Your account has been disabled. Please contact support.' });
  const token = sign({ sub: c.id, typ: 'customer' }, config.jwt.expiresIn);
  res.json({
    token, is_new: !c.name, ...extra,
    customer: { id: c.id, mobile: c.mobile, name: c.name, email: c.email, avatar_url: c.avatar_url },
  });
}

async function saveFcm(customerId, token) {
  if (token) await db.query('UPDATE customers SET fcm_token = ? WHERE id = ?', [token, customerId]);
}

// ---------- OTP login (passwordless) ----------
router.post('/otp/send', limiter, validate(z.object({ mobile: identifier })), ah(async (req, res) => {
  await sendOtp(req.body.mobile, 'login');
  res.json({ message: 'OTP sent', ttl_minutes: config.otp.ttlMinutes });
}));

router.post('/otp/verify', limiter, validate(z.object({ mobile: identifier, otp: z.string().regex(/^\d{6}$/, 'OTP must be 6 digits'), fcm_token: z.string().optional() })), ah(async (req, res) => {
  const { mobile: id, otp, fcm_token } = req.body;
  await verifyOtp(id, otp, 'login');
  let c = await findByIdentifier(id);
  if (!c) {
    const r = await db.query(kindOf(id) === 'email' ? 'INSERT INTO customers (email) VALUES (?)' : 'INSERT INTO customers (mobile) VALUES (?)', [id]);
    c = await db.one('SELECT * FROM customers WHERE id = ?', [r.insertId]);
  }
  await saveFcm(c.id, fcm_token);
  await issue(res, c);
}));

// ---------- Create Account: send code, then verify + create ----------
const registerSchema = z.object({ name: z.string().trim().min(2, 'Enter your name').max(120), identifier, password });

router.post('/register', limiter, validate(registerSchema), ah(async (req, res) => {
  if (await findByIdentifier(req.body.identifier)) throw badRequest('An account with this email or phone already exists. Please log in.');
  await sendOtp(req.body.identifier, 'register');
  res.json({ message: 'Verification code sent', ttl_minutes: config.otp.ttlMinutes });
}));

router.post('/register/verify', limiter, validate(registerSchema.extend({ otp: z.string().regex(/^\d{6}$/), fcm_token: z.string().optional() })), ah(async (req, res) => {
  const { name, identifier: id, password: pw, otp, fcm_token } = req.body;
  if (await findByIdentifier(id)) throw badRequest('An account with this email or phone already exists. Please log in.');
  await verifyOtp(id, otp, 'register');
  const hash = await bcrypt.hash(pw, 10);
  const r = await db.query(
    kindOf(id) === 'email' ? 'INSERT INTO customers (name, email, password_hash) VALUES (?,?,?)' : 'INSERT INTO customers (name, mobile, password_hash) VALUES (?,?,?)',
    [name, id, hash]
  );
  const c = await db.one('SELECT * FROM customers WHERE id = ?', [r.insertId]);
  await saveFcm(c.id, fcm_token);
  await issue(res, c);
}));

// ---------- Login Account (email or phone + password) ----------
router.post('/login', limiter, validate(z.object({ identifier, password: z.string().min(1), fcm_token: z.string().optional() })), ah(async (req, res) => {
  const c = await findByIdentifier(req.body.identifier);
  if (!c || !c.password_hash || !(await bcrypt.compare(req.body.password, c.password_hash))) {
    throw unauthorized(c && !c.password_hash ? 'This account uses one-time codes. Tap "Login with OTP" or reset your password.' : 'Incorrect email/phone or password');
  }
  await saveFcm(c.id, req.body.fcm_token);
  await issue(res, c);
}));

// ---------- Forgot Password → code → Create New Password ----------
router.post('/password/forgot', limiter, validate(z.object({ identifier })), ah(async (req, res) => {
  const c = await findByIdentifier(req.body.identifier);
  // Same response either way so the endpoint can't be used to discover accounts
  if (c && c.is_active) await sendOtp(req.body.identifier, 'reset');
  res.json({ message: 'If an account exists, a code has been sent', ttl_minutes: config.otp.ttlMinutes });
}));

router.post('/password/verify-code', limiter, validate(z.object({ identifier, otp: z.string().regex(/^\d{6}$/) })), ah(async (req, res) => {
  const c = await findByIdentifier(req.body.identifier);
  if (!c) throw badRequest('Incorrect code');
  await verifyOtp(req.body.identifier, req.body.otp, 'reset');
  res.json({ reset_token: sign({ sub: c.id, typ: 'reset' }, '15m') });
}));

router.post('/password/reset', limiter, validate(z.object({ reset_token: z.string(), password, fcm_token: z.string().optional() })), ah(async (req, res) => {
  let p;
  try { p = jwt.verify(req.body.reset_token, config.jwt.secret); } catch (_) { throw badRequest('This reset link has expired. Please start again.'); }
  if (p.typ !== 'reset') throw badRequest('Invalid reset request');
  await db.query('UPDATE customers SET password_hash = ? WHERE id = ?', [await bcrypt.hash(req.body.password, 10), p.sub]);
  const c = await db.one('SELECT * FROM customers WHERE id = ?', [p.sub]);
  await saveFcm(c.id, req.body.fcm_token);
  await issue(res, c);
}));

router.post('/logout', customerAuth, ah(async (req, res) => {
  await db.query('UPDATE customers SET fcm_token = NULL WHERE id = ?', [req.customer.id]);
  res.json({ message: 'Logged out' });
}));

module.exports = router;
module.exports.schemas = { mobile, identifier, password };
