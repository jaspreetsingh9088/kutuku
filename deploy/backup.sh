#!/usr/bin/env bash
# Daily MySQL backup with 14-day retention.
# Install:  sudo cp backup.sh /usr/local/bin/shop-backup && sudo chmod +x /usr/local/bin/shop-backup
# Cron:     30 2 * * * /usr/local/bin/shop-backup >> /var/log/shop-backup.log 2>&1
# Optional off-site copy: set S3_BUCKET (requires aws cli) — strongly recommended.
set -euo pipefail
ENV_FILE="${ENV_FILE:-/var/www/shop/backend/.env}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/shop}"
KEEP_DAYS="${KEEP_DAYS:-14}"

# shellcheck disable=SC1090
set -a; source <(grep -E '^(DB_HOST|DB_PORT|DB_USER|DB_PASSWORD|DB_NAME)=' "$ENV_FILE"); set +a
mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y-%m-%d_%H%M)
FILE="$BACKUP_DIR/${DB_NAME}_${STAMP}.sql.gz"

MYSQL_PWD="$DB_PASSWORD" mysqldump -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USER" \
  --single-transaction --quick --routines --triggers --no-tablespaces "$DB_NAME" | gzip -9 > "$FILE"

# Also archive uploaded images (skipped if a CDN/object storage is used instead)
UPLOADS="$(dirname "$ENV_FILE")/uploads"
[ -d "$UPLOADS" ] && tar -czf "$BACKUP_DIR/uploads_${STAMP}.tar.gz" -C "$(dirname "$UPLOADS")" uploads

if [ -n "${S3_BUCKET:-}" ]; then
  aws s3 cp "$FILE" "s3://$S3_BUCKET/db/"
  [ -f "$BACKUP_DIR/uploads_${STAMP}.tar.gz" ] && aws s3 cp "$BACKUP_DIR/uploads_${STAMP}.tar.gz" "s3://$S3_BUCKET/uploads/"
fi

find "$BACKUP_DIR" -type f -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) backup ok: $FILE ($(du -h "$FILE" | cut -f1))"
