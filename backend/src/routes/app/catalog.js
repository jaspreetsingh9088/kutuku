const router = require('express').Router();
const db = require('../../config/db');
const { ah, paging, pageMeta, notFound } = require('../../utils/http');
const { remember } = require('../../utils/cache');
const { hydrate } = require('../../services/catalog');
const { optionalCustomer } = require('../../middleware/auth');

const ACTIVE = 'p.is_active = 1 AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.is_active = 1)';
const BASE_COLS = 'p.id, p.category_id, p.name, p.slug, p.is_featured, p.gst_rate, p.created_at, p.sold_count';

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

router.get('/home', optionalCustomer, ah(async (req, res) => {
  const data = await remember('home', 60, async () => {
    const [banners, categories, featured, popular, latest] = await Promise.all([
      db.query('SELECT id, title, description, image_url, redirect_type, redirect_value FROM banners WHERE is_active = 1 ORDER BY display_order, id'),
      db.query(`SELECT c.id, c.name, c.slug, c.image_url,
        (SELECT COUNT(*) FROM products p WHERE p.is_active = 1
           AND (p.category_id = c.id OR p.category_id IN (SELECT id FROM categories s WHERE s.parent_id = c.id))) AS product_count
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
    const rows = await db.query(
      `SELECT c.id, c.parent_id, c.name, c.slug, c.image_url, c.display_order,
         (SELECT COUNT(*) FROM products p WHERE p.is_active = 1
            AND (p.category_id = c.id OR p.category_id IN (SELECT id FROM categories s WHERE s.parent_id = c.id))) AS product_count
       FROM categories c WHERE c.is_active = 1 ORDER BY c.display_order, c.name`
    );
    const roots = rows.filter((r) => !r.parent_id).map((r) => ({ ...r, children: [] }));
    const map = Object.fromEntries(roots.map((r) => [r.id, r]));
    rows.filter((r) => r.parent_id && map[r.parent_id]).forEach((r) => map[r.parent_id].children.push(r));
    return roots;
  });
  res.json(tree);
}));

/**
 * GET /products?q=&category_id=&min_price=&max_price=&sort=latest|popular|price_asc|price_desc&featured=1&page=&limit=
 * category_id includes products in its subcategories.
 */
router.get('/products', optionalCustomer, ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 20, 50);
  const where = [ACTIVE];
  const params = [];
  const q = String(req.query.q || '').trim();
  if (q) {
    where.push('(p.name LIKE ? OR p.description LIKE ? OR EXISTS (SELECT 1 FROM product_variants sv WHERE sv.product_id = p.id AND sv.sku = ?))');
    params.push(`%${q}%`, `%${q}%`, q);
  }
  if (req.query.category_id) {
    where.push('(p.category_id = ? OR p.category_id IN (SELECT id FROM categories WHERE parent_id = ?))');
    params.push(Number(req.query.category_id), Number(req.query.category_id));
  }
  if (req.query.featured === '1') where.push('p.is_featured = 1');
  const priceExpr = '(SELECT MIN(v2.price) FROM product_variants v2 WHERE v2.product_id = p.id AND v2.is_active = 1)';
  if (req.query.min_price) { where.push(`${priceExpr} >= ?`); params.push(Number(req.query.min_price)); }
  if (req.query.max_price) { where.push(`${priceExpr} <= ?`); params.push(Number(req.query.max_price)); }
  const order = {
    latest: 'p.created_at DESC, p.id DESC',
    popular: 'p.sold_count DESC, p.id DESC',
    price_asc: `${priceExpr} ASC, p.id`,
    price_desc: `${priceExpr} DESC, p.id`,
  }[req.query.sort] || (q ? `(p.name LIKE ${db.pool.escape(`${q}%`)}) DESC, p.sold_count DESC` : 'p.created_at DESC, p.id DESC');

  const whereSql = where.join(' AND ');
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM products p WHERE ${whereSql}`, params);
  const rows = await db.query(`SELECT ${BASE_COLS} FROM products p WHERE ${whereSql} ORDER BY ${order} LIMIT ? OFFSET ?`, [...params, limit, offset]);
  const data = await markWishlisted(req.customer && req.customer.id, await hydrate(rows));
  res.json({ data, meta: pageMeta(page, limit, total) });
}));

router.get('/products/:id', optionalCustomer, ah(async (req, res) => {
  const row = await db.one('SELECT p.*, c.name AS category_name FROM products p LEFT JOIN categories c ON c.id = p.category_id WHERE p.id = ? AND p.is_active = 1', [req.params.id]);
  if (!row) throw notFound('Product not found');
  const [product] = await markWishlisted(req.customer && req.customer.id, await hydrate([row]));
  const related = await listProducts('p.category_id = ? AND p.id <> ?', [row.category_id, row.id], 'p.sold_count DESC', 8);
  res.json({ ...product, related });
}));

/** Lightweight autocomplete. */
router.get('/search/suggest', ah(async (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 2) return res.json([]);
  res.json(await db.query(`SELECT p.id, p.name FROM products p WHERE ${ACTIVE} AND p.name LIKE ? ORDER BY p.sold_count DESC LIMIT 8`, [`%${q}%`]));
}));

module.exports = router;
