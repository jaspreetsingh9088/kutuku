/**
 * Ordered, idempotent schema migrations. Each runs once; the applied version is stored in settings.schema_version.
 * Works on MySQL 8, MariaDB and TiDB (only standard ALTER/CREATE statements, guarded by information_schema checks).
 */
const db = require('../config/db');

async function hasColumn(table, column) {
  const r = await db.one('SELECT COUNT(*) AS n FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?', [table, column]);
  return r.n > 0;
}
async function hasIndex(table, index) {
  const r = await db.one('SELECT COUNT(*) AS n FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?', [table, index]);
  return r.n > 0;
}
async function addColumn(table, column, ddl) {
  if (!(await hasColumn(table, column))) await db.query(`ALTER TABLE ${table} ADD COLUMN ${column} ${ddl}`);
}
async function addIndex(table, index, ddl) {
  if (!(await hasIndex(table, index))) await db.query(`ALTER TABLE ${table} ADD ${ddl}`);
}

const MIGRATIONS = [
  // 1 — Figma features: password accounts, stores, colours, reviews, notifications, chat, content
  async () => {
    // Customers: password login, avatar, notification preferences; mobile becomes optional (email sign-up)
    await db.query('ALTER TABLE customers MODIFY mobile VARCHAR(15) NULL');
    await addColumn('customers', 'password_hash', 'VARCHAR(255) NULL');
    await addColumn('customers', 'avatar_url', 'VARCHAR(512) NULL');
    await addColumn('customers', 'notif_payment', 'TINYINT(1) NOT NULL DEFAULT 1');
    await addColumn('customers', 'notif_tracking', 'TINYINT(1) NOT NULL DEFAULT 1');
    await addColumn('customers', 'notif_complete', 'TINYINT(1) NOT NULL DEFAULT 1');
    await addColumn('customers', 'notif_general', 'TINYINT(1) NOT NULL DEFAULT 1');
    await addIndex('customers', 'uq_customers_email', 'UNIQUE KEY uq_customers_email (email)');

    // OTP codes can target an email or a phone, for different purposes
    await db.query('ALTER TABLE otp_codes MODIFY mobile VARCHAR(190) NOT NULL');
    await addColumn('otp_codes', 'purpose', "VARCHAR(20) NOT NULL DEFAULT 'login'");

    await db.query(`CREATE TABLE IF NOT EXISTS stores (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      name VARCHAR(150) NOT NULL,
      slug VARCHAR(170) NOT NULL UNIQUE,
      logo_url VARCHAR(512) NULL,
      description TEXT NULL,
      city VARCHAR(100) NULL,
      is_verified TINYINT(1) NOT NULL DEFAULT 0,
      is_active TINYINT(1) NOT NULL DEFAULT 1,
      display_order INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS store_followers (
      store_id INT UNSIGNED NOT NULL,
      customer_id INT UNSIGNED NOT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (store_id, customer_id),
      INDEX idx_follow_customer (customer_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await addColumn('products', 'store_id', 'INT UNSIGNED NULL');
    await addIndex('products', 'idx_prod_store', 'INDEX idx_prod_store (store_id)');
    await addColumn('products', 'rating_avg', 'DECIMAL(3,2) NOT NULL DEFAULT 0');
    await addColumn('products', 'rating_count', 'INT UNSIGNED NOT NULL DEFAULT 0');
    await addColumn('banners', 'store_id', 'INT UNSIGNED NULL');

    await addColumn('product_variants', 'color', 'VARCHAR(40) NULL');
    await addColumn('product_variants', 'color_hex', 'VARCHAR(9) NULL');

    await db.query(`CREATE TABLE IF NOT EXISTS reviews (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      product_id INT UNSIGNED NOT NULL,
      customer_id INT UNSIGNED NOT NULL,
      order_id INT UNSIGNED NULL,
      rating TINYINT UNSIGNED NOT NULL,
      comment TEXT NULL,
      is_approved TINYINT(1) NOT NULL DEFAULT 1,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE KEY uq_review (product_id, customer_id),
      INDEX idx_review_product (product_id, is_approved)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    await db.query(`CREATE TABLE IF NOT EXISTS notifications (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      customer_id INT UNSIGNED NOT NULL,
      type VARCHAR(30) NOT NULL DEFAULT 'general',
      title VARCHAR(150) NOT NULL,
      body VARCHAR(500) NULL,
      image_url VARCHAR(512) NULL,
      data JSON NULL,
      is_read TINYINT(1) NOT NULL DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_notif_customer (customer_id, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    await db.query(`CREATE TABLE IF NOT EXISTS conversations (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      customer_id INT UNSIGNED NOT NULL,
      store_id INT UNSIGNED NOT NULL DEFAULT 0,
      last_message VARCHAR(500) NULL,
      last_message_at TIMESTAMP NULL,
      customer_unread INT UNSIGNED NOT NULL DEFAULT 0,
      admin_unread INT UNSIGNED NOT NULL DEFAULT 0,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE KEY uq_conv (customer_id, store_id),
      INDEX idx_conv_last (last_message_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS messages (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      conversation_id INT UNSIGNED NOT NULL,
      sender ENUM('customer','store') NOT NULL,
      body TEXT NULL,
      product_id INT UNSIGNED NULL,
      order_id INT UNSIGNED NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_msg_conv (conversation_id, id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    await db.query(`CREATE TABLE IF NOT EXISTS onboarding_slides (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      title VARCHAR(150) NOT NULL,
      subtitle VARCHAR(300) NULL,
      image_url VARCHAR(512) NOT NULL,
      display_order INT NOT NULL DEFAULT 0,
      is_active TINYINT(1) NOT NULL DEFAULT 1
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS faqs (
      id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      question VARCHAR(255) NOT NULL,
      answer TEXT NOT NULL,
      display_order INT NOT NULL DEFAULT 0,
      is_active TINYINT(1) NOT NULL DEFAULT 1
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS pages (
      slug VARCHAR(60) PRIMARY KEY,
      title VARCHAR(150) NOT NULL,
      content MEDIUMTEXT NOT NULL,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS search_terms (
      term VARCHAR(100) PRIMARY KEY,
      total_count INT UNSIGNED NOT NULL DEFAULT 0,
      day_count INT UNSIGNED NOT NULL DEFAULT 0,
      day DATE NULL,
      last_searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    await addColumn('cart_items', 'selected', 'TINYINT(1) NOT NULL DEFAULT 1');
    await addColumn('orders', 'courier_name', 'VARCHAR(120) NULL');
    await addColumn('orders', 'courier_phone', 'VARCHAR(20) NULL');
    await addColumn('orders', 'tracking_url', 'VARCHAR(512) NULL');
    await addColumn('orders', 'preferred_method', 'VARCHAR(20) NULL');
    await addColumn('order_items', 'variant_color', 'VARCHAR(40) NULL');
  },
];

/** Run pending migrations. Safe to call on every cold start (fast when up to date). */
async function migrate() {
  const row = await db.one("SELECT `value` FROM settings WHERE `key` = 'schema_version'");
  let version = row ? Number(row.value) : 0;
  for (let i = version; i < MIGRATIONS.length; i++) {
    console.log(`[migrate] applying #${i + 1}`);
    await MIGRATIONS[i]();
    version = i + 1;
    await db.query("INSERT INTO settings (`key`, `value`) VALUES ('schema_version', ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)", [String(version)]);
  }
  return version;
}

module.exports = { migrate, MIGRATIONS };
