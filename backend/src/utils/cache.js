const NodeCache = require('node-cache');

// Short-lived cache for frequently read, rarely changed data (home screen, categories, banners).
const cache = new NodeCache({ stdTTL: 60, checkperiod: 120 });

async function remember(key, ttl, fn) {
  const hit = cache.get(key);
  if (hit !== undefined) return hit;
  const val = await fn();
  cache.set(key, val, ttl);
  return val;
}

/** Clear all keys starting with any of the given prefixes. */
function bust(...prefixes) {
  const keys = cache.keys().filter((k) => prefixes.some((p) => k.startsWith(p)));
  cache.del(keys);
}

module.exports = { remember, bust };
