import { describe, expect, it, vi } from 'vitest'
import { geceMi } from './gece'

// İstanbul = UTC+3 (2026-10-12)
describe('geceMi', () => {
  it('gündüz seferi gece değil', () =>
    expect(geceMi('2026-10-12T05:30:00Z', '2026-10-12T07:00:00Z')).toBe(false))
  it('kalkış 05:00 (İstanbul) gece', () =>
    expect(geceMi('2026-10-12T02:00:00Z', '2026-10-12T04:00:00Z')).toBe(true))
  it('varış 21:30 gece', () =>
    expect(geceMi('2026-10-12T15:00:00Z', '2026-10-12T18:30:00Z')).toBe(true))
  it('ertesi güne sarkan sefer gece', () =>
    expect(geceMi('2026-10-12T18:30:00Z', '2026-10-12T21:30:00Z')).toBe(true))
})

describe('geceMi — ayardaki pencere (gece.baslangic / gece.bitis)', () => {
  const aralik = (b: string, e: string) => {
    const dk = (s: string) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3))
    return { baslangicDk: dk(b), bitisDk: dk(e) }
  }
  it('pencere 22:00–05:00 ise 21:30 varış artık gece değil', () => {
    const a = aralik('22:00', '05:00')
    expect(geceMi('2026-10-12T15:00:00Z', '2026-10-12T18:30:00Z', a)).toBe(false)
    expect(geceMi('2026-10-12T15:00:00Z', '2026-10-12T19:30:00Z', a)).toBe(true) // 22:30
  })
  it('pencere 18:00–06:00 ise 18:30 kalkış gece', () =>
    expect(geceMi('2026-10-12T15:30:00Z', '2026-10-12T17:00:00Z', aralik('18:00', '06:00'))).toBe(
      true,
    ))
  it('dakika duyarlı: 20:30 başlangıçta 20:15 gece değil', () =>
    expect(geceMi('2026-10-12T17:15:00Z', '2026-10-12T17:25:00Z', aralik('20:30', '06:00'))).toBe(
      false,
    ))
  it('gece yarısını aşmayan pencere (01:00–05:00): içi gece, dışı değil', () => {
    const a = aralik('01:00', '05:00')
    expect(geceMi('2026-10-12T00:30:00Z', '2026-10-12T00:50:00Z', a)).toBe(true) // 03:30–03:50
    expect(geceMi('2026-10-12T05:30:00Z', '2026-10-12T06:30:00Z', a)).toBe(false) // 08:30–09:30
  })
})

describe('geceAyariYukle', () => {
  it('parametre okunamazsa varsayılan kalır, okunursa aralık güncellenir', async () => {
    const { geceAyariYukle, geceAraligi } = await import('./gece')
    const { parametreApi } = await import('@/features/parametre/parametreApi')
    vi.spyOn(parametreApi, 'parametreler').mockRejectedValueOnce(new Error('ag'))
    await geceAyariYukle()
    expect(geceAraligi.value).toEqual({ baslangicDk: 1200, bitisDk: 360 })
    vi.spyOn(parametreApi, 'parametreler').mockResolvedValueOnce([
      { anahtar: 'gece.baslangic', deger: '22:30' },
      { anahtar: 'gece.bitis', deger: '05:00' },
    ] as never)
    await geceAyariYukle()
    expect(geceAraligi.value).toEqual({ baslangicDk: 1350, bitisDk: 300 })
    geceAraligi.value = { baslangicDk: 1200, bitisDk: 360 }
  })
})
