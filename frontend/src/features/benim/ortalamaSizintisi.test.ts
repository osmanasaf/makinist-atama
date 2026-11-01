import { describe, expect, it } from 'vitest'

// OK-35 kapısı: makinist ekranı hiçbir yerde "ortalama" verisi göstermez ya da istemez (ağ yanıtında da yok: BenimOzetTest.java).
const kaynaklar = import.meta.glob(['./**/*.vue', './**/*.ts', './**/*.css', '!./**/*.test.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

describe('makinist ekranında ortalama yok (OK-35)', () => {
  it('features/benim/** üretim kaynağında "ortalama" geçmez', () => {
    expect(Object.keys(kaynaklar).length).toBeGreaterThan(5)
    const bulunan = Object.entries(kaynaklar)
      .filter(([, metin]) => /ortalama/i.test(metin))
      .map(([yol]) => yol)
    expect(bulunan).toEqual([])
  })

  it('arayüz tipi ortalamaSaat/farkYuzde alanı tanımlamaz', () => {
    const tipler = Object.entries(kaynaklar).find(([y]) => y.endsWith('benimApi.ts'))![1]
    expect(tipler).not.toMatch(/ortalamaSaat|farkYuzde/)
  })
})
