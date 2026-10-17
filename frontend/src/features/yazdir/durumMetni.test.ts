import { describe, expect, it } from 'vitest'
import { makinist } from '@/features/atama/testVerisi'
import { durumMetni } from './durumMetni'

const asil = makinist()
const yrd = makinist({ atamaId: 2, rol: 'YARDIMCI' })
const kirmizi = makinist({ kirmizi: true, kirmiziNedenler: ['Dinlenme 9 s < 11 s'] })
const taban = {
  durum: 'PLANLI' as const,
  makinistler: [asil],
  kalkisGecikmeDk: 0,
  guncelKalkis: '2026-10-12T05:30:00Z', // İstanbul 08:30
}

describe('durumMetni', () => {
  it('atanmış ve zamanında: boş metin', () => {
    expect(durumMetni(taban)).toBe('')
  })
  it('ASIL yoksa BOŞ (yalnız yardımcı atanmışsa da)', () => {
    expect(durumMetni({ ...taban, makinistler: [] })).toBe('BOŞ')
    expect(durumMetni({ ...taban, makinistler: [yrd] })).toBe('BOŞ')
  })
  it('iptal: İPTAL (BOŞ ve gecikme eklenmez)', () => {
    expect(durumMetni({ ...taban, durum: 'IPTAL', makinistler: [], kalkisGecikmeDk: 30 })).toBe(
      'İPTAL',
    )
  })
  it('gecikme: yeni kalkış İstanbul saatiyle', () => {
    expect(durumMetni({ ...taban, kalkisGecikmeDk: 90 })).toBe('+90 dk (yeni kalkış 08:30)')
  })
  it('birleşik: BOŞ · gecikme', () => {
    expect(durumMetni({ ...taban, makinistler: [], kalkisGecikmeDk: 90 })).toBe(
      'BOŞ · +90 dk (yeni kalkış 08:30)',
    )
  })
  it('kırmızı görevli: KONTROL', () => {
    expect(durumMetni({ ...taban, makinistler: [kirmizi] })).toBe('KONTROL')
    // yardımcı kırmızıysa da sefer kontrol ister
    expect(durumMetni({ ...taban, makinistler: [asil, { ...yrd, kirmizi: true }] })).toBe('KONTROL')
  })
  it('kırmızı + gecikme: KONTROL · gecikme', () => {
    expect(durumMetni({ ...taban, makinistler: [kirmizi], kalkisGecikmeDk: 90 })).toBe(
      'KONTROL · +90 dk (yeni kalkış 08:30)',
    )
  })
  it('iptal öncelikli: kırmızı görevli olsa da yalnız İPTAL', () => {
    expect(durumMetni({ ...taban, durum: 'IPTAL', makinistler: [kirmizi] })).toBe('İPTAL')
  })
})
