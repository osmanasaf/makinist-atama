// Atama masası zaman çizelgesi: saf geometri (MK-159). Vue/API import etmez; saat dilimi Europe/Istanbul (+03:00, yaz saati yok).
import type { Tarih, Zaman } from '@/api/tipler'

export interface SeferZamani {
  planlananKalkis: Zaman
  guncelKalkis: Zaman
  guncelVaris: Zaman
  kalkisGecikmeDk: number
}
/** Seçili günün 00:00'ından itibaren dakika; pencere gün sınırını (1440) aşmaz, yalnız gecikmeyle ertesi güne kayan kalkışlar için genişler. */
export interface Pencere {
  basDk: number
  bitDk: number
}
export interface Cubuk {
  solYuzde: number
  genYuzde: number
  /** Sefer pencerenin solunda / sağında devam ediyor: çubuk kırpıldı, uç işaretlenir. */
  solKirpik: boolean
  sagKirpik: boolean
  /** Gecikmeli seferde planlanan kalkışın konumu; gecikme yoksa ya da pencere dışındaysa null. */
  planlananYuzde: number | null
}
export interface Bant {
  solYuzde: number
  genYuzde: number
}

const GUN_DK = 1440
const EN_AZ_DK = 6 * 60
const VARSAYILAN: Pencere = { basDk: 4 * 60, bitDk: GUN_DK }

/** Seçili günün İstanbul gece yarısı (ms). */
export const gunBaslangici = (tarih: Tarih): number => Date.parse(`${tarih}T00:00:00+03:00`)
/** Zamanın seçili günün 00:00'ına göre dakikası (ertesi gün 1440+, önceki gün negatif). */
export const gunDakikasi = (zaman: Zaman, tarih: Tarih): number =>
  (Date.parse(zaman) - gunBaslangici(tarih)) / 60_000

/**
 * Pencere günün verisinden: en erken kalkış (aşağı) – en geç varış (yukarı) tam saate yuvarlanır, gün sonunu (24:00) aşmaz,
 * en az 6, en çok 24 saattir. Gün sonunu geçen sefer sağda kırpılır (çubuk ucu işaretlenir). Boş listede 04:00–24:00.
 */
export function pencere(satirlar: SeferZamani[], tarih: Tarih): Pencere {
  if (satirlar.length === 0) return { ...VARSAYILAN }
  const kalkis = satirlar.map((s) => gunDakikasi(s.guncelKalkis, tarih))
  const varis = satirlar.map((s) => gunDakikasi(s.guncelVaris, tarih))
  const saatAlt = (dk: number) => Math.floor(dk / 60) * 60
  const saatUst = (dk: number) => Math.ceil(dk / 60) * 60
  const tavan = Math.max(GUN_DK, saatUst(Math.max(...kalkis) + 1)) // her çubuğun başı görünür kalsın
  let bas = saatAlt(Math.min(...kalkis))
  let bit = Math.min(saatUst(Math.max(...varis)), tavan)
  if (bit - bas < EN_AZ_DK) {
    bit = bas + EN_AZ_DK
    if (bit > tavan) {
      bit = tavan
      bas = Math.min(bas, bit - EN_AZ_DK)
    }
  }
  bit = Math.min(bit, bas + GUN_DK) // en çok 24 saat
  return { basDk: bas, bitDk: bit }
}

const yuzde = (dk: number, p: Pencere) => ((dk - p.basDk) / (p.bitDk - p.basDk)) * 100
const kirp = (y: number) => Math.min(100, Math.max(0, y))
const EN_AZ_GEN = 0.8 // % — saniyelik seferler görünmez olmasın

export function cubuk(s: SeferZamani, tarih: Tarih, p: Pencere): Cubuk {
  const bas = gunDakikasi(s.guncelKalkis, tarih)
  const bit = gunDakikasi(s.guncelVaris, tarih)
  const sol = kirp(yuzde(bas, p))
  const sag = kirp(yuzde(bit, p))
  const gen = Math.min(100 - sol, Math.max(EN_AZ_GEN, sag - sol))
  const planlanan = gunDakikasi(s.planlananKalkis, tarih)
  const planYuzde = yuzde(planlanan, p)
  return {
    solYuzde: sol,
    genYuzde: gen,
    solKirpik: bas < p.basDk,
    sagKirpik: bit > p.bitDk,
    planlananYuzde: s.kalkisGecikmeDk > 0 && planYuzde >= 0 && planYuzde < 100 ? planYuzde : null,
  }
}

/**
 * Gece bantları, `gece.baslangic`/`gece.bitis` ayarından (sabit saat yok): gece yarısını aşan pencere (20:00–06:00) günün
 * başında ve sonunda iki bant, aynı gün içindeki pencere (01:00–05:00) tek bant verir. Pencereyle kesişmeyenler çıkmaz.
 */
export function geceBantlari(aralik: { baslangicDk: number; bitisDk: number }, p: Pencere): Bant[] {
  const { baslangicDk: b, bitisDk: e } = aralik
  if (b === e) return []
  const parcalar: [number, number][] = []
  for (let gun = Math.floor(p.basDk / GUN_DK) - 1; gun * GUN_DK < p.bitDk; gun++) {
    const t = gun * GUN_DK
    if (b > e) parcalar.push([t, t + e], [t + b, t + GUN_DK])
    else parcalar.push([t + b, t + e])
  }
  const bantlar: Bant[] = []
  for (const [bas, bit] of parcalar) {
    const a = Math.max(bas, p.basDk)
    const z = Math.min(bit, p.bitDk)
    if (z > a)
      bantlar.push({ solYuzde: yuzde(a, p), genYuzde: ((z - a) / (p.bitDk - p.basDk)) * 100 })
  }
  return bantlar
}

/** "Şimdi" çizgisinin konumu; pencerenin dışındaysa (seçili gün bugün değilse) null. */
export function simdiYuzde(simdiMs: number, tarih: Tarih, p: Pencere): number | null {
  const dk = (simdiMs - gunBaslangici(tarih)) / 60_000
  return dk < p.basDk || dk > p.bitDk ? null : yuzde(dk, p)
}

/** Üst eksen: iki saatte bir "HH" etiketi ve konumu. */
export function saatEtiketleri(p: Pencere): { yuzde: number; etiket: string }[] {
  const sonuc: { yuzde: number; etiket: string }[] = []
  for (let dk = Math.ceil(p.basDk / 120) * 120; dk <= p.bitDk; dk += 120)
    sonuc.push({
      yuzde: yuzde(dk, p),
      etiket: String((((dk / 60) % 24) + 24) % 24).padStart(2, '0'),
    })
  return sonuc
}

/**
 * Izgara çizgileri eksen etiketleriyle aynı yerde (D1/B-02): çizgi öğesi ilk çizgiden sağ kenara uzanır, `background-size` yüzdesi
 * pencerenin değil ÖĞENİN genişliğinedir; bu yüzden adım ilk çizgiden sağa kalan süreye göre hesaplanır.
 */
export function izgara(p: Pencere): { solYuzde: number; adimYuzde: number } {
  const ilk = Math.ceil(p.basDk / 120) * 120
  return { solYuzde: yuzde(ilk, p), adimYuzde: (120 / (p.bitDk - ilk)) * 100 }
}
