import { sayiBicimle } from '@/lib/sayi'

/**
 * Doluluk eğrisinin saf geometrisi (MK-141): ölçekler, yol dizesi, eksen tikleri, hedef çizgileri.
 * Bileşen yalnız bunları çizer; DOM ve renk burada yoktur.
 */
export interface EgriNokta {
  ek: number
  bos: number
  dolulukYuzde: number
}
export interface EgriHedef {
  yuzde: number
  ulasildi: boolean
  ek: number | null
}
export interface Cerceve {
  genislik: number
  yukseklik: number
  sol: number
  sag: number
  ust: number
  alt: number
}
export interface Olcek {
  cerceve: Cerceve
  xMin: number
  xMax: number
  yMin: number
  yMax: number
  x: (ek: number) => number
  y: (dolulukYuzde: number) => number
}
export interface HedefCizgisi {
  yuzde: number
  y: number
  /** Çakışmayı önlemek için en az `aralik` px ayrılmış etiket konumu (çizgiden az sapar). */
  etiketY: number
  etiket: string
  ulasildi: boolean
  ek: number | null
}

/** Sağ kenar boşluğu hedef etiketlerine ayrılmıştır ("doğrudan etiket": gösterge kutusu yok). */
export const VARSAYILAN_CERCEVE: Cerceve = {
  genislik: 720,
  yukseklik: 300,
  sol: 52,
  sag: 92,
  ust: 16,
  alt: 48,
}
/** Çizilen nokta tavanı; daha uzun eğri düzgün aralıkla örneklenir (tablo her zaman tamamını gösterir). */
export const AZAMI_NOKTA = 60

const yuvarla = (n: number, basamak = 2) => {
  const k = 10 ** basamak
  return Math.round(n * k) / k + 0 // -0 -> 0
}

/** İlk ve son nokta korunur; en çok `azami` nokta döner. */
export function ornekle<T>(noktalar: readonly T[], azami: number = AZAMI_NOKTA): T[] {
  if (noktalar.length <= azami) return [...noktalar]
  const son = noktalar.length - 1
  const sonuc: T[] = []
  let onceki = -1
  for (let i = 0; i < azami; i++) {
    const k = Math.round((i * son) / (azami - 1))
    if (k !== onceki) sonuc.push(noktalar[k]!)
    onceki = k
  }
  return sonuc
}

/** 1-2-5 dizisinden `ham` değere en yakın üst "güzel" adım. */
export function guzelAdim(ham: number): number {
  if (!(ham > 0)) return 1
  const us = 10 ** Math.floor(Math.log10(ham))
  const oran = ham / us
  return (oran <= 1 ? 1 : oran <= 2 ? 2 : oran <= 5 ? 5 : 10) * us
}

/** [min, max] içinde yaklaşık `adet` güzel tik; `tamsayi` ise adım en az 1. */
export function eksenTikleri(min: number, max: number, adet = 5, tamsayi = false): number[] {
  if (!(max > min)) return [min]
  let adim = guzelAdim((max - min) / adet)
  if (tamsayi) adim = Math.max(1, Math.round(adim))
  const sonuc: number[] = []
  for (let v = Math.ceil(min / adim - 1e-9) * adim; v <= max + 1e-9; v += adim) {
    sonuc.push(yuvarla(v, 6))
  }
  return sonuc
}

/**
 * Ölçekler. X: ek makinist (tek nokta ya da hepsi aynı ise tek nokta ortalanır). Y: doluluk %; üst sınır her zaman 100,
 * alt sınır veri ve hedeflerin en düşüğünün güzel adıma aşağı yuvarlanmışı (hedef çizgileri hep görünür).
 */
export function olcekKur(
  noktalar: readonly EgriNokta[],
  hedefYuzdeleri: readonly number[] = [],
  cerceve: Cerceve = VARSAYILAN_CERCEVE,
): Olcek {
  const eksler = noktalar.map((n) => n.ek)
  let xMin = eksler.length ? Math.min(...eksler) : 0
  let xMax = eksler.length ? Math.max(...eksler) : 1
  if (xMax === xMin) {
    xMin -= 0.5
    xMax += 0.5
  }
  const yuzdeler = [...noktalar.map((n) => n.dolulukYuzde), ...hedefYuzdeleri]
  const yAlt = yuzdeler.length ? Math.min(...yuzdeler) : 90
  const yMax = 100
  const adim = guzelAdim((yMax - Math.min(yAlt, 99)) / 4)
  const yMin = Math.max(0, Math.min(Math.floor(yAlt / adim) * adim, yMax - adim))

  const genislik = cerceve.genislik - cerceve.sol - cerceve.sag
  const yukseklik = cerceve.yukseklik - cerceve.ust - cerceve.alt
  return {
    cerceve,
    xMin,
    xMax,
    yMin,
    yMax,
    x: (ek) => cerceve.sol + ((ek - xMin) / (xMax - xMin)) * genislik,
    y: (d) => cerceve.ust + ((yMax - d) / (yMax - yMin)) * yukseklik,
  }
}

export const noktaKonumu = (n: EgriNokta, o: Olcek) => ({
  x: yuvarla(o.x(n.ek)),
  y: yuvarla(o.y(n.dolulukYuzde)),
})

/** SVG `d` dizesi (M/L); boş eğri boş dize, tek nokta yalnız "M". */
export function yolDizesi(noktalar: readonly EgriNokta[], o: Olcek): string {
  return noktalar
    .map((n, i) => {
      const { x, y } = noktaKonumu(n, o)
      return `${i === 0 ? 'M' : 'L'}${x} ${y}`
    })
    .join(' ')
}

/** Hedef çizgileri üstten alta (100 -> 98); etiketler en az `aralik` px ayrılır. */
export function hedefCizgileri(
  hedefler: readonly EgriHedef[],
  o: Olcek,
  aralik = 14,
): HedefCizgisi[] {
  const sirali = [...hedefler]
    .filter((h) => h.yuzde >= o.yMin && h.yuzde <= o.yMax)
    .sort((a, b) => b.yuzde - a.yuzde)
  let onceki = -Infinity
  return sirali.map((h) => {
    const y = yuvarla(o.y(h.yuzde))
    const etiketY = Math.max(y, onceki + aralik)
    onceki = etiketY
    return {
      yuzde: h.yuzde,
      y,
      etiketY,
      etiket: `%${sayiBicimle(h.yuzde)} hedef`,
      ulasildi: h.ulasildi,
      ek: h.ek,
    }
  })
}

/** Fare/klavye ipucu: görünüm-kutusu x konumuna en yakın noktanın dizini; boşsa -1. */
export function enYakinIndeks(noktalar: readonly EgriNokta[], o: Olcek, xPx: number): number {
  let en = -1
  let fark = Infinity
  noktalar.forEach((n, i) => {
    const f = Math.abs(o.x(n.ek) - xPx)
    if (f < fark) {
      fark = f
      en = i
    }
  })
  return en
}

/** Ekran okuyucu özeti (`role="img"` aria-label): uçlar ve hedefler; ayrıntı tablo görünümündedir. */
export function ozetMetni(noktalar: readonly EgriNokta[], hedefler: readonly EgriHedef[]): string {
  if (noktalar.length === 0) return 'Doluluk eğrisi: veri yok.'
  const ilk = noktalar[0]!
  const son = noktalar[noktalar.length - 1]!
  const parca = [
    `Doluluk eğrisi, ${noktalar.length} nokta: ek makinist ${ilk.ek} iken doluluk %${sayiBicimle(ilk.dolulukYuzde)}`,
  ]
  if (noktalar.length > 1) {
    parca[0] += `, ek makinist ${son.ek} iken %${sayiBicimle(son.dolulukYuzde)}`
  }
  const h = hedefler.map(
    (x) =>
      `%${sayiBicimle(x.yuzde)} için ${x.ulasildi && x.ek != null ? `+${x.ek}` : 'ulaşılamadı'}`,
  )
  if (h.length) parca.push(`Hedefler: ${h.join(', ')}`)
  parca.push('Ayrıntılı değerler tablo görünümünde')
  return parca.join('. ') + '.'
}
