#!/usr/bin/env bash
# Veritabanı yedeği (NFR-08). Kullanım: bash ops/yedekle.sh
# Çıktı: $YEDEK_DIZINI/makinist-<tarih>.dump (YEDEK_DIZINI OneDrive içinde olamaz). 14 günden eskiler silinir.
set -euo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/ortak.sh"

db_calisiyor || hata "db servisi çalışmıyor (docker compose up -d db --wait)"
HEDEF="$(yedek_dizini)"
ZAMAN="$(date +%Y%m%d-%H%M%S)"
DUMP="$HEDEF/makinist-$ZAMAN.dump"
trap 'rm -f "$DUMP.part"' EXIT

echo "veritabanı (pg_dump) -> $DUMP"
db_sh 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$DUMP.part"
[ -s "$DUMP.part" ] || hata "pg_dump boş çıktı verdi"
mv "$DUMP.part" "$DUMP"

# 14 gün saklama
find "$HEDEF" -maxdepth 1 -name 'makinist-*.dump' -mtime +14 -delete

echo "tamam: $DUMP ($(wc -c < "$DUMP") bayt)"
