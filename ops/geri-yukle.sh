#!/usr/bin/env bash
# Yedeği canlı veritabanına geri yükler. MEVCUT VERİYİ SİLER (veritabanı baştan kurulur).
# Kullanım: bash ops/geri-yukle.sh [--evet] <yedek-dizini>/makinist-<tarih>.dump
# Temiz sunucuda önce `docker compose --profile app up -d --build` ile hacim oluşturulur.
set -euo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/ortak.sh"

ONAY=hayir
if [ "${1:-}" = "--evet" ]; then ONAY=evet; shift; fi
DUMP="${1:-}"
[ -n "$DUMP" ] && [ -f "$DUMP" ] || hata "kullanım: bash ops/geri-yukle.sh [--evet] <makinist-<tarih>.dump>"
[ "$ONAY" = evet ] || hata "bu işlem canlı veritabanını SİLİP yedekten kurar. Emin olduğunuzda --evet ile tekrar çalıştırın."

db_calisiyor || hata "db servisi çalışmıyor (docker compose up -d db --wait)"
APP_CALISIYOR="$(dc ps -q --status running app 2>/dev/null || true)"
[ -z "$APP_CALISIYOR" ] || { echo "app durduruluyor"; dc stop app; }

echo "veritabanı yedekten kuruluyor: $DUMP"
db_sh 'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" -c "CREATE DATABASE \"$POSTGRES_DB\""'
db_sh 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$DUMP"

[ -z "$APP_CALISIYOR" ] || { echo "app başlatılıyor"; dc start app; }
echo "OK: geri yükleme tamam"
