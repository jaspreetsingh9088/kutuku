const db = require('../config/db');

/** Attach images, variants and store info to product rows (3 queries regardless of list size). */
async function hydrate(products, { activeVariantsOnly = true } = {}) {
  if (!products.length) return products;
  const ids = products.map((p) => p.id);
  const storeIds = [...new Set(products.map((p) => p.store_id).filter(Boolean))];
  const [images, variants, stores] = await Promise.all([
    db.query('SELECT id, product_id, url, sort_order FROM product_images WHERE product_id IN (?) ORDER BY sort_order, id', [ids]),
    db.query(
      `SELECT id, product_id, sku, label, color, color_hex, mrp, price, stock, low_stock_threshold, is_active
       FROM product_variants WHERE product_id IN (?) ${activeVariantsOnly ? 'AND is_active = 1' : ''} ORDER BY price, id`,
      [ids]
    ),
    storeIds.length ? db.query('SELECT id, name, slug, logo_url, is_verified, city FROM stores WHERE id IN (?)', [storeIds]) : [],
  ]);
  const storeById = Object.fromEntries(stores.map((s) => [s.id, { ...s, is_verified: !!s.is_verified }]));
  const byId = Object.fromEntries(products.map((p) => [p.id, { ...p, images: [], variants: [] }]));
  images.forEach((i) => byId[i.product_id].images.push(i));
  variants.forEach((v) => byId[v.product_id].variants.push({ ...v, in_stock: v.stock > 0 }));
  return products.map((p) => {
    const x = byId[p.id];
    if (typeof x.specifications === 'string') {
      try { x.specifications = JSON.parse(x.specifications); } catch (_) { /* keep */ }
    }
    x.image = x.images[0] ? x.images[0].url : null;
    const cheapest = x.variants[0];
    x.price = cheapest ? cheapest.price : null;
    x.mrp = cheapest ? cheapest.mrp : null;
    x.in_stock = x.variants.some((v) => v.stock > 0);
    x.store = x.store_id ? storeById[x.store_id] || null : null;
    x.rating = x.rating_avg !== undefined ? Number(x.rating_avg) : 0;
    return x;
  });
}

/** Record a search term for the "Popular Search" list (counts per day and overall). */
async function recordSearch(term) {
  const t = String(term || '').trim().toLowerCase().slice(0, 100);
  if (t.length < 2) return;
  try {
    await db.query(
      `INSERT INTO search_terms (term, total_count, day_count, day) VALUES (?, 1, 1, CURDATE())
       ON DUPLICATE KEY UPDATE total_count = total_count + 1,
         day_count = IF(day = CURDATE(), day_count + 1, 1), day = CURDATE(), last_searched_at = NOW()`,
      [t]
    );
  } catch (_) { /* never block search on analytics */ }
}

module.exports = { hydrate, recordSearch };
