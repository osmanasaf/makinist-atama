import { afterEach, describe, expect, it, vi } from 'vitest'
import { bugun, gunKaydir, kalanMetni, saatBicimle, tarihBicimle, zamanBicimle } from './tarih'

afterEach(() => vi.useRealTimers())

describe('tarih', () => {
  it('tarihBicimle ISO tarihi gg.aa.yyyy yapar, boşta boş döner', () => {
    expect(tarihBicimle('2026-10-30')).toBe('30.10.2026')
    expect(tarihBicimle(null)).toBe('')
    expect(tarihBicimle('bozuk')).toBe('')
  })

  it('zamanBicimle UTC zamanı İstanbul saatine çevirir (gg.aa.yyyy ss:dd)', () => {
    expect(zamanBicimle('2026-10-05T06:12:33.123Z')).toBe('05.10.2026 09:12')
    expect(zamanBicimle(null)).toBe('')
    expect(zamanBicimle('bozuk')).toBe('bozuk')
  })

  it('zamanBicimle gün sınırını geçer: 31 Eki 22:00Z -> 1 Kas 01:00', () => {
    expect(zamanBicimle('2026-10-31T22:00:00Z')).toBe('01.11.2026 01:00')
  })

  it('bugun İstanbul gününü verir (UTC gece yarısından önce de ertesi gün olabilir)', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-08T21:30:00Z')) // İstanbul 09.10 00:30
    expect(bugun()).toBe('2026-10-09')
    vi.setSystemTime(new Date('2026-10-09T20:59:00Z')) // İstanbul 09.10 23:59
    expect(bugun()).toBe('2026-10-09')
  })

  it('saatBicimle UTC zamanı İstanbul saatine (ss:dd) çevirir', () => {
    expect(saatBicimle('2026-10-12T05:30:00Z')).toBe('08:30')
    expect(saatBicimle('2026-10-12T21:05:00Z')).toBe('00:05')
    expect(saatBicimle(null)).toBe('')
    expect(saatBicimle('bozuk')).toBe('bozuk')
  })

  it('gunKaydir ay/yıl sınırını geçer', () => {
    expect(gunKaydir('2026-10-31', 1)).toBe('2026-11-01')
    expect(gunKaydir('2026-01-01', -1)).toBe('2025-12-31')
  })
})

describe('kalanMetni (H1/H8: "31 dk sonra", "3 sa 20 dk sonra")', () => {
  const t0 = Date.parse('2026-10-23T06:00:00Z')
  const sonra = (dk: number) => t0 + dk * 60_000
  it('dakikaya yukarı yuvarlar; saat ve dakika birlikte, tam saatte dakika yok', () => {
    expect(kalanMetni(sonra(31), t0)).toBe('31 dk sonra')
    expect(kalanMetni(t0 + 30_001, t0)).toBe('1 dk sonra')
    expect(kalanMetni(sonra(60), t0)).toBe('1 sa sonra')
    expect(kalanMetni(sonra(200), t0)).toBe('3 sa 20 dk sonra')
  })
  it('geçmiş ya da şimdi → "şimdi"; 48 saatten uzak → gün', () => {
    expect(kalanMetni(t0, t0)).toBe('şimdi')
    expect(kalanMetni(sonra(-5), t0)).toBe('şimdi')
    expect(kalanMetni(sonra(47 * 60), t0)).toBe('47 sa sonra')
    expect(kalanMetni(sonra(50 * 60), t0)).toBe('2 gün sonra')
  })
})
