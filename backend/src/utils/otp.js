const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const config = require('../config');
const db = require('../config/db');
const { badRequest } = require('./http');

const MAX_ATTEMPTS = 5;

async function deliver(mobile, code) {
  const p = config.otp.provider;
  if (p === 'msg91') {
    const url = `https://control.msg91.com/api/v5/otp?template_id=${config.otp.msg91TemplateId}&mobile=91${mobile}&otp=${code}`;
    const r = await fetch(url, { method: 'POST', headers: { authkey: config.otp.msg91AuthKey } });
    if (!r.ok) throw new Error(`MSG91 error ${r.status}`);
    return;
  }
  if (p === 'twilio') {
    const body = new URLSearchParams({ To: `+91${mobile}`, From: config.otp.twilioFrom, Body: `Your verification code is ${code}` });
    const auth = Buffer.from(`${config.otp.twilioSid}:${config.otp.twilioToken}`).toString('base64');
    const r = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${config.otp.twilioSid}/Messages.json`, {
      method: 'POST', headers: { Authorization: `Basic ${auth}` }, body,
    });
    if (!r.ok) throw new Error(`Twilio error ${r.status}`);
    return;
  }
  // Development: print to console
  console.log(`[OTP] ${mobile} -> ${code}`);
}

async function sendOtp(mobile) {
  const recent = await db.one(
    'SELECT COUNT(*) AS n FROM otp_codes WHERE mobile = ? AND created_at > (NOW() - INTERVAL 10 MINUTE)',
    [mobile]
  );
  if (recent.n >= 5) throw badRequest('Too many OTP requests. Please try again in a few minutes.');
  const code = config.otp.provider === 'console' && config.demoMode ? '123456' : String(crypto.randomInt(100000, 1000000));
  const hash = await bcrypt.hash(code, 8);
  await db.query('UPDATE otp_codes SET consumed = 1 WHERE mobile = ? AND consumed = 0', [mobile]);
  await db.query(
    'INSERT INTO otp_codes (mobile, code_hash, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))',
    [mobile, hash, config.otp.ttlMinutes]
  );
  await deliver(mobile, code);
}

async function verifyOtp(mobile, code) {
  const row = await db.one(
    'SELECT * FROM otp_codes WHERE mobile = ? AND consumed = 0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1',
    [mobile]
  );
  if (!row) throw badRequest('OTP expired. Please request a new one.');
  if (row.attempts >= MAX_ATTEMPTS) throw badRequest('Too many wrong attempts. Please request a new OTP.');
  const ok = await bcrypt.compare(String(code), row.code_hash);
  if (!ok) {
    await db.query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id = ?', [row.id]);
    throw badRequest('Incorrect OTP');
  }
  await db.query('UPDATE otp_codes SET consumed = 1 WHERE id = ?', [row.id]);
}

module.exports = { sendOtp, verifyOtp };
