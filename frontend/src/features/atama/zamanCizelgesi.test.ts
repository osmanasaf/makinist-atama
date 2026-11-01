import { describe, expect, it } from 'vitest'
import {
  cubuk,
  geceBantlari,
  gunDakikasi,
  izgara,
  pencere,
  saatEtiketleri,
  simdiYuzde,
  type SeferZamani,
} from './zamanCizelgesi'

const T = '2026-10-23'
// İstanbul saati → UTC (+03:00): 08:30 = 05:30Z. `g` = gün kayması (1 = ertesi gün).
const z = (sa: number, dk: number, g = 0) =>
  new Date(Date.parse(`${T}T00:00:00+03:00`) + ((g * 24 + sa) * 60 + dk) * 60_000).toISOString()
const sefer = (
  k: [number, number, number?],
  v: [number, number, number?],
  ek = {},
): SeferZamani => ({
  planlananKalkis: z(...k),
  guncelKalkis: z(...k),
  guncelVaris: z(...v),
  kalkisGecikmeDk: 0,
  ...ek,
})

describe('gunDakikasi', () => {
  it("İstanbul gününün 00:00'ına göre dakika; ertesi gün 1440+", () => {
    expect(gunDakikasi(z(8, 30), T)).toBe(510)
    expect(gunDakikasi(z(2, 47, 1), T)).toBe(1440 + 167)
  })
})

describe('pencere', () => {
  it('boş liste: 04:00–24:00', () => {
    expect(pencere([], T)).toEqual({ basDk: 240, bitDk: 1440 })
  })

  it('en erken kalkış aşağı, en geç varış yukarı tam saate yuvarlanır (yarım saat sınırları)', () => {
    const p = pencere(
      [sefer([5, 9], [6, 57]), sefer([13, 40], [15, 32]), sefer([18, 45], [20, 30])],
      T,
    )
    expect(p).toEqual({ basDk: 300, bitDk: 1260 }) // 05:00 – 21:00
  })

  it('tam saat varış aynen kalır (06:00 → 06:00)', () => {
    expect(pencere([sefer([5, 0], [18, 0])], T)).toEqual({ basDk: 300, bitDk: 1080 })
  })

  it('gece yarısını geçen sefer: pencere gün sonunda (24:00) biter, sefer sağda kırpılır', () => {
    const geceSefer = sefer([21, 22], [2, 47, 1])
    const p = pencere([sefer([5, 9], [6, 57]), geceSefer], T)
    expect(p).toEqual({ basDk: 300, bitDk: 1440 })
    const c = cubuk(geceSefer, T, p)
    expect(c.sagKirpik).toBe(true)
    expect(c.solKirpik).toBe(false)
    expect(c.solYuzde + c.genYuzde).toBeCloseTo(100, 6)
  })

  it('en az 6 saat: kısa aralık bitişe doğru genişler', () => {
    expect(pencere([sefer([8, 30], [10, 0])], T)).toEqual({ basDk: 480, bitDk: 840 })
  })

  it("en az 6 saat: gün sonuna yakın seferde pencere geriye doğru uzar, 24:00'ı aşmaz", () => {
    expect(pencere([sefer([21, 0], [23, 0])], T)).toEqual({ basDk: 1080, bitDk: 1440 })
  })

  it('OK-42b: eksen günün verisine uyar (09–17 arası seferler → 8 saatlik pencere, sabit 04–24 değil)', () => {
    const p = pencere([sefer([9, 10], [11, 0]), sefer([14, 20], [16, 40])], T)
    expect(p).toEqual({ basDk: 540, bitDk: 1020 })
  })

  it('OK-42b: en çok 24 saat (00:30 kalkış + ertesi gün 00:40 kalkış olsa da pencere 24 saati aşmaz)', () => {
    const p = pencere([sefer([0, 30], [4, 0]), sefer([0, 40, 1], [2, 0, 1])], T)
    expect(p.bitDk - p.basDk).toBeLessThanOrEqual(1440)
    expect(p.basDk).toBe(0)
  })

  it('gecikmeyle ertesi güne kayan kalkış çubuğun başı pencerede kalacak şekilde pencereyi genişletir', () => {
    const p = pencere([sefer([23, 30], [1, 10, 1]), sefer([0, 20, 1], [2, 0, 1])], T)
    expect(p.bitDk).toBeGreaterThanOrEqual(1440 + 21)
    expect(p.basDk).toBeLessThanOrEqual(1410)
  })
})

describe('cubuk', () => {
  const p = { basDk: 240, bitDk: 1440 } // 04:00–24:00 (1200 dk)

  it('konum ve genişlik yüzde: 06:00–08:00 → sol %10, genişlik %10', () => {
    const c = cubuk(sefer([6, 0], [8, 0]), T, p)
    expect(c.solYuzde).toBeCloseTo(10, 6)
    expect(c.genYuzde).toBeCloseTo(10, 6)
    expect(c.solKirpik || c.sagKirpik).toBe(false)
    expect(c.planlananYuzde).toBeNull()
  })

  it('çok kısa sefer görünmez olmaz (en az genişlik)', () => {
    expect(cubuk(sefer([6, 0], [6, 1]), T, p).genYuzde).toBeGreaterThanOrEqual(0.8)
  })

  it('pencerenin solundan başlayan sefer sol kırpılır', () => {
    const c = cubuk(sefer([3, 0], [5, 0]), T, p)
    expect(c.solKirpik).toBe(true)
    expect(c.solYuzde).toBe(0)
    expect(c.genYuzde).toBeCloseTo((60 / 1200) * 100, 6)
  })

  it('gecikmede planlanan başlangıç işaret konumu verir; gecikme yoksa null', () => {
    const gecikmeli = sefer([6, 30], [8, 0], { planlananKalkis: z(6, 5), kalkisGecikmeDk: 25 })
    expect(cubuk(gecikmeli, T, p).planlananYuzde).toBeCloseTo(((365 - 240) / 1200) * 100, 6)
    expect(cubuk(sefer([6, 30], [8, 0]), T, p).planlananYuzde).toBeNull()
  })

  it('iptal ya da durumdan bağımsız: geometri yalnız zamandan gelir', () => {
    const a = cubuk(sefer([10, 0], [12, 0]), T, p)
    expect(a.solYuzde).toBeCloseTo(((600 - 240) / 1200) * 100, 6)
  })
})

describe('geceBantlari (ayardan; sabit saat yok)', () => {
  const p = { basDk: 300, bitDk: 1440 } // 05:00–24:00

  it('20:00–06:00: pencere başında kısa, sonunda uzun iki bant', () => {
    const b = geceBantlari({ baslangicDk: 1200, bitisDk: 360 }, p)
    expect(b).toHaveLength(2)
    expect(b[0]!.solYuzde).toBe(0)
    expect(b[0]!.genYuzde).toBeCloseTo((60 / 1140) * 100, 6) // 05:00–06:00
    expect(b[1]!.solYuzde).toBeCloseTo((900 / 1140) * 100, 6)
    expect(b[1]!.genYuzde).toBeCloseTo((240 / 1140) * 100, 6) // 20:00–24:00
  })

  it('ayar 01:00–05:00 (aynı gün): bu pencerede bant yok; ayar 10:00–12:00 tek bant', () => {
    expect(geceBantlari({ baslangicDk: 60, bitisDk: 300 }, p)).toEqual([])
    const b = geceBantlari({ baslangicDk: 600, bitisDk: 720 }, p)
    expect(b).toHaveLength(1)
    expect(b[0]!.solYuzde).toBeCloseTo((300 / 1140) * 100, 6)
    expect(b[0]!.genYuzde).toBeCloseTo((120 / 1140) * 100, 6)
  })

  it('ayar değişince bantlar değişir (aynı pencere, farklı aralık)', () => {
    expect(geceBantlari({ baslangicDk: 1200, bitisDk: 360 }, p)).not.toEqual(
      geceBantlari({ baslangicDk: 1320, bitisDk: 300 }, p),
    )
  })

  it('başlangıç = bitiş: gece yok', () => {
    expect(geceBantlari({ baslangicDk: 0, bitisDk: 0 }, p)).toEqual([])
  })

  it('ertesi güne uzayan pencerede ikinci günün gece bandı da çıkar', () => {
    const b = geceBantlari({ baslangicDk: 1200, bitisDk: 360 }, { basDk: 1200, bitDk: 1560 })
    expect(b.length).toBeGreaterThanOrEqual(2) // 20:00–24:00 ve ertesi gün 00:00–02:00
  })
})

describe('simdiYuzde', () => {
  const p = { basDk: 240, bitDk: 1440 }
  const an = (sa: number, dk: number) => Date.parse(`${T}T00:00:00+03:00`) + (sa * 60 + dk) * 60_000

  it('pencere içindeki an yüzde döner', () => {
    expect(simdiYuzde(an(10, 0), T, p)).toBeCloseTo(30, 6)
  })
  it('başka günde ya da pencere dışında null', () => {
    expect(simdiYuzde(an(2, 0), T, p)).toBeNull()
    expect(simdiYuzde(an(10, 0) + 86_400_000, T, p)).toBeNull()
  })
})

describe('saatEtiketleri', () => {
  it('iki saatte bir, 24 mod; sınırlar dahil', () => {
    const e = saatEtiketleri({ basDk: 300, bitDk: 1500 })
    expect(e.map((x) => x.etiket)).toEqual([
      '06',
      '08',
      '10',
      '12',
      '14',
      '16',
      '18',
      '20',
      '22',
      '00',
    ])
    expect(e[0]!.yuzde).toBeCloseTo((60 / 1200) * 100, 6)
  })
})

describe('izgara (D1/B-02)', () => {
  // Izgara öğesi ilk çizgiden sağa uzanır (left: solYuzde; right: 0); background-size yüzdesi ÖĞENİN genişliğinedir.
  const konum = (g: ReturnType<typeof izgara>, k: number) =>
    g.solYuzde + (k * g.adimYuzde * (100 - g.solYuzde)) / 100

  it.each([
    ['çift saat tabanı (04–24)', { basDk: 240, bitDk: 1440 }],
    ['tek saat tabanı (05–23)', { basDk: 300, bitDk: 1380 }],
    ['tek saat tabanı, 24 saat (07–07)', { basDk: 420, bitDk: 1860 }],
    ['yarım adım kalan (05–22)', { basDk: 300, bitDk: 1320 }],
  ])('%s: her çizgi eksen etiketinin yüzdesinde', (_ad, p) => {
    const g = izgara(p)
    const etiketler = saatEtiketleri(p)
    expect(etiketler.length).toBeGreaterThan(2)
    etiketler.forEach((e, k) => expect(konum(g, k)).toBeCloseTo(e.yuzde, 6))
    expect(g.solYuzde).toBeCloseTo(etiketler[0]!.yuzde, 6)
  })
})
