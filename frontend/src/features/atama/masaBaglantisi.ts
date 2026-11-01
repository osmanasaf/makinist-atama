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

/** `/atama?tarih=…&suzgec=…`: ana sayfadaki "yapılacaklar" ve sayaç bağlantıları tek yerden üretilir. */
export function masaBaglantisi(tarih: string, suzgec?: MasaSuzgeci): RouteLocationRaw {
  return { name: 'atama', query: suzgec ? { tarih, suzgec } : { tarih } }
}
