import { describe, expect, it } from 'vitest'
import {
  AZAMI_NOKTA,
  VARSAYILAN_CERCEVE,
  eksenTikleri,
  enYakinIndeks,
  guzelAdim,
  hedefCizgileri,
  noktaKonumu,
  olcekKur,
  ornekle,
  ozetMetni,
  yolDizesi,
  type EgriHedef,
  type EgriNokta,
} from './egriGeometri'

const n = (ek: number, d: number, bos = 0): EgriNokta => ({ ek, bos, dolulukYuzde: d })
const C = VARSAYILAN_CERCEVE
const hedefler: EgriHedef[] = [
  { yuzde: 98, ulasildi: true, ek: 7 },
  { yuzde: 99, ulasildi: true, ek: 12 },
  { yuzde: 100, ulasildi: false, ek: null },
]
const veri = [n(0, 95.64, 122), n(7, 98.43, 44), n(12, 99, 28), n(64, 99.89, 3)]

describe('egriGeometri', () => {
  it('nokta -> piksel: ek=0 sol kenar, en büyük ek sağ kenar; %100 üst, alt sınır alt kenar', () => {
    const o = olcekKur(veri, [98, 99, 100])
    expect(o.x(0)).toBe(C.sol)
    expect(o.x(64)).toBe(C.genislik - C.sag)
    expect(o.y(100)).toBe(C.ust)
    expect(o.y(o.yMin)).toBe(C.yukseklik - C.alt)
    expect(o.yMin).toBeLessThanOrEqual(95.64)
    expect(o.y(99)).toBeGreaterThan(o.y(100))
    expect(noktaKonumu(veri[0]!, o)).toEqual({ x: C.sol, y: Math.round(o.y(95.64) * 100) / 100 })
  })

  it('ek=0..N: ara noktalar x ekseninde doğrusal yerleşir', () => {
    const o = olcekKur([n(0, 90), n(10, 95), n(20, 99)])
    expect(o.x(10)).toBeCloseTo((o.x(0) + o.x(20)) / 2, 6)
  })

  it('tek nokta: x ortalanır, yol yalnız "M", ölçek sonlu', () => {
    const o = olcekKur([n(5, 97)], [98, 99, 100])
    const orta = (C.sol + C.genislik - C.sag) / 2
    expect(o.x(5)).toBeCloseTo(orta, 6)
    expect(yolDizesi([n(5, 97)], o)).toMatch(/^M[\d.]+ [\d.]+$/)
    expect(Number.isFinite(o.y(97))).toBe(true)
  })

  it('boş eğri: boş yol, sonlu ölçek, tek tik', () => {
    const o = olcekKur([])
    expect(yolDizesi([], o)).toBe('')
    expect(Number.isFinite(o.x(0))).toBe(true)
    expect(Number.isFinite(o.y(95))).toBe(true)
    expect(ozetMetni([], [])).toContain('veri yok')
  })

  it('yol dizesi: M ile başlar, nokta başına bir L/M', () => {
    const o = olcekKur(veri)
    const yol = yolDizesi(veri, o)
    expect(yol.startsWith('M')).toBe(true)
    expect((yol.match(/L/g) ?? []).length).toBe(veri.length - 1)
  })

  it('tüm doluluk %100 iken de alt sınır üst sınırdan küçük (bölme sıfır olmaz)', () => {
    const o = olcekKur([n(0, 100), n(3, 100)], [100])
    expect(o.yMin).toBeLessThan(o.yMax)
    expect(Number.isFinite(o.y(100))).toBe(true)
  })

  it('eksen tikleri: güzel adım, sınırlar içinde; tam sayı ekseninde ondalık yok', () => {
    expect(eksenTikleri(94, 100, 4)).toEqual([94, 96, 98, 100])
    expect(eksenTikleri(0, 64, 6, true)).toEqual([0, 20, 40, 60])
    expect(eksenTikleri(4.5, 5.5, 5, true)).toEqual([5])
    expect(eksenTikleri(3, 3)).toEqual([3])
    expect(guzelAdim(0)).toBe(1)
    expect(guzelAdim(1.09)).toBe(2)
  })

  it('hedef çizgileri: 100 en üstte, her biri etiketli; yakın etiketler ayrılır', () => {
    const o = olcekKur(veri, [98, 99, 100])
    const h = hedefCizgileri(hedefler, o)
    expect(h.map((x) => x.yuzde)).toEqual([100, 99, 98])
    expect(h.map((x) => x.etiket)).toEqual(['%100 hedef', '%99 hedef', '%98 hedef'])
    expect(h[0]!.ulasildi).toBe(false)
    for (let i = 1; i < h.length; i++)
      expect(h[i]!.etiketY - h[i - 1]!.etiketY).toBeGreaterThanOrEqual(14)
    // geniş alanda (ör. %60 doluluk) çizgiler sıkışır ama etiketler yine ayrışır
    const dar = olcekKur([n(0, 60), n(9, 99)], [98, 99, 100])
    const hd = hedefCizgileri(hedefler, dar)
    for (let i = 1; i < hd.length; i++)
      expect(hd[i]!.etiketY - hd[i - 1]!.etiketY).toBeGreaterThanOrEqual(14)
  })

  it('en çok 60 nokta: örnekleme ilk ve son noktayı korur, sırayı bozmaz', () => {
    const cok = Array.from({ length: 200 }, (_, i) => n(i, 90 + i / 25))
    const o = ornekle(cok)
    expect(o.length).toBeLessThanOrEqual(AZAMI_NOKTA)
    expect(o[0]).toBe(cok[0])
    expect(o[o.length - 1]).toBe(cok[199])
    expect(o.map((x) => x.ek)).toEqual(o.map((x) => x.ek).sort((a, b) => a - b))
    expect(ornekle(veri)).toEqual(veri)
  })

  it('en yakın indeks: x konumuna en yakın nokta; boşsa -1', () => {
    const o = olcekKur(veri)
    expect(enYakinIndeks(veri, o, o.x(0) - 30)).toBe(0)
    expect(enYakinIndeks(veri, o, o.x(12) + 1)).toBe(2)
    expect(enYakinIndeks(veri, o, 9999)).toBe(3)
    expect(enYakinIndeks([], o, 10)).toBe(-1)
  })

  it('özet metni: uçlar ve hedefler (+n / ulaşılamadı), tabloya yönlendirir', () => {
    const t = ozetMetni(veri, hedefler)
    expect(t).toContain('4 nokta')
    expect(t).toContain('%95,64')
    expect(t).toContain('%99,89')
    expect(t).toContain('%98 için +7')
    expect(t).toContain('%100 için ulaşılamadı')
    expect(t).toContain('tablo')
  })
})
