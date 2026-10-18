import { ref } from 'vue'
import { saatBicimle } from '@/lib/tarih'
import { parametreApi } from '@/features/parametre/parametreApi'

/**
 * Gece penceresi (dakika): ayar `gece.baslangic` / `gece.bitis`. Ayar okunana dek (ya da okunamazsa)
 * ayar varsayılanı 20:00–06:00 geçerlidir; masa her açılışta `geceAyariYukle` ile tazeler.
 * Yalnız görsel ipucudur: kural değerlendirmesi sunucuda, ayardaki aynı pencereyle yapılır.
 */
export const geceAraligi = ref({ baslangicDk: 20 * 60, bitisDk: 6 * 60 })

const SAAT_DESENI = /^(\d{1,2}):(\d{2})$/
const dakika = (s: string | undefined): number | null => {
  const e = SAAT_DESENI.exec(s ?? '')
  if (!e) return null
  const sa = Number(e[1])
  const dk = Number(e[2])
  return sa < 24 && dk < 60 ? sa * 60 + dk : null
}

/** Gece ayarını okur (`GET /api/parametre`, her rol okuyabilir); okunamazsa mevcut aralık kalır. */
export async function geceAyariYukle(): Promise<void> {
  try {
    const liste = await parametreApi.parametreler()
    const al = (anahtar: string) => dakika(liste.find((p) => p.anahtar === anahtar)?.deger)
    const baslangicDk = al('gece.baslangic')
    const bitisDk = al('gece.bitis')
    if (baslangicDk !== null && bitisDk !== null) geceAraligi.value = { baslangicDk, bitisDk }
  } catch {
    // ayar okunamadı: varsayılan pencere ile devam (yalnız görsel ipucu)
  }
}

const geceSaati = (z: string, { baslangicDk, bitisDk }: typeof geceAraligi.value) => {
  const [sa, dk] = saatBicimle(z).split(':').map(Number)
  const t = sa! * 60 + dk!
  // Gece yarısını aşan pencere (20:00–06:00) ya da aynı gün içinde kalan pencere (ör. 01:00–05:00)
  return baslangicDk > bitisDk ? t >= baslangicDk || t < bitisDk : t >= baslangicDk && t < bitisDk
}

/** Kalkış ya da varış gece aralığındaysa ya da sefer ertesi güne sarkıyorsa gece görevidir. */
export function geceMi(kalkis: string, varis: string, aralik = geceAraligi.value): boolean {
  const gun = (z: string) =>
    new Date(z).toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' })
  return geceSaati(kalkis, aralik) || geceSaati(varis, aralik) || gun(kalkis) !== gun(varis)
}
