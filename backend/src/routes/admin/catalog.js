const router = require('express').Router();
const { z } = require('zod');
const XLSX = require('xlsx');
const db = require('../../config/db');
const { ah, validate, paging, pageMeta, notFound, badRequest, slugify } = require('../../utils/http');
const { adminAuth } = require('../../middleware/auth');
const { imageUpload, storeImages, sheetUpload } = require('../../middleware/upload');
const { hydrate } = require('../../services/catalog');
const { bust } = require('../../utils/cache');

router.use(adminAuth());

/** Find a free slug. Pass the transaction helper `q` when called inside db.tx so uncommitted rows are seen. */
const uniqueSlug = async (table, base, exceptId = 0, q = db) => {
  const root = slugify(base) || 'item';
  let slug = root;
  let i = 1;
  // eslint-disable-next-line no-await-in-loop
  while (await q.one(`SELECT id FROM ${table} WHERE slug = ? AND id <> ?`, [slug, exceptId])) slug = `${root}-${++i}`;
  return slug;
};
const bool = z.union([z.boolean(), z.number(), z.string()]).transform((v) => v === true || v === 1 || v === '1' || v === 'true');

// ================= Image upload (generic) =================
router.post('/uploads/:folder(products|categories|banners)', imageUpload.array('files', 10), ah(async (req, res) => {
  if (!req.files || !req.files.length) throw badRequest('Choose at least one image');
  res.json({ urls: await storeImages(req.params.folder, req.files) });
}));

// ================= Categories =================
const categorySchema = z.object({
  name: z.string().trim().min(2).max(150),
  parent_id: z.number().int().positive().nullable().optional(),
  image_url: z.string().url().nullable().optional().or(z.literal('')),
  display_order: z.number().int().default(0),
  is_active: bool.default(true),
});

router.get('/categories', ah(async (_req, res) => {
  res.json(await db.query(
    `SELECT c.*, p.name AS parent_name,
       (SELECT COUNT(*) FROM products pr WHERE pr.category_id = c.id) AS product_count
     FROM categories c LEFT JOIN categories p ON p.id = c.parent_id ORDER BY COALESCE(c.parent_id, c.id), c.parent_id IS NOT NULL, c.display_order, c.name`
  ));
}));

router.post('/categories', validate(categorySchema), ah(async (req, res) => {
  const c = req.body;
  const r = await db.query('INSERT INTO categories (name, slug, parent_id, image_url, display_order, is_active) VALUES (?,?,?,?,?,?)',
    [c.name, await uniqueSlug('categories', c.name), c.parent_id || null, c.image_url || null, c.display_order, c.is_active ? 1 : 0]);
  bust('categories', 'home');
  res.status(201).json(await db.one('SELECT * FROM categories WHERE id = ?', [r.insertId]));
}));

router.put('/categories/:id', validate(categorySchema), ah(async (req, res) => {
  const id = Number(req.params.id);
  const c = req.body;
  if (c.parent_id === id) throw badRequest('A category cannot be its own parent');
  if (!(await db.one('SELECT id FROM categories WHERE id = ?', [id]))) throw notFound();
  await db.query('UPDATE categories SET name=?, slug=?, parent_id=?, image_url=?, display_order=?, is_active=? WHERE id=?',
    [c.name, await uniqueSlug('categories', c.name, id), c.parent_id || null, c.image_url || null, c.display_order, c.is_active ? 1 : 0, id]);
  bust('categories', 'home');
  res.json(await db.one('SELECT * FROM categories WHERE id = ?', [id]));
}));

router.delete('/categories/:id', ah(async (req, res) => {
  const n = await db.one('SELECT COUNT(*) AS n FROM products WHERE category_id = ?', [req.params.id]);
  if (n.n > 0) throw badRequest(`Move or delete the ${n.n} product(s) in this category first`);
  await db.query('DELETE FROM categories WHERE id = ?', [req.params.id]);
  bust('categories', 'home');
  res.json({ ok: true });
}));

// ================= Products =================
const variantSchema = z.object({
  id: z.number().int().positive().optional(),
  sku: z.string().trim().min(1).max(80),
  label: z.string().trim().min(1).max(80),
  mrp: z.coerce.number().nonnegative(),
  price: z.coerce.number().nonnegative(),
  stock: z.coerce.number().int().min(0),
  low_stock_threshold: z.coerce.number().int().min(0).default(5),
  is_active: bool.default(true),
}).refine((v) => v.price <= v.mrp, { message: 'Selling price cannot exceed MRP', path: ['price'] });

const productSchema = z.object({
  name: z.string().trim().min(2).max(255),
  category_id: z.number().int().positive().nullable().optional(),
  description: z.string().max(20000).optional().nullable(),
  specifications: z.array(z.object({ key: z.string(), value: z.string() })).optional().nullable(),
  hsn_code: z.string().max(20).optional().nullable(),
  gst_rate: z.coerce.number().min(0).max(28).default(0),
  is_featured: bool.default(false),
  is_active: bool.default(true),
  images: z.array(z.string().url()).max(10).default([]),
  variants: z.array(variantSchema).min(1, 'Add at least one variant'),
});

router.get('/products', ah(async (req, res) => {
  const { page, limit, offset } = paging(req.query, 25, 200);
  const where = ['1=1'];
  const params = [];
  if (req.query.q) {
    where.push('(p.name LIKE ? OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.sku LIKE ?))');
    params.push(`%${req.query.q}%`, `%${req.query.q}%`);
  }
  if (req.query.category_id) { where.push('(p.category_id = ? OR p.category_id IN (SELECT id FROM categories WHERE parent_id = ?))'); params.push(req.query.category_id, req.query.category_id); }
  if (req.query.status === 'active') where.push('p.is_active = 1');
  if (req.query.status === 'inactive') where.push('p.is_active = 0');
  if (req.query.stock === 'low') where.push('EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.stock <= v.low_stock_threshold)');
  if (req.query.stock === 'out') where.push('NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.stock > 0)');
  const w = where.join(' AND ');
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total FROM products p WHERE ${w}`, params);
  const rows = await db.query(
    `SELECT p.id, p.name, p.slug, p.category_id, p.is_active, p.is_featured, p.gst_rate, p.sold_count, p.created_at, c.name AS category_name
     FROM products p LEFT JOIN categories c ON c.id = p.category_id WHERE ${w} ORDER BY p.id DESC LIMIT ? OFFSET ?`,
    [...params, limit, offset]
  );
  const data = (await hydrate(rows, { activeVariantsOnly: false })).map((p) => ({ ...p, total_stock: p.variants.reduce((a, v) => a + v.stock, 0) }));
  res.json({ data, meta: pageMeta(page, limit, total) });
}));

router.get('/products/:id', ah(async (req, res) => {
  const row = await db.one('SELECT * FROM products WHERE id = ?', [req.params.id]);
  if (!row) throw notFound();
  const [p] = await hydrate([row], { activeVariantsOnly: false });
  res.json(p);
}));

async function saveProduct(q, id, b) {
  const skus = b.variants.map((v) => v.sku.toUpperCase());
  if (new Set(skus).size !== skus.length) throw badRequest('Each variant needs a unique SKU');
  const clash = await q.one('SELECT sku FROM product_variants WHERE sku IN (?) AND product_id <> ? LIMIT 1', [skus, id || 0]);
  if (clash) throw badRequest(`SKU ${clash.sku} is already used by another product`);
  const vals = [b.name, b.category_id || null, b.description || null, b.specifications ? JSON.stringify(b.specifications) : null,
    b.hsn_code || null, b.gst_rate, b.is_featured ? 1 : 0, b.is_active ? 1 : 0];
  if (id) {
    await q.query('UPDATE products SET name=?, category_id=?, description=?, specifications=?, hsn_code=?, gst_rate=?, is_featured=?, is_active=?, slug=? WHERE id=?',
      [...vals, await uniqueSlug('products', b.name, id, q), id]);
  } else {
    const r = await q.query('INSERT INTO products (name, category_id, description, specifications, hsn_code, gst_rate, is_featured, is_active, slug) VALUES (?,?,?,?,?,?,?,?,?)',
      [...vals, await uniqueSlug('products', b.name, 0, q)]);
    id = r.insertId;
  }
  await q.query('DELETE FROM product_images WHERE product_id = ?', [id]);
  for (let i = 0; i < b.images.length; i++) await q.query('INSERT INTO product_images (product_id, url, sort_order) VALUES (?,?,?)', [id, b.images[i], i]);

  const keepIds = [];
  for (const v of b.variants) {
    const existing = await q.one('SELECT id FROM product_variants WHERE product_id = ? AND (id = ? OR sku = ?)', [id, v.id || 0, v.sku.toUpperCase()]);
    const vv = [v.sku.toUpperCase(), v.label, v.mrp, v.price, v.stock, v.low_stock_threshold, v.is_active ? 1 : 0];
    if (existing) {
      await q.query('UPDATE product_variants SET sku=?, label=?, mrp=?, price=?, stock=?, low_stock_threshold=?, is_active=? WHERE id=?', [...vv, existing.id]);
      keepIds.push(existing.id);
    } else {
      const r = await q.query('INSERT INTO product_variants (sku, label, mrp, price, stock, low_stock_threshold, is_active, product_id) VALUES (?,?,?,?,?,?,?,?)', [...vv, id]);
      keepIds.push(r.insertId);
    }
  }
  // Variants removed in the form: delete if never ordered, otherwise deactivate (keeps order history intact)
  const removed = await q.query('SELECT id FROM product_variants WHERE product_id = ? AND id NOT IN (?)', [id, keepIds]);
  for (const r of removed) {
    const used = await q.one('SELECT 1 AS x FROM order_items WHERE variant_id = ? LIMIT 1', [r.id]);
    if (used) await q.query('UPDATE product_variants SET is_active = 0 WHERE id = ?', [r.id]);
    else await q.query('DELETE FROM product_variants WHERE id = ?', [r.id]);
  }
  return id;
}

router.post('/products', validate(productSchema), ah(async (req, res) => {
  const id = await db.tx((q) => saveProduct(q, 0, req.body));
  bust('home');
  res.status(201).json({ id });
}));

router.put('/products/:id', validate(productSchema), ah(async (req, res) => {
  if (!(await db.one('SELECT id FROM products WHERE id = ?', [req.params.id]))) throw notFound();
  await db.tx((q) => saveProduct(q, Number(req.params.id), req.body));
  bust('home');
  res.json({ id: Number(req.params.id) });
}));

router.patch('/products/:id/status', validate(z.object({ is_active: bool })), ah(async (req, res) => {
  await db.query('UPDATE products SET is_active = ? WHERE id = ?', [req.body.is_active ? 1 : 0, req.params.id]);
  bust('home');
  res.json({ ok: true });
}));

router.delete('/products/:id', ah(async (req, res) => {
  const used = await db.one('SELECT 1 AS x FROM order_items WHERE product_id = ? LIMIT 1', [req.params.id]);
  if (used) {
    await db.query('UPDATE products SET is_active = 0 WHERE id = ?', [req.params.id]);
    bust('home');
    return res.json({ ok: true, deactivated: true, message: 'Product has orders, so it was deactivated instead of deleted' });
  }
  await db.query('DELETE FROM products WHERE id = ?', [req.params.id]);
  bust('home');
  res.json({ ok: true });
}));

router.patch('/variants/:id/stock', validate(z.object({ stock: z.coerce.number().int().min(0) })), ah(async (req, res) => {
  await db.query('UPDATE product_variants SET stock = ? WHERE id = ?', [req.body.stock, req.params.id]);
  res.json({ ok: true });
}));

// ================= Bulk import / export =================
const TEMPLATE_HEADERS = ['product_name', 'category', 'subcategory', 'description', 'hsn_code', 'gst_rate', 'is_featured', 'is_active',
  'image_urls', 'sku', 'variant_label', 'mrp', 'price', 'stock', 'low_stock_threshold'];

router.get('/products-import/template', (_req, res) => {
  const ws = XLSX.utils.aoa_to_sheet([
    TEMPLATE_HEADERS,
    ['Basmati Rice Premium', 'Groceries', 'Rice & Grains', 'Aged long-grain basmati rice', '1006', 5, 1, 1, 'https://example.com/rice1.jpg|https://example.com/rice2.jpg', 'RICE-BAS-1KG', '1 kg', 180, 159, 120, 10],
    ['Basmati Rice Premium', 'Groceries', 'Rice & Grains', '', '1006', 5, 1, 1, '', 'RICE-BAS-5KG', '5 kg', 850, 749, 40, 5],
  ]);
  ws['!cols'] = TEMPLATE_HEADERS.map((h) => ({ wch: Math.max(12, h.length + 2) }));
  const notes = XLSX.utils.aoa_to_sheet([
    ['How to use this template'],
    ['• One row per variant. Rows with the same product_name are grouped into one product.'],
    ['• sku must be unique. If the SKU already exists, that variant (and its product) is UPDATED; otherwise it is CREATED.'],
    ['• For a price/stock-only update, fill just sku, mrp, price and stock — other columns can be left blank.'],
    ['• category/subcategory are created automatically if they do not exist.'],
    ['• image_urls: separate multiple URLs with |'],
    ['• is_featured / is_active: 1 = yes, 0 = no'],
  ]);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Products');
  XLSX.utils.book_append_sheet(wb, notes, 'Instructions');
  res.setHeader('Content-Disposition', 'attachment; filename="product-import-template.xlsx"');
  res.type('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet').send(XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }));
});

const blank = (v) => v === undefined || v === null || String(v).trim() === '';
const toNum = (v) => (blank(v) ? null : Number(String(v).replace(/[,₹\s]/g, '')));

/** POST /products-import?dry_run=1 — validates and (unless dry run) applies the sheet. */
router.post('/products-import', sheetUpload.single('file'), ah(async (req, res) => {
  if (!req.file) throw badRequest('Choose a file to upload');
  const wb = XLSX.read(req.file.buffer, { type: 'buffer' });
  const rows = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { defval: '' });
  if (!rows.length) throw badRequest('The sheet is empty');
  if (rows.length > 5000) throw badRequest('Maximum 5000 rows per import');
  const dryRun = req.query.dry_run === '1';

  const errors = [];
  const valid = [];
  const seenSku = new Set();
  rows.forEach((raw, idx) => {
    const r = Object.fromEntries(Object.entries(raw).map(([k, v]) => [String(k).trim().toLowerCase(), typeof v === 'string' ? v.trim() : v]));
    const line = idx + 2;
    const err = (m) => errors.push({ row: line, sku: r.sku || '', message: m });
    if (blank(r.sku)) return err('sku is required');
    const sku = String(r.sku).toUpperCase();
    if (seenSku.has(sku)) return err(`Duplicate SKU ${sku} in file`);
    seenSku.add(sku);
    const mrp = toNum(r.mrp); const price = toNum(r.price); const stock = toNum(r.stock);
    if (mrp !== null && (Number.isNaN(mrp) || mrp < 0)) return err('mrp must be a positive number');
    if (price !== null && (Number.isNaN(price) || price < 0)) return err('price must be a positive number');
    if (mrp !== null && price !== null && price > mrp) return err('price cannot be greater than mrp');
    if (stock !== null && (!Number.isInteger(stock) || stock < 0)) return err('stock must be a whole number ≥ 0');
    const gst = toNum(r.gst_rate);
    if (gst !== null && (Number.isNaN(gst) || gst < 0 || gst > 28)) return err('gst_rate must be between 0 and 28');
    valid.push({ line, sku, r, mrp, price, stock, gst });
  });

  const existing = valid.length
    ? await db.query('SELECT v.id, v.sku, v.product_id FROM product_variants v WHERE v.sku IN (?)', [valid.map((v) => v.sku)])
    : [];
  const bySku = Object.fromEntries(existing.map((e) => [e.sku, e]));
  for (const v of valid) {
    if (!bySku[v.sku]) {
      if (blank(v.r.product_name)) errors.push({ row: v.line, sku: v.sku, message: 'product_name is required for new SKUs' });
      else if (blank(v.r.variant_label) || v.mrp === null || v.price === null || v.stock === null) errors.push({ row: v.line, sku: v.sku, message: 'variant_label, mrp, price and stock are required for new SKUs' });
    }
  }
  const okRows = valid.filter((v) => !errors.some((e) => e.row === v.line));
  const summary = { total_rows: rows.length, valid_rows: okRows.length, error_rows: errors.length, to_create: okRows.filter((v) => !bySku[v.sku]).length, to_update: okRows.filter((v) => bySku[v.sku]).length };
  if (dryRun) return res.json({ dry_run: true, summary, errors });

  const result = await db.tx(async (q) => {
    const catCache = {};
    const getCategory = async (name, parentId) => {
      if (blank(name)) return parentId;
      const key = `${parentId || 0}:${name.toLowerCase()}`;
      if (catCache[key]) return catCache[key];
      let c = await q.one('SELECT id FROM categories WHERE name = ? AND parent_id <=> ?', [name, parentId || null]);
      if (!c) {
        const r = await q.query('INSERT INTO categories (name, slug, parent_id) VALUES (?, ?, ?)', [name, await uniqueSlug('categories', name, 0, q), parentId || null]);
        c = { id: r.insertId };
      }
      catCache[key] = c.id;
      return c.id;
    };
    const productIdByName = {};
    let created = 0; let updated = 0;
    for (const v of okRows) {
      const r = v.r;
      const ex = bySku[v.sku];
      if (ex) {
        const sets = []; const params = [];
        if (v.mrp !== null) { sets.push('mrp = ?'); params.push(v.mrp); }
        if (v.price !== null) { sets.push('price = ?'); params.push(v.price); }
        if (v.stock !== null) { sets.push('stock = ?'); params.push(v.stock); }
        if (!blank(r.variant_label)) { sets.push('label = ?'); params.push(r.variant_label); }
        if (!blank(r.low_stock_threshold)) { sets.push('low_stock_threshold = ?'); params.push(toNum(r.low_stock_threshold)); }
        if (sets.length) await q.query(`UPDATE product_variants SET ${sets.join(', ')} WHERE id = ?`, [...params, ex.id]);
        const psets = []; const pparams = [];
        if (!blank(r.description)) { psets.push('description = ?'); pparams.push(r.description); }
        if (!blank(r.hsn_code)) { psets.push('hsn_code = ?'); pparams.push(String(r.hsn_code)); }
        if (v.gst !== null) { psets.push('gst_rate = ?'); pparams.push(v.gst); }
        if (!blank(r.is_active)) { psets.push('is_active = ?'); pparams.push(Number(r.is_active) ? 1 : 0); }
        if (!blank(r.is_featured)) { psets.push('is_featured = ?'); pparams.push(Number(r.is_featured) ? 1 : 0); }
        if (psets.length) await q.query(`UPDATE products SET ${psets.join(', ')} WHERE id = ?`, [...pparams, ex.product_id]);
        updated++;
        continue;
      }
      const pname = String(r.product_name);
      let pid = productIdByName[pname.toLowerCase()];
      if (!pid) {
        const found = await q.one('SELECT id FROM products WHERE name = ? LIMIT 1', [pname]);
        if (found) pid = found.id;
        else {
          const parent = await getCategory(r.category, null);
          const catId = await getCategory(r.subcategory, parent);
          const ins = await q.query(
            'INSERT INTO products (name, slug, category_id, description, hsn_code, gst_rate, is_featured, is_active) VALUES (?,?,?,?,?,?,?,?)',
            [pname, await uniqueSlug('products', pname, 0, q), catId || null, r.description || null, blank(r.hsn_code) ? null : String(r.hsn_code), v.gst || 0,
              Number(r.is_featured) ? 1 : 0, blank(r.is_active) ? 1 : (Number(r.is_active) ? 1 : 0)]
          );
          pid = ins.insertId;
          const urls = String(r.image_urls || '').split('|').map((s) => s.trim()).filter(Boolean);
          for (let i = 0; i < urls.length; i++) await q.query('INSERT INTO product_images (product_id, url, sort_order) VALUES (?,?,?)', [pid, urls[i], i]);
        }
        productIdByName[pname.toLowerCase()] = pid;
      }
      await q.query('INSERT INTO product_variants (product_id, sku, label, mrp, price, stock, low_stock_threshold) VALUES (?,?,?,?,?,?,?)',
        [pid, v.sku, r.variant_label, v.mrp, v.price, v.stock, toNum(r.low_stock_threshold) ?? 5]);
      created++;
    }
    return { created, updated };
  });
  bust('home', 'categories');
  res.json({ dry_run: false, summary: { ...summary, ...result }, errors });
}));

router.get('/products-export', ah(async (_req, res) => {
  const rows = await db.query(
    `SELECT p.name AS product_name, pc.name AS category, c.name AS subcategory, p.description, p.hsn_code, p.gst_rate, p.is_featured, p.is_active,
       (SELECT GROUP_CONCAT(url ORDER BY sort_order SEPARATOR '|') FROM product_images i WHERE i.product_id = p.id) AS image_urls,
       v.sku, v.label AS variant_label, v.mrp, v.price, v.stock, v.low_stock_threshold
     FROM product_variants v JOIN products p ON p.id = v.product_id
     LEFT JOIN categories c ON c.id = p.category_id LEFT JOIN categories pc ON pc.id = c.parent_id
     ORDER BY p.id, v.id`
  );
  // When the product's category is top-level, report it as category (not subcategory)
  const fixed = rows.map((r) => (r.category ? r : { ...r, category: r.subcategory, subcategory: '' }));
  const ws = XLSX.utils.json_to_sheet(fixed, { header: TEMPLATE_HEADERS });
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Products');
  res.setHeader('Content-Disposition', `attachment; filename="products-${new Date().toISOString().slice(0, 10)}.xlsx"`);
  res.type('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet').send(XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }));
}));

module.exports = router;
