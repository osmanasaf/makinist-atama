# Yedek betiklerinin ortak kısmı (kaynak olarak yüklenir: `. "$(dirname "$0")/ortak.sh"`). Tek başına çalıştırılmaz.
# Git Bash'te (Windows) ve Linux'ta çalışır; ana makinede psql/pg_dump gerekmez, hepsi `docker compose exec` ile.
# Dosya deposu yoktur (Excel diskte saklanmaz): yedek yalnız veritabanıdır.

KOK="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$KOK" || exit 1

# Git Bash, "/veri/..." gibi argümanları docker.exe'ye geçerken Windows yoluna çevirmesin
export MSYS_NO_PATHCONV=1

dc() { docker compose --profile app "$@"; }
hata() { echo "HATA: $*" >&2; exit 1; }

# .env'den tek değer okur (yalnız satır başı KEY=; CR ve çevreleyici tırnak atılır). Ortam değişkeni önceliklidir.
env_oku() {
  local ad="$1" varsayilan="${2:-}" deger
  deger="${!ad:-}"
  if [ -z "$deger" ] && [ -f "$KOK/.env" ]; then
    deger="$(grep -E "^${ad}=" "$KOK/.env" | tail -n1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/")"
  fi
  echo "${deger:-$varsayilan}"
}

# Yedek dizinini çözer; OneDrive içindeyse (yol ya da gerçek yol) reddeder (senkron kilidi/bozulma).
yedek_dizini() {
  local d gercek
  d="$(env_oku YEDEK_DIZINI "$HOME/makinist-yedek")"
  onedrive_mi "$d"
  mkdir -p "$d" || hata "yedek dizini oluşturulamadı: $d"
  gercek="$(cd "$d" && pwd -P)"
  onedrive_mi "$gercek"   # junction/sembolik bağ OneDrive'a çıkıyorsa
  echo "$gercek"
}
onedrive_mi() {
  case "$(echo "$1" | tr '[:upper:]' '[:lower:]')" in
    *onedrive*) hata "YEDEK_DIZINI OneDrive içinde olamaz ($1). .env içinde YEDEK_DIZINI'ni OneDrive dışında bir klasöre ayarlayın." ;;
  esac
}

db_calisiyor() { [ -n "$(dc ps -q --status running db 2>/dev/null)" ]; }

# Konteyner içinde (db) kabuk komutu; ortam: POSTGRES_USER, POSTGRES_DB
db_sh() { dc exec -T db sh -c "$@"; }

# Her tablonun satır sayısı (ad|sayı), sıralı; ilk argüman veritabanı adı (varsayılan: canlı)
satir_sayilari() {
  local vt="${1:-}"
  db_sh "psql -U \"\$POSTGRES_USER\" -d \"${vt:-\$POSTGRES_DB}\" -At -F'|' -c \"
    SELECT table_name, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text
    FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY 1\""
}
