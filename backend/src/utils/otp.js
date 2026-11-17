const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const config = require('../config');
const db = require('../config/db');
const { badRequest } = require('./http');

const MAX_ATTEMPTS = 5;

/** "email" for addresses with @, otherwise "phone" (10-digit Indian mobile). */
const kindOf = (identifier) => (String(identifier).includes('@') ? 'email' : 'phone');

let mailer = null;
function getMailer() {
  if (mailer !== null) return mailer;
  if (!process.env.SMTP_HOST) { mailer = false; return mailer; }
  const nodemailer = require('nodemailer');
  mailer = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 587),
    secure: process.env.SMTP_SECURE === '1',
    auth: process.env.SMTP_USER ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS } : undefined,
  });
  return mailer;
}

const SUBJECTS = { login: 'Your login code', register: 'Verify your account', reset: 'Reset your password' };

async function deliver(identifier, code, purpose) {
  if (kindOf(identifier) === 'email') {
    const m = getMailer();
    if (m) {
      await m.sendMail({
        from: process.env.SMTP_FROM || config.store.email,
        to: identifier,
        subject: `${SUBJECTS[purpose] || 'Verification code'} — ${config.store.name}`,
        text: `Your verification code is ${code}. It expires in ${config.otp.ttlMinutes} minutes.`,
      });
      return;
    }
    console.log(`[OTP:${purpose}] ${identifier} -> ${code} (set SMTP_HOST to send emails)`);
    return;
  }
  const p = config.otp.provider;
  if (p === 'msg91') {
    const url = `https://control.msg91.com/api/v5/otp?template_id=${config.otp.msg91TemplateId}&mobile=91${identifier}&otp=${code}`;
    const r = await fetch(url, { method: 'POST', headers: { authkey: config.otp.msg91AuthKey } });
    if (!r.ok) throw new Error(`MSG91 error ${r.status}`);
    return;
  }
  if (p === 'twilio') {
    const body = new URLSearchParams({ To: `+91${identifier}`, From: config.otp.twilioFrom, Body: `Your verification code is ${code}` });
    const auth = Buffer.from(`${config.otp.twilioSid}:${config.otp.twilioToken}`).toString('base64');
    const r = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${config.otp.twilioSid}/Messages.json`, {
      method: 'POST', headers: { Authorization: `Basic ${auth}` }, body,
    });
    if (!r.ok) throw new Error(`Twilio error ${r.status}`);
    return;
  }
  console.log(`[OTP:${purpose}] ${identifier} -> ${code}`);
}

/** Send a 6-digit code to a phone or email for a purpose: login | register | reset. */
async function sendOtp(identifier, purpose = 'login') {
  const recent = await db.one(
    'SELECT COUNT(*) AS n FROM otp_codes WHERE mobile = ? AND created_at > (NOW() - INTERVAL 10 MINUTE)',
    [identifier]
  );
  if (recent.n >= 5) throw badRequest('Too many code requests. Please try again in a few minutes.');
  const code = config.otp.provider === 'console' && config.demoMode ? '123456' : String(crypto.randomInt(100000, 1000000));
  const hash = await bcrypt.hash(code, 8);
  await db.query('UPDATE otp_codes SET consumed = 1 WHERE mobile = ? AND purpose = ? AND consumed = 0', [identifier, purpose]);
  await db.query(
    'INSERT INTO otp_codes (mobile, code_hash, purpose, expires_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))',
    [identifier, hash, purpose, config.otp.ttlMinutes]
  );
  await deliver(identifier, code, purpose);
}

async function verifyOtp(identifier, code, purpose = 'login') {
  const row = await db.one(
    'SELECT * FROM otp_codes WHERE mobile = ? AND purpose = ? AND consumed = 0 AND expires_at > NOW() ORDER BY id DESC LIMIT 1',
    [identifier, purpose]
  );
  if (!row) throw badRequest('Code expired. Please request a new one.');
  if (row.attempts >= MAX_ATTEMPTS) throw badRequest('Too many wrong attempts. Please request a new code.');
  const ok = await bcrypt.compare(String(code), row.code_hash);
  if (!ok) {
    await db.query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id = ?', [row.id]);
    throw badRequest('Incorrect code');
  }
  await db.query('UPDATE otp_codes SET consumed = 1 WHERE id = ?', [row.id]);
}

module.exports = { sendOtp, verifyOtp, kindOf };
