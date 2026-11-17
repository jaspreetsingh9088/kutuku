const jwt = require('jsonwebtoken');
const config = require('../config');
const db = require('../config/db');
const { unauthorized, forbidden } = require('../utils/http');

const sign = (payload, expiresIn) => jwt.sign(payload, config.jwt.secret, { expiresIn });

function readToken(req) {
  const h = req.headers.authorization || '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}

/** Customer auth (Android app). */
async function customerAuth(req, _res, next) {
  try {
    const token = readToken(req);
    if (!token) throw unauthorized();
    const p = jwt.verify(token, config.jwt.secret);
    if (p.typ !== 'customer') throw unauthorized();
    const c = await db.one('SELECT id, mobile, name, email, is_active FROM customers WHERE id = ?', [p.sub]);
    if (!c) throw unauthorized();
    if (!c.is_active) throw forbidden('Your account has been disabled. Please contact support.');
    req.customer = c;
    next();
  } catch (e) {
    next(e.status ? e : unauthorized('Session expired, please log in again'));
  }
}

/** Optional customer auth: attaches req.customer if a valid token is present. */
async function optionalCustomer(req, _res, next) {
  const token = readToken(req);
  if (!token) return next();
  try {
    const p = jwt.verify(token, config.jwt.secret);
    if (p.typ === 'customer') req.customer = { id: p.sub };
  } catch (_) { /* ignore */ }
  next();
}

/** Admin auth (web panel). Optional role list restricts access further. */
const adminAuth = (...roles) => async (req, _res, next) => {
  try {
    const token = readToken(req);
    if (!token) throw unauthorized();
    const p = jwt.verify(token, config.jwt.secret);
    if (p.typ !== 'admin') throw unauthorized();
    const a = await db.one('SELECT id, name, email, role, is_active FROM admins WHERE id = ?', [p.sub]);
    if (!a || !a.is_active) throw unauthorized();
    if (roles.length && !roles.includes(a.role)) throw forbidden();
    req.admin = a;
    next();
  } catch (e) {
    next(e.status ? e : unauthorized('Session expired, please log in again'));
  }
};

module.exports = { sign, customerAuth, optionalCustomer, adminAuth };
