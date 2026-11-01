// Hızlı doldurma saf işlevleri (MK-150, 151, 154, 155): Vue/API içe aktarmaz. Sunucu yine de her atamayı yeniden değerlendirir.
import type { Aday, AtamaRolu, GunMakinisti, GunSatiri } from './atamaApi'

/** Temiz aday: uygun, bu seferde görevli değil, hiç ihlali (uyarı dahil) yok. */
const temizMi = (a: Aday) => a.uygun && a.buSeferdeRol === null && a.ihlaller.length === 0

/** "Önerilen" = sunucu sırasındaki ilk temiz aday (sıra `AtamaServisi.adaylar` ile aynı); yoksa null. */
export const onerilenAday = (adaylar: Aday[]): Aday | null => adaylar.find(temizMi) ?? null

/** OK-34: yalnız temiz aday, yolcu olmayan rol, rolde mevcut kimse yok → onaysız atanır. */
export const hizliAtanabilir = (aday: Aday, rol: AtamaRolu, mevcut: GunMakinisti | null) =>
  rol !== 'YOLCU' && mevcut === null && temizMi(aday)

const bosMu = (s: GunSatiri) => s.durum !== 'IPTAL' && !s.makinistler.some((m) => m.rol === 'ASIL')

/** `suAnId`'den sonraki ilk boş (iptalsiz, ASIL'ı olmayan) sefer; sona gelince başa sarar, kendisini döndürmez; yoksa null. */
export function sonrakiBos(satirlar: GunSatiri[], suAnId: number | null): number | null {
  const i = satirlar.findIndex((s) => s.seferId === suAnId)
  const sirali = [...satirlar.slice(i + 1), ...satirlar.slice(0, Math.max(i, 0))]
  return sirali.find(bosMu)?.seferId ?? null
}

/** Havuz ortalaması bundan düşükse (saat) fark gösterilmez (OK-39). */
export const EN_AZ_ORTALAMA_SAAT = 1

/**
 * OK-35: aday havuzunun (panelde listelenen) bu ay saat ortalamasına göre fark, işaretli tam sayı yüzde. Yalnız bilgi:
 * hiçbir sıralama/kural onu kullanmaz. YOLCU'da havuz karma olduğundan boş; ortalama {@link EN_AZ_ORTALAMA_SAAT}'in altındaysa null
 * (OK-39: ay başında bir kişi atanınca 0 saatli herkes "-%100" görünmesin).
 */
export function ortalamayaFark(adaylar: Aday[], rol: AtamaRolu): Map<number, number | null> {
  const fark = new Map<number, number | null>()
  if (rol === 'YOLCU' || adaylar.length === 0) return fark
  const ort = adaylar.reduce((t, a) => t + a.buAySaat, 0) / adaylar.length
  for (const a of adaylar)
    fark.set(
      a.makinistId,
      ort < EN_AZ_ORTALAMA_SAAT ? null : Math.round(((a.buAySaat - ort) / ort) * 100) || 0,
    )
  return fark
}
