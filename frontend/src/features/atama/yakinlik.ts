// Sınıra yakınlık + yorgunluk (F3, MK-176): sunucu yanıtını ekran verisine çeviren SAF işlevler. Yalnız bilgi; kırmızı/uyarı üretmez.
import type {
  YakinlikEsikleri,
  YakinlikIsareti,
  YakinlikSatiri,
  YakinlikYaniti,
  YorgunlukOzeti,
} from './atamaApi'

/** Dakika -> "10 s 20 dk", "11 s", "30 dk" (sunucudaki `ZamanHesabi.sure` ile aynı biçim). */
export function sureMetni(dk: number): string {
  const sa = Math.floor(dk / 60)
  const r = dk % 60
  if (sa === 0) return `${r} dk`
  return r === 0 ? `${sa} s` : `${sa} s ${r} dk`
}

export interface YorgunlukAlani {
  ad: string
  deger: string
  /** Satır içi kısa biçim: "38 s", "2 gece", "en kısa dinlenme 11 s". */
  kisa: string
  /** Eşiği aşan değer: kalın + simge + gizli metin (renk YOK, MK-175). */
  vurgulu: boolean
  esik: string
}

/** Üç ham değer ve her birinin eşiği (bileşik puan yok). */
export function yorgunlukAlanlari(y: YorgunlukOzeti, e: YakinlikEsikleri): YorgunlukAlani[] {
  return [
    {
      ad: 'Çalışma',
      deger: sureMetni(y.toplamDk),
      kisa: sureMetni(y.toplamDk),
      vurgulu: y.toplamDk > e.toplamSaat * 60,
      esik: `${e.toplamSaat} s üstü`,
    },
    {
      ad: 'Gece nöbeti',
      deger: `${y.geceNobetSayisi} adet`,
      kisa: `${y.geceNobetSayisi} gece`,
      vurgulu: y.geceNobetSayisi >= e.geceAdet,
      esik: `${e.geceAdet} ve üstü`,
    },
    {
      ad: 'En kısa dinlenme',
      deger: y.enKisaDinlenmeDk === null ? '—' : sureMetni(y.enKisaDinlenmeDk),
      kisa: `en kısa dinlenme ${y.enKisaDinlenmeDk === null ? '—' : sureMetni(y.enKisaDinlenmeDk)}`,
      vurgulu: y.enKisaDinlenmeDk !== null && y.enKisaDinlenmeDk < e.dinlenmeSaat * 60,
      esik: `${e.dinlenmeSaat} s altı`,
    },
  ]
}

/** Aday satırı için tek satırlık özet: "7 g: 38 s · 2 gece · en kısa dinlenme 11 s". */
export function yorgunlukOzeti(y: YorgunlukOzeti): string {
  const kisa = yorgunlukAlanlari(y, {
    pencereGun: y.pencereGun,
    toplamSaat: 0,
    geceAdet: 0,
    dinlenmeSaat: 0,
  }).map((x) => x.kisa)
  return `${y.pencereGun} g: ${kisa.join(' · ')}`
}

/** Aday ucu: makinist kimliği -> satır. Marj kapalıysa ya da yanıt yoksa boş (ekranda hiçbir işaret yok). */
export function adayHaritasi(y: YakinlikYaniti | null | undefined): Map<number, YakinlikSatiri> {
  const harita = new Map<number, YakinlikSatiri>()
  if (y?.marjAktif) for (const s of y.satirlar) harita.set(s.makinistId, s)
  return harita
}

/** Gün ucu: atama kimliği -> satır. */
export function atamaHaritasi(y: YakinlikYaniti | null | undefined): Map<number, YakinlikSatiri> {
  const harita = new Map<number, YakinlikSatiri>()
  if (y?.marjAktif) for (const s of y.satirlar) if (s.atamaId !== null) harita.set(s.atamaId, s)
  return harita
}

/** İşaretlerin ölçüm metinleri ("Günlük süre ...; Haftalık dinlenme ..."); rozet metninden bağımsız. */
export const isaretMetinleri = (isaretler: YakinlikIsareti[]): string =>
  isaretler.map((i) => i.metin).join('; ')

/** Masa işareti `title`/erişilebilir adı. */
export function isaretlerMetni(isaretler: YakinlikIsareti[]): string {
  return isaretler.length === 0 ? '' : `Sınıra yakın: ${isaretMetinleri(isaretler)}`
}
