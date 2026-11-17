const router = require('express').Router();
const rateLimit = require('express-rate-limit');
const bcrypt = require('bcryptjs');
const { z } = require('zod');
const db = require('../../config/db');
const config = require('../../config');
const { ah, validate, unauthorized, badRequest, notFound } = require('../../utils/http');
const { sign, adminAuth } = require('../../middleware/auth');

const loginLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 20 });

router.post('/login', loginLimiter, validate(z.object({ email: z.string().email(), password: z.string().min(1) })), ah(async (req, res) => {
  const a = await db.one('SELECT * FROM admins WHERE email = ?', [req.body.email.toLowerCase()]);
  if (!a || !a.is_active || !(await bcrypt.compare(req.body.password, a.password_hash))) throw unauthorized('Invalid email or password');
  res.json({ token: sign({ sub: a.id, typ: 'admin' }, config.jwt.adminExpiresIn), admin: { id: a.id, name: a.name, email: a.email, role: a.role } });
}));

router.get('/me', adminAuth(), (req, res) => res.json(req.admin));

router.post('/change-password', adminAuth(), validate(z.object({ current: z.string(), next: z.string().min(8) })), ah(async (req, res) => {
  const a = await db.one('SELECT password_hash FROM admins WHERE id = ?', [req.admin.id]);
  if (!(await bcrypt.compare(req.body.current, a.password_hash))) throw badRequest('Current password is incorrect');
  await db.query('UPDATE admins SET password_hash = ? WHERE id = ?', [await bcrypt.hash(req.body.next, 10), req.admin.id]);
  res.json({ ok: true });
}));

// ---- Staff accounts (superadmin only) ----
router.get('/users', adminAuth('superadmin'), ah(async (_req, res) => {
  res.json(await db.query('SELECT id, name, email, role, is_active, created_at FROM admins ORDER BY id'));
}));

router.post('/users', adminAuth('superadmin'), validate(z.object({
  name: z.string().min(2), email: z.string().email(), password: z.string().min(8), role: z.enum(['superadmin', 'manager', 'staff']),
})), ah(async (req, res) => {
  const { name, email, password, role } = req.body;
  if (await db.one('SELECT id FROM admins WHERE email = ?', [email.toLowerCase()])) throw badRequest('Email already exists');
  const r = await db.query('INSERT INTO admins (name, email, password_hash, role) VALUES (?, ?, ?, ?)', [name, email.toLowerCase(), await bcrypt.hash(password, 10), role]);
  res.status(201).json({ id: r.insertId });
}));

router.patch('/users/:id', adminAuth('superadmin'), validate(z.object({ is_active: z.boolean().optional(), role: z.enum(['superadmin', 'manager', 'staff']).optional() })), ah(async (req, res) => {
  if (Number(req.params.id) === req.admin.id) throw badRequest('You cannot modify your own account here');
  const a = await db.one('SELECT id FROM admins WHERE id = ?', [req.params.id]);
  if (!a) throw notFound();
  if (req.body.is_active !== undefined) await db.query('UPDATE admins SET is_active = ? WHERE id = ?', [req.body.is_active ? 1 : 0, a.id]);
  if (req.body.role) await db.query('UPDATE admins SET role = ? WHERE id = ?', [req.body.role, a.id]);
  res.json({ ok: true });
}));

module.exports = router;
