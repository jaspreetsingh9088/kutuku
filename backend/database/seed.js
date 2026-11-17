// Creates the first admin user. Pass --demo to also add sample categories, products, a banner and a coupon.
require('dotenv').config();
const db = require('../src/config/db');
const { seedAdmin, seedDemo } = require('../src/services/setup');

(async () => {
  const created = await seedAdmin();
  if (created) console.log(`Admin created: ${created.email} / ${created.pass}  (change this password after first login)`);
  else console.log('Admin already exists');
  if (process.argv.includes('--demo')) {
    await seedDemo();
    console.log('Demo data added');
  }
  await db.pool.end();
})().catch((e) => { console.error(e); process.exit(1); });
