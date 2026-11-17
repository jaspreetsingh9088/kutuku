const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const multer = require('multer');
const config = require('../config');
const { badRequest } = require('../utils/http');

// Vercel's filesystem is read-only except /tmp; there, images go to Vercel Blob instead.
const UPLOAD_ROOT = config.onVercel ? path.join('/tmp', 'uploads') : path.join(__dirname, '..', '..', 'uploads');
try { fs.mkdirSync(UPLOAD_ROOT, { recursive: true }); } catch (_) { /* read-only fs */ }

const IMAGE_TYPES = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };

/** Accepts up to 10 images (5 MB each) into memory; storeImages() then saves them. */
const imageUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024, files: 10 },
  fileFilter: (_req, file, cb) =>
    IMAGE_TYPES[file.mimetype] ? cb(null, true) : cb(badRequest('Only JPG, PNG or WEBP images are allowed')),
});

/** Save uploaded images to the configured storage and return their public URLs. */
async function storeImages(folder, files) {
  const urls = [];
  for (const f of files) {
    const name = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}${IMAGE_TYPES[f.mimetype]}`;
    if (config.storage.driver === 'blob') {
      const { put } = require('@vercel/blob');
      const blob = await put(`${folder}/${name}`, f.buffer, { access: 'public', contentType: f.mimetype, addRandomSuffix: false });
      urls.push(blob.url);
    } else {
      const dir = path.join(UPLOAD_ROOT, folder);
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, name), f.buffer);
      urls.push(fileUrl(folder, name));
    }
  }
  return urls;
}

/** In-memory upload for Excel/CSV imports. */
const sheetUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (_req, file, cb) =>
    /\.(xlsx|xls|csv)$/i.test(file.originalname) ? cb(null, true) : cb(badRequest('Upload an .xlsx, .xls or .csv file')),
});

/** Public URL for a locally stored file (CDN-aware). */
function fileUrl(folder, filename) {
  const base = config.storage.cdnUrl || `${config.publicUrl}/uploads`;
  return `${base}/${folder}/${filename}`;
}

module.exports = { UPLOAD_ROOT, imageUpload, storeImages, sheetUpload, fileUrl };
