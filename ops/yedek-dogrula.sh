#!/usr/bin/env bash
# NFR-08: yedeği alır, GEÇİCİ bir veritabanına geri yükler ve tablo satır sayılarını kaynakla karşılaştırır. Canlı veriye yazmaz.
# Başarıda son satır: OK. Kullanım: bash ops/yedek-dogrula.sh
set -euo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/ortak.sh"

db_calisiyor || hata "db servisi çalışmıyor (docker compose up -d db --wait)"
GECICI="$(mktemp -d)"
GECICI_VT="makinist_dogrula_$$"
temizle() {
  db_sh "psql -U \"\$POSTGRES_USER\" -d postgres -c 'DROP DATABASE IF EXISTS $GECICI_VT WITH (FORCE)'" >/dev/null 2>&1 || true
  rm -rf "$GECICI"
}
trap temizle EXIT

ONCE="$(satir_sayilari)"
[ -n "$ONCE" ] || hata "kaynak veritabanında tablo yok (uygulama hiç açılmadı mı?)"

# gerçek yedek betiği, geçici dizine
YEDEK_DIZINI="$GECICI" bash "$KOK/ops/yedekle.sh"
DUMP="$(ls "$GECICI"/makinist-*.dump)"

SONRA="$(satir_sayilari)"
[ "$ONCE" = "$SONRA" ] || hata "kaynak veritabanı yedek sırasında değişti; sessiz bir anda yeniden deneyin"

echo "geçici veritabanına geri yükleniyor: $GECICI_VT"
db_sh "psql -U \"\$POSTGRES_USER\" -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE $GECICI_VT'" >/dev/null
db_sh "pg_restore -U \"\$POSTGRES_USER\" -d $GECICI_VT --no-owner --exit-on-error" < "$DUMP"
GERI="$(satir_sayilari "$GECICI_VT")"

if [ "$ONCE" != "$GERI" ]; then
  echo "--- satır sayıları (kaynak | geri yüklenen) ---" >&2
  diff <(echo "$ONCE") <(echo "$GERI") >&2 || true
  hata "satır sayıları eşit değil"
fi
TABLO="$(echo "$ONCE" | wc -l | tr -d ' ')"
TOPLAM="$(echo "$ONCE" | awk -F'|' '{s += $2} END {print s}')"
echo "tablo: $TABLO, toplam satır: $TOPLAM (kaynak = geri yüklenen)"
echo "$ONCE" | awk -F'|' '$2 > 0 {printf "  %s=%s", $1, $2} END {print ""}'

[ "$TOPLAM" -gt 0 ] || echo "UYARI: tüm tablolar boş; doğrulama yalnız şemayı sınadı. Örnek veriyle tekrarlayın." >&2
echo OK
