class ApiError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

const badRequest = (msg, details) => new ApiError(400, msg, details);
const unauthorized = (msg = 'Unauthorized') => new ApiError(401, msg);
const forbidden = (msg = 'Forbidden') => new ApiError(403, msg);
const notFound = (msg = 'Not found') => new ApiError(404, msg);

/** Wrap async route handlers so rejected promises reach the error middleware. */
const ah = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/** Validate req[source] against a zod schema; replaces it with parsed data. */
const validate = (schema, source = 'body') => (req, _res, next) => {
  const r = schema.safeParse(req[source]);
  if (!r.success) {
    return next(badRequest('Validation failed', r.error.issues.map((i) => ({ path: i.path.join('.'), message: i.message }))));
  }
  req[source] = r.data;
  next();
};

/** Parse ?page & ?limit. */
function paging(q, defLimit = 20, maxLimit = 100) {
  const page = Math.max(1, parseInt(q.page, 10) || 1);
  const limit = Math.min(maxLimit, Math.max(1, parseInt(q.limit, 10) || defLimit));
  return { page, limit, offset: (page - 1) * limit };
}

const pageMeta = (page, limit, total) => ({ page, limit, total, pages: Math.ceil(total / limit) });

const slugify = (s) =>
  String(s)
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^\w\s-]/g, '')
    .trim()
    .replace(/[\s_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 150);

const round2 = (n) => Math.round((Number(n) + Number.EPSILON) * 100) / 100;

module.exports = { ApiError, badRequest, unauthorized, forbidden, notFound, ah, validate, paging, pageMeta, slugify, round2 };
