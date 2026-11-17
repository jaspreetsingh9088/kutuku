require('dotenv').config();

const num = (v, d) => (v === undefined || v === '' ? d : Number(v));

const env = process.env.NODE_ENV || 'development';

module.exports = {
  env,
  // DEMO_MODE=1 allows the fixed OTP 123456 and simulated Razorpay payments even in production.
  // Use it ONLY for a preview deployment; never on the real store.
  demoMode: process.env.DEMO_MODE === '1' || env !== 'production',
  onVercel: !!process.env.VERCEL,
  setupKey: process.env.SETUP_KEY || '',
  cronSecret: process.env.CRON_SECRET || '',
  port: num(process.env.PORT, 4000),
  publicUrl: (process.env.PUBLIC_URL || 'http://localhost:4000').replace(/\/$/, ''),
  corsOrigins: (process.env.CORS_ORIGINS || '*').split(',').map((s) => s.trim()),
  db: {
    // Either DATABASE_URL (mysql://user:pass@host:port/db) or the individual DB_* values
    url: process.env.DATABASE_URL || '',
    ssl: process.env.DB_SSL === '1' || /tidbcloud|aivencloud|psdb\.cloud|ssl-mode=REQUIRED/i.test(process.env.DATABASE_URL || ''),
    host: process.env.DB_HOST || '127.0.0.1',
    port: num(process.env.DB_PORT, 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'shop',
  },
  jwt: {
    secret: process.env.JWT_SECRET || 'dev-secret-change-me',
    expiresIn: process.env.JWT_EXPIRES_IN || '30d',
    adminExpiresIn: process.env.ADMIN_JWT_EXPIRES_IN || '12h',
  },
  otp: {
    provider: process.env.OTP_PROVIDER || 'console',
    ttlMinutes: num(process.env.OTP_TTL_MINUTES, 5),
    msg91AuthKey: process.env.MSG91_AUTH_KEY,
    msg91TemplateId: process.env.MSG91_TEMPLATE_ID,
    twilioSid: process.env.TWILIO_ACCOUNT_SID,
    twilioToken: process.env.TWILIO_AUTH_TOKEN,
    twilioFrom: process.env.TWILIO_FROM,
  },
  razorpay: {
    keyId: process.env.RAZORPAY_KEY_ID || '',
    keySecret: process.env.RAZORPAY_KEY_SECRET || '',
    webhookSecret: process.env.RAZORPAY_WEBHOOK_SECRET || '',
  },
  firebaseServiceAccount: process.env.FIREBASE_SERVICE_ACCOUNT || '',
  storage: {
    // "local" (VPS disk) or "blob" (Vercel Blob — required on Vercel, whose disk is read-only)
    driver: process.env.STORAGE_DRIVER || (process.env.BLOB_READ_WRITE_TOKEN ? 'blob' : 'local'),
    cdnUrl: (process.env.CDN_URL || '').replace(/\/$/, ''),
  },
  store: {
    name: process.env.STORE_NAME || 'My Store',
    address: process.env.STORE_ADDRESS || '',
    gstin: process.env.STORE_GSTIN || '',
    state: process.env.STORE_STATE || 'Punjab',
    phone: process.env.STORE_PHONE || '',
    email: process.env.STORE_EMAIL || '',
  },
  delivery: {
    freeAbove: num(process.env.FREE_DELIVERY_ABOVE, 499),
    charge: num(process.env.DELIVERY_CHARGE, 40),
  },
};
