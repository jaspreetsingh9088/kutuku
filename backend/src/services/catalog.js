const db = require('../config/db');

/** Attach images and variants to a list of product rows (2 queries regardless of list size). */
async function hydrate(products, { activeVariantsOnly = true } = {}) {
  if (!products.length) return products;
  const ids = products.map((p) => p.id);
  const [images, variants] = await Promise.all([
    db.query('SELECT id, product_id, url, sort_order FROM product_images WHERE product_id IN (?) ORDER BY sort_order, id', [ids]),
    db.query(
      `SELECT id, product_id, sku, label, mrp, price, stock, low_stock_threshold, is_active
       FROM product_variants WHERE product_id IN (?) ${activeVariantsOnly ? 'AND is_active = 1' : ''} ORDER BY price, id`,
      [ids]
    ),
  ]);
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
    return x;
  });
}

module.exports = { hydrate };
