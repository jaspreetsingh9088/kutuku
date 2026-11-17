const router = require('express').Router();
const { z } = require('zod');
const db = require('../../config/db');
const { ah, paging, pageMeta, notFound, badRequest, validate } = require('../../utils/http');
const { remember } = require('../../utils/cache');
const { hydrate, recordSearch } = require('../../services/catalog');
const { optionalCustomer, customerAuth } = require('../../middleware/auth');

const ACTIVE = 'p.is_active = 1 AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.is_active = 1)';
const BASE_COLS = 'p.id, p.category_id, p.store_id, p.name, p.slug, p.is_featured, p.gst_rate, p.created_at, p.sold_count, p.rating_avg, p.rating_count';
const PRICE = '(SELECT MIN(v2.price) FROM product_variants v2 WHERE v2.product_id = p.id AND v2.is_active = 1)';
const COUNT_IN_CAT = (alias) => `(SELECT COUNT(*) FROM products p WHERE p.is_active = 1
  AND (p.category_id = ${alias}.id OR p.category_id IN (SELECT id FROM categories s WHERE s.parent_id = ${alias}.id)))`;

async function listProducts(where, params, order, limit) {
  const rows = await db.query(`SELECT ${BASE_COLS} FROM products p WHERE ${ACTIVE} ${where ? `AND ${where}` : ''} ORDER BY ${order} LIMIT ?`, [...params, limit]);
  return hydrate(rows);
}

/** Wishlist flags for logged-in users. */
async function markWishlisted(customerId, products) {
  if (!customerId || !products.length) return products;
  const ids = products.map((p) => p.id);
  const rows = await db.query('SELECT product_id FROM wishlist_items WHERE customer_id = ? AND product_id IN (?)', [customerId, ids]);
  const set = new Set(rows.map((r) => r.product_id));
  return products.map((p) => ({ ...p, wishlisted: set.has(p.id) }));
}

/** Store summary with product/follower counts (and whether the customer follows it). */
async function storeSummary(where, params, customerId) {
  const rows = await db.query(
    `SELECT s.id, s.name, s.slug, s.logo_url, s.description, s.city, s.is_verified,
       (SELECT COUNT(*) FROM products p WHERE p.store_id = s.id AND p.is_active = 1) AS product_count,
       (SELECT COUNT(*) FROM store_followers f WHERE f.store_id = s.id) AS follower_count
       ${customerId ? ', EXISTS (SELECT 1 FROM store_followers f2 WHERE f2.store_id = s.id AND f2.customer_id = ?) AS following' : ''}
     FROM stores s WHERE s.is_active = 1 AND ${where} ORDER BY s.display_order, s.name LIMIT 10`,
    customerId ? [customerId, ...params] : params
  );
  return rows.map((s) => ({ ...s, is_verified: !!s.is_verified, following: !!s.following }));
}

router.get('/home', optionalCustomer, ah(async (req, res) => {
  const data = await remember('home', 60, async () => {
    const [banners, categories, featured, popular, latest] = await Promise.all([
      db.query('SELECT id, title, description, image_url, redirect_type, redirect_value FROM banners WHERE is_active = 1 AND store_id IS NULL ORDER BY display_order, id'),
      db.query(`SELECT c.id, c.name, c.slug, c.image_url, ${COUNT_IN_CAT('c')} AS product_count
        FROM categories c WHERE c.is_active = 1 AND c.parent_id IS NULL ORDER BY c.display_order, c.name`),
      listProducts('p.is_featured = 1', [], 'p.updated_at DESC', 10),
      listProducts('', [], 'p.sold_count DESC, p.id DESC', 10),
      listProducts('', [], 'p.created_at DESC', 10),
    ]);
    return { banners, categories, featured, popular, latest };
  });
  const id = req.customer && req.customer.id;
  res.json({
    ...data,
    featured: await markWishlisted(id, data.featured),
    popular: await markWishlisted(id, data.popular),
    latest: await markWishlisted(id, data.latest),
  });
}));

router.get('/categories', ah(async (_req, res) => {
  const tree = await remember('categories', 300, async () => {
    const rows = await db.query(`SELECT c.id, c.parent_id, c.name, c.slug, c.image_url, c.display_order, ${COUNT_IN_CAT('c')} AS product_count
      FROM categories c WHERE c.is_active = 1 ORDER BY c.display_order, c.name`);
    const roots = rows.filter((r) => !r.parent_id).map((r) => ({ ...r, children: [] }));
    const map = Object.fromEntries(roots.map((r) => [r.id, r]));
    rows.filter((r) => r.parent_id && map[r.parent_id]).forEach((r) => map[r.parent_id].children.push(r));
    return roots;
  });
  res.json(tree);
}));

/** Options for the "Filter By" sheet: price range, colours and store locations. */
router.get('/filters', ah(async (_req, res) => {
  res.json(await remember('filters', 300, async () => {
    const [price, colors, cities] = await Promise.all([
      db.one('SELECT COALESCE(MIN(price),0) AS min, COALESCE(MAX(price),0) AS max FROM product_variants WHERE is_active = 1'),
      db.query("SELECT color AS name, MAX(color_hex) AS hex FROM product_variants WHERE is_active = 1 AND color IS NOT NULL AND color <> '' GROUP BY color ORDER BY COUNT(*) DESC LIMIT 12"),
      db.query("SELECT DISTINCT city FROM stores WHERE is_active = 1 AND city IS NOT NULL AND city <> '' ORDER BY city"),
    ]);
    return { price, colors, cities: cities.map((c) => c.city) };
  }));
}));

/**
 * GET /products?q=&category_id=&store_id=&color=&city=&min_price=&max_price=&sort=latest|popular|price_asc|price_desc|best&featured=1&page=&limit=
 */
router.get('/products', optionalCustomer, ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 20, 50);
  const where = [ACTIVE];
  const params = [];
  const q = String(req.query.q || '').trim();
  if (q) {
    where.push('(p.name LIKE ? OR p.description LIKE ? OR EXISTS (SELECT 1 FROM product_variants sv WHERE sv.product_id = p.id AND sv.sku = ?) OR EXISTS (SELECT 1 FROM stores st WHERE st.id = p.store_id AND st.name LIKE ?))');
    params.push(`%${q}%`, `%${q}%`, q, `%${q}%`);
  }
  if (req.query.category_id) {
    where.push('(p.category_id = ? OR p.category_id IN (SELECT id FROM categories WHERE parent_id = ?))');
    params.push(Number(req.query.category_id), Number(req.query.category_id));
  }
  if (req.query.store_id) { where.push('p.store_id = ?'); params.push(Number(req.query.store_id)); }
  if (req.query.color) { where.push('EXISTS (SELECT 1 FROM product_variants cv WHERE cv.product_id = p.id AND cv.is_active = 1 AND cv.color = ?)'); params.push(String(req.query.color)); }
  if (req.query.city) { where.push('EXISTS (SELECT 1 FROM stores sc WHERE sc.id = p.store_id AND sc.city = ?)'); params.push(String(req.query.city)); }
  if (req.query.featured === '1') where.push('p.is_featured = 1');
  if (req.query.min_price) { where.push(`${PRICE} >= ?`); params.push(Number(req.query.min_price)); }
  if (req.query.max_price) { where.push(`${PRICE} <= ?`); params.push(Number(req.query.max_price)); }
  const order = {
    latest: 'p.created_at DESC, p.id DESC',
    popular: 'p.sold_count DESC, p.id DESC',
    best: 'p.sold_count DESC, p.rating_avg DESC, p.id DESC',
    price_asc: `${PRICE} ASC, p.id`,
    price_desc: `${PRICE} DESC, p.id`,
  }[req.query.sort] || (q ? `(p.name LIKE ${db.pool.escape(`${q}%`)}) DESC, p.sold_count DESC` : 'p.created_at DESC, p.id DESC');

  const whereSql = where.join(' AND ');
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM products p WHERE ${whereSql}`, params);
  const rows = await db.query(`SELECT ${BASE_COLS} FROM products p WHERE ${whereSql} ORDER BY ${order} LIMIT ? OFFSET ?`, [...params, limit, offset]);
  const data = await markWishlisted(req.customer && req.customer.id, await hydrate(rows));
  const body = { data, meta: pageMeta(page, limit, total) };
  if (q && page === 1) {
    recordSearch(q);
    body.stores = await storeSummary('s.name LIKE ?', [`%${q}%`], req.customer && req.customer.id);
  }
  res.json(body);
}));

router.get('/products/:id', optionalCustomer, ah(async (req, res) => {
  const row = await db.one('SELECT p.*, c.name AS category_name FROM products p LEFT JOIN categories c ON c.id = p.category_id WHERE p.id = ? AND p.is_active = 1', [req.params.id]);
  if (!row) throw notFound('Product not found');
  const [product] = await markWishlisted(req.customer && req.customer.id, await hydrate([row]));
  const related = await listProducts('p.category_id = ? AND p.id <> ?', [row.category_id, row.id], 'p.sold_count DESC', 8);
  let store = null;
  if (row.store_id) [store] = await storeSummary('s.id = ?', [row.store_id], req.customer && req.customer.id);
  res.json({ ...product, store: store || product.store, related });
}));

/** Lightweight autocomplete. */
router.get('/search/suggest', ah(async (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 2) return res.json([]);
  res.json(await db.query(`SELECT p.id, p.name FROM products p WHERE ${ACTIVE} AND p.name LIKE ? ORDER BY p.sold_count DESC LIMIT 8`, [`%${q}%`]));
}));

/** "Popular Search": most searched terms today (falls back to all-time), each with a matching product image. */
router.get('/search/popular', ah(async (_req, res) => {
  const out = await remember('popular-search', 120, async () => {
    let terms = await db.query('SELECT term, day_count AS count, total_count FROM search_terms WHERE day = CURDATE() ORDER BY day_count DESC LIMIT 6');
    if (terms.length < 4) terms = await db.query('SELECT term, day_count AS count, total_count FROM search_terms ORDER BY total_count DESC LIMIT 6');
    const result = [];
    for (const t of terms) {
      const p = await db.one(`SELECT p.id, p.name, (SELECT url FROM product_images i WHERE i.product_id = p.id ORDER BY sort_order LIMIT 1) AS image
        FROM products p WHERE ${ACTIVE} AND p.name LIKE ? ORDER BY p.sold_count DESC LIMIT 1`, [`%${t.term}%`]);
      if (p) result.push({ term: t.term, count: Number(t.count || t.total_count), product_id: p.id, product_name: p.name, image: p.image });
    }
    if (result.length < 4) {
      // Not enough search history yet: show best sellers instead
      const best = await listProducts('', [], 'p.sold_count DESC, p.id DESC', 4 - result.length);
      best.forEach((p) => result.push({ term: p.name, count: p.sold_count, product_id: p.id, product_name: p.name, image: p.image }));
    }
    return result.map((r, i) => ({ ...r, tag: ['hot', 'new', 'popular', 'new', 'hot', 'popular'][i] }));
  });
  res.json(out);
}));

// ---------- Stores ----------
router.get('/stores', optionalCustomer, ah(async (req, res) => {
  const followed = req.query.followed === '1' && req.customer;
  res.json(await storeSummary(
    followed ? 'EXISTS (SELECT 1 FROM store_followers ff WHERE ff.store_id = s.id AND ff.customer_id = ?)' : '1=1',
    followed ? [req.customer.id] : [], req.customer && req.customer.id
  ));
}));

router.get('/stores/:id', optionalCustomer, ah(async (req, res) => {
  const [store] = await storeSummary('s.id = ?', [req.params.id], req.customer && req.customer.id);
  if (!store) throw notFound('Store not found');
  const banners = await db.query('SELECT id, title, description, image_url, redirect_type, redirect_value FROM banners WHERE is_active = 1 AND store_id = ? ORDER BY display_order, id', [store.id]);
  const popular = await markWishlisted(req.customer && req.customer.id, await listProducts('p.store_id = ?', [store.id], 'p.sold_count DESC, p.id DESC', 10));
  res.json({ ...store, banners, popular });
}));

router.post('/stores/:id/follow', customerAuth, ah(async (req, res) => {
  const s = await db.one('SELECT id FROM stores WHERE id = ? AND is_active = 1', [req.params.id]);
  if (!s) throw notFound('Store not found');
  await db.query('INSERT IGNORE INTO store_followers (store_id, customer_id) VALUES (?, ?)', [s.id, req.customer.id]);
  res.json({ following: true });
}));

router.delete('/stores/:id/follow', customerAuth, ah(async (req, res) => {
  await db.query('DELETE FROM store_followers WHERE store_id = ? AND customer_id = ?', [req.params.id, req.customer.id]);
  res.json({ following: false });
}));

// ---------- Reviews ----------
router.get('/products/:id/reviews', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 20, 50);
  const [{ total }] = await db.query('SELECT COUNT(*) AS total FROM reviews WHERE product_id = ? AND is_approved = 1', [req.params.id]);
  const rows = await db.query(
    `SELECT r.id, r.rating, r.comment, r.created_at, COALESCE(c.name, 'Customer') AS customer_name, c.avatar_url
     FROM reviews r JOIN customers c ON c.id = r.customer_id WHERE r.product_id = ? AND r.is_approved = 1 ORDER BY r.id DESC LIMIT ? OFFSET ?`,
    [req.params.id, limit, offset]
  );
  res.json({ data: rows, meta: pageMeta(page, limit, total) });
}));

router.post('/products/:id/reviews', customerAuth, validate(z.object({ rating: z.number().int().min(1).max(5), comment: z.string().trim().max(1000).optional() })), ah(async (req, res) => {
  const productId = Number(req.params.id);
  const bought = await db.one(
    `SELECT o.id FROM orders o JOIN order_items oi ON oi.order_id = o.id
     WHERE o.customer_id = ? AND oi.product_id = ? AND o.status = 'delivered' LIMIT 1`, [req.customer.id, productId]
  );
  if (!bought) throw badRequest('You can review a product after it has been delivered to you');
  await db.query(
    `INSERT INTO reviews (product_id, customer_id, order_id, rating, comment) VALUES (?,?,?,?,?)
     ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), created_at = NOW()`,
    [productId, req.customer.id, bought.id, req.body.rating, req.body.comment || null]
  );
  await refreshRating(productId);
  res.status(201).json({ ok: true });
}));

async function refreshRating(productId) {
  await db.query(
    `UPDATE products SET rating_avg = COALESCE((SELECT AVG(rating) FROM reviews WHERE product_id = ? AND is_approved = 1), 0),
       rating_count = (SELECT COUNT(*) FROM reviews WHERE product_id = ? AND is_approved = 1) WHERE id = ?`,
    [productId, productId, productId]
  );
}

// ---------- App content ----------
router.get('/onboarding', ah(async (_req, res) => {
  res.json(await remember('onboarding', 300, () => db.query('SELECT id, title, subtitle, image_url FROM onboarding_slides WHERE is_active = 1 ORDER BY display_order, id')));
}));
router.get('/faqs', ah(async (_req, res) => {
  res.json(await remember('faqs', 300, () => db.query('SELECT id, question, answer FROM faqs WHERE is_active = 1 ORDER BY display_order, id')));
}));
router.get('/pages/:slug', ah(async (req, res) => {
  const p = await db.one('SELECT slug, title, content, updated_at FROM pages WHERE slug = ?', [req.params.slug]);
  if (!p) throw notFound('Page not found');
  res.json(p);
}));

module.exports = router;
module.exports.refreshRating = refreshRating;
