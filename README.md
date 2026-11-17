# E-Commerce Platform — Android App, Admin Panel & Backend

SyncMinds IT · Kotlin (Android) · React (Admin) · Node.js + MySQL (API)

```
shopapp/
├── backend/   Node.js (Express) REST API + MySQL schema, GST invoices, Razorpay, FCM, bulk import
├── admin/     React (Vite) admin web panel
├── android/   Native Android app — Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26)
└── deploy/    Nginx, PM2 and daily-backup configuration
```

---

## 1. Run everything locally (about 10 minutes)

**Requirements:** Node.js 18+, MySQL 8 (or MariaDB 10.6+), Android Studio (Ladybug or newer, with JDK 17).

### Backend
```bash
cd backend
cp .env.example .env            # edit DB credentials
npm install
npm run migrate                 # creates the database tables
node database/seed.js --demo    # creates admin@example.com / Admin@123 + sample catalog
npm run dev                     # http://localhost:4000
```
In development:
- **OTP is always `123456`** and is printed to the console (`OTP_PROVIDER=console`).
- **Razorpay runs in mock mode** while the keys in `.env` are the placeholders, so orders can be placed without real keys. Put real **test** keys (`rzp_test_…`) in `.env` to use the real Razorpay checkout.

### Admin panel
```bash
cd admin
npm install
npm run dev                     # http://localhost:5173  (proxies /api to :4000)
```
Log in with `admin@example.com` / `Admin@123`, then change the password under **Settings**.

### Android app
1. Open the `android/` folder in Android Studio and let Gradle sync.
2. Run on an **emulator**. The debug build talks to `http://10.0.2.2:4000/`, which is your computer's `localhost`.
   For a physical phone on the same Wi-Fi, change `API_BASE_URL` for `debug` in `app/build.gradle.kts` to your computer's LAN IP, and add that IP to `res/xml/network_security_config.xml`.
3. Log in with any valid 10-digit mobile number (e.g. `9876543210`) and OTP `123456`.

---

## 2. What's included

### Android app (`android/`)
| Area | Screens / behaviour |
|---|---|
| Auth | Mobile + OTP login with a resend timer; first-time profile step; session stored in DataStore; auto-logout on expiry |
| Home | Auto-scrolling banner carousel (banners can open a product, category or URL), categories, Featured / Popular / New arrivals, pull-to-refresh |
| Catalog | Category tree with subcategories; product grid with infinite scroll; search; filter by price range; sort by newest, popular or price |
| Product | Image gallery, weight/quantity variant chips with per-variant price and MRP, live stock ("Only 3 left"), specifications, related products, wishlist |
| Cart | Quantity stepper, stock validation, coupon entry and coupon list, free-delivery nudge, itemised bill |
| Checkout | Saved addresses (add/edit, Indian state picker, validation), Razorpay Checkout (UPI, cards, net banking, wallets), server-side payment verification |
| Orders | Active/past tabs, order tracking timeline, cancellation with reason, reorder, **GST invoice PDF download** |
| Account | Edit profile (name, email, GSTIN), addresses, wishlist, payment history, logout, **delete account** (required by Google Play) |
| Push | FCM: order placed, confirmed, packed, shipped, out for delivery and delivered notifications, plus promotional broadcasts; tapping one opens the order or product |

### Admin panel (`admin/`)
Dashboard (sales today/month/total, 30-day chart, low-stock alerts, recent orders) · Products (search, filters, variants, multi-image upload, specifications, GST/HSN, activate/deactivate) · **Bulk upload** (Excel/CSV template, validate-before-import, error report, bulk price/stock update, catalog export) · Categories & subcategories · Orders (filters, status workflow, cancel + auto-refund, partial refunds, invoice, Excel/CSV export) · Coupons (fixed/percentage, cap, minimum order, validity, total and per-customer limits) · Banners · Customers (profile, order history, block/unblock) · Reports (daily/weekly/monthly sales, product-wise, orders, stock, low stock — all exportable) · Settings (password, promotional push, staff accounts with roles).

### Backend (`backend/`)
- REST API at `/api/v1/…` (customer) and `/api/v1/admin/…` (admin), JWT auth, Zod input validation, rate-limited OTP and login.
- **Stock is reserved when an order is created** and released automatically if payment isn't completed within 30 minutes. Row locking prevents overselling.
- Razorpay: order creation, signature verification, refunds, and a webhook (`POST /api/v1/webhooks/razorpay`) that confirms payments even if the app closes mid-payment.
- GST invoices: prices are treated as GST-inclusive. Intra-state orders get CGST + SGST; inter-state orders get IGST.
- Caching for home, categories and banners; pagination everywhere; indexed queries. Built for 1,000+ products, and the import has been tested at that scale.
- `/privacy` serves a privacy-policy page for the Play Store listing. **It is a template: fill in the bracketed details and have the client review it.**

---

## 3. Production setup

### What the client must provide
| Item | Where it goes |
|---|---|
| Razorpay **live** Key ID + Secret, and a webhook secret | `backend/.env` → `RAZORPAY_*`. In the Razorpay Dashboard → Webhooks, add `https://api.yourstore.com/api/v1/webhooks/razorpay` with events `payment.captured` and `payment.failed` |
| SMS provider for OTP (MSG91 recommended for India; DLT-registered template) | `OTP_PROVIDER=msg91`, `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID` |
| Firebase project | Android: download `google-services.json` into `android/app/`. Backend: service-account JSON path in `FIREBASE_SERVICE_ACCOUNT` |
| Store legal details (name, address, GSTIN, state) | `STORE_*` in `backend/.env`; these are printed on invoices |
| Domain names | e.g. `api.yourstore.com`, `admin.yourstore.com` |
| Google Play developer account | One-time $25 fee, paid by the client |

### Server (Ubuntu 22.04+ VPS, 2 GB RAM is enough to start)
```bash
# Install Node 20, MySQL 8, Nginx, PM2, Certbot
sudo mkdir -p /var/www/shop /var/log/shop && sudo chown $USER /var/www/shop /var/log/shop
# copy backend/ and admin/ to /var/www/shop/
cd /var/www/shop/backend && npm ci --omit=dev && cp .env.example .env   # set NODE_ENV=production, real secrets, a long JWT_SECRET
npm run migrate && npm run seed
cd ../admin && npm ci && npm run build
pm2 start /path/to/deploy/ecosystem.config.js && pm2 save && pm2 startup
sudo cp deploy/nginx.conf /etc/nginx/sites-available/shop && sudo ln -s /etc/nginx/sites-available/shop /etc/nginx/sites-enabled/
sudo certbot --nginx -d api.yourstore.com -d admin.yourstore.com
```
Then set `PUBLIC_URL=https://api.yourstore.com` and `CORS_ORIGINS=https://admin.yourstore.com`.

**Daily backups:** install `deploy/backup.sh` as described in its header (cron at 02:30, 14-day retention). Set `S3_BUCKET` to also keep an off-site copy, which is strongly recommended.

**CDN for images:** put Cloudflare (or CloudFront) in front of `api.yourstore.com/uploads/` and set `CDN_URL` so new image URLs point at the CDN.

### Android release
1. **Change the package name** in `app/build.gradle.kts` (`applicationId`, e.g. `com.clientname.shop`). It can never change after the first upload.
2. Set the app name in `res/values/strings.xml`. Replace the placeholder icon in `res/drawable/ic_bag.xml` (or use Android Studio → New → Image Asset), and set the brand colour in `ui/theme/Theme.kt` and `res/values/colors.xml`.
3. Set the release `API_BASE_URL` in `app/build.gradle.kts`.
4. Create an upload keystore (Build → Generate Signed Bundle), then create `android/keystore.properties`:
   ```
   storeFile=upload-keystore.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
   Back up the keystore and passwords somewhere safe. **Never commit them.**
5. `./gradlew bundleRelease` → upload `app/build/outputs/bundle/release/app-release.aab` to Play Console.

**Play Console checklist:** privacy policy URL (`https://api.yourstore.com/privacy`) · Data safety form (collects phone number, name, email, address, purchase history; shared with payment processor; deletion available in-app) · Content rating questionnaire · Target audience · App access instructions for reviewers (give them a test mobile number; you can whitelist one with a fixed OTP) · Screenshots (phone, at least 2) · 512×512 icon · 1024×500 feature graphic.

### Live preview on Vercel (admin panel + API)
**Live preview: https://kutuku-seven.vercel.app** (TiDB Cloud Starter in Singapore, `DEMO_MODE=1`). One Vercel project serves the admin panel and the API on the same domain (`vercel.json` + `api/index.js` at the repo root). The Android app can't run on Vercel; point its `API_BASE_URL` at the Vercel domain to test it against the live API.

1. **Create a free MySQL database.** [TiDB Cloud Starter](https://tidbcloud.com) works well: create a free instance (Singapore, to match the `sin1` Vercel region in `vercel.json`), run `CREATE DATABASE shop;` in its SQL Editor, and copy the connection string (`mysql://USER:PASSWORD@HOST:4000/shop`). Aiven's free MySQL also works.
2. **Import the GitHub repo in Vercel:** New Project → pick the repo → keep the root directory as `/`. The framework preset can stay "Other". `vercel.json` sets the build.
3. **Add environment variables** (Settings → Environment Variables):

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | the connection string from step 1 |
   | `DB_SSL` | `1` |
   | `JWT_SECRET` | a long random string |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | the first admin login |
   | `DEMO_MODE` | `1` for the preview: OTP is always `123456` and payments are simulated. **Remove it for the real store.** |
   | `SEED_DEMO` | `1` to load the sample catalog into an empty database |
   | `CRON_SECRET` | any random string |
   | `STORE_NAME`, `STORE_GSTIN`, `STORE_STATE`, `STORE_ADDRESS` | printed on invoices |
   | `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | test keys, when you want real Razorpay checkout |

4. **Image uploads:** Storage → Create → Blob → connect it to the project. This sets `BLOB_READ_WRITE_TOKEN`, and uploads then go to Vercel Blob.
5. **Deploy.** The first API request creates the tables, the admin account and (with `SEED_DEMO=1`) the sample catalog automatically. Open `https://<project>.vercel.app` and log in to the admin panel.

Vercel limits to know about: request bodies are capped at 4.5 MB (the admin uploads images one at a time, max 4 MB each), and on the free plan the cron job that releases abandoned-checkout stock runs once a day. Stock is also released whenever a new order is placed. For the real launch, the VPS setup above avoids both limits.

---

## 4. API overview

| Customer (`/api/v1`) | Admin (`/api/v1/admin`) |
|---|---|
| `POST auth/otp/send`, `auth/otp/verify`, `auth/logout` | `POST auth/login`, `GET auth/me`, staff `auth/users` |
| `GET home`, `categories`, `products?q&category_id&min_price&max_price&sort&page`, `products/:id`, `search/suggest` | `GET dashboard` |
| `GET/POST/PUT/DELETE cart`, `POST cart/apply-coupon`, `GET coupons` | `products` CRUD, `variants/:id/stock`, `uploads/:folder` |
| `GET/POST/DELETE wishlist/:productId` | `products-import` (+ `?dry_run=1`), `products-import/template`, `products-export` |
| `GET/PUT/DELETE profile`, `profile/addresses` CRUD, `profile/fcm-token` | `categories`, `coupons`, `banners` CRUD |
| `POST orders` → Razorpay order; `POST orders/:id/verify-payment`; `orders/:id/payment-failed` | `orders` list/detail/export, `orders/:id/status`, `/cancel`, `/refund`, `/payment-status`, `/invoice` |
| `GET orders`, `orders/:id`, `POST orders/:id/cancel`, `orders/:id/reorder`, `GET orders/:id/invoice`, `orders/payments/history` | `customers` list/detail/status; `reports/sales|products|orders|stock` (`?export=1&format=csv`); `notifications/broadcast` |

The backend uses Node.js. The proposal allowed Laravel or Node.js, and Node keeps the admin panel and API in a single language.

---

## 5. Testing status
- **Backend:** tested end to end against MariaDB: OTP login, catalog, search, filters, cart, stock limits, coupons (including per-customer reuse), order placement with stock reservation, payment signature verification (valid and invalid), webhook confirmation (including replay), payment failure releasing stock, cancellation with refund and restock, reorder, invoice PDF, admin product CRUD, bulk import with validation errors, exports, status workflow, partial refunds, reports, customer blocking, and role checks.
- **Admin panel:** production build tested in a browser against the API; no console errors.
- **Android:** written for current stable libraries (AGP 8.7, Kotlin 2.0.21, Compose BOM 2024.12). It was **not** compiled in the build sandbox because Google's Maven repository wasn't reachable there. Expect the first Gradle sync in Android Studio to work, and fix any small compile issues it reports before QA on real devices. Run the full payment flow with Razorpay **test** keys before going live.
