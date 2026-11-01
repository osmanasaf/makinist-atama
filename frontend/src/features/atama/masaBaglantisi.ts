import type { RouteLocationRaw } from 'vue-router'

/** Atama masasının satır süzgeçleri; adres sorgusundaki `suzgec` değerleri (derin bağlantı sözleşmesi, MK-156). `bitmeyen` = atanmamışlardan henüz bitmeyenler (ana sayfa panosunun sayısıyla aynı küme; D1/QA-7). */
export const MASA_SUZGECLERI = [
  'atanmamis',
  'bitmeyen',
  'kirmizi',
  'konduktor',
  'gormeyen',
] as const
export type MasaSuzgeci = (typeof MASA_SUZGECLERI)[number]

/** Sorgudaki değeri süzgece çevirir; bilinmeyen değer, dizi ya da boş → null (yok sayılır). */
export function suzgecOku(deger: unknown): MasaSuzgeci | null {
  return MASA_SUZGECLERI.find((s) => s === deger) ?? null
}

/** Sorgudaki `sefer` değeri: pozitif tam sayı kimlik, aksi halde (bozuk, dizi, boş) null. */
export function seferOku(deger: unknown): number | null {
  const n = typeof deger === 'string' && /^\d+$/.test(deger) ? Number(deger) : 0
  return Number.isSafeInteger(n) && n > 0 ? n : null
}

/**
 * `/atama?tarih=…&suzgec=…&sefer=…`: ana sayfadaki "yapılacaklar", sayaç bağlantıları ve aylık çizelge hücreleri (F5, AK-5.6: gün + sefer seçili) tek yerden üretilir.
 * `sefer`: gün yüklenince o sefer seçilir (o günde yoksa yok sayılır).
 */
export function masaBaglantisi(
  tarih: string,
  suzgec?: MasaSuzgeci,
  seferId?: number,
): RouteLocationRaw {
  return {
    name: 'atama',
    query: { tarih, ...(suzgec ? { suzgec } : {}), ...(seferId ? { sefer: String(seferId) } : {}) },
  }
}
