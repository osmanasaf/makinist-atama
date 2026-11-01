import { describe, expect, it } from 'vitest'
import type { GunSatiri } from '@/features/atama/atamaApi'
import { atanmamisMi, yapilacaklariOlustur, type YapilacakGirdi } from './yapilacaklar'

const SIMDI = Date.parse('2026-10-23T03:40:00Z') // İstanbul 06:40
const dk = (n: number) => new Date(SIMDI + n * 60_000).toISOString()
const asil = {
  atamaId: 1,
  rol: 'ASIL' as const,
  makinistId: 1,
  sicil: 'D001',
  ad: 'Ali',
  kirmizi: false,
  kirmiziNedenler: [],
  uyarilar: [],
}
function satir(id: number, ek: Partial<GunSatiri> = {}): GunSatiri {
  return {
    seferId: id,
    trenNo: `T${id}`,
    kalkisGar: 'Ankara',
    varisGar: 'Sivas',
    planlananKalkis: dk(30),
    planlananVaris: dk(300),
    guncelKalkis: dk(30),
    guncelVaris: dk(300),
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    aracTipi: 'YHT',
    hat: 'AS',
    km: 100,
    durum: 'PLANLI',
    kirmizi: false,
    makinistler: [],
    bosNeden: null,
    ...ek,
  }
}
const girdi = (ek: Partial<YapilacakGirdi> = {}): YapilacakGirdi => ({
  simdi: SIMDI,
  tarih: '2026-10-23',
  satirlar: [],
  kirmizi: { sefer: 0, atama: 0, intikal: 0 },
  gorulmeyen: 0,
  takas: 0,
  izin: 0,
  belge: { dolmus: 0, yaklasan: 0 },
  ...ek,
})
const kimlikler = (g: YapilacakGirdi) => yapilacaklariOlustur(g).map((i) => i.kimlik)

describe('atanmamisMi', () => {
  it('yalnız PLANLI, asılsız ve henüz bitmemiş sefer', () => {
    expect(atanmamisMi(satir(1), SIMDI)).toBe(true)
    expect(atanmamisMi(satir(1, { makinistler: [asil] }), SIMDI)).toBe(false)
    expect(atanmamisMi(satir(1, { durum: 'IPTAL' }), SIMDI)).toBe(false)
    expect(atanmamisMi(satir(1, { guncelVaris: dk(-5) }), SIMDI)).toBe(false)
  })
})

describe('yapilacaklariOlustur (AK-6.1)', () => {
  it('hiçbir şey yoksa boş liste (sıfır satırlar çıkmaz)', () => {
    expect(yapilacaklariOlustur(girdi())).toEqual([])
  })

  it('sabit öncelik: kırmızı → atanmamış → görmeyen → takas → izin → belge', () => {
    const g = girdi({
      satirlar: [satir(1)],
      kirmizi: { sefer: 2, atama: 3, intikal: 0 },
      gorulmeyen: 4,
      takas: 1,
      izin: 2,
      belge: { dolmus: 1, yaklasan: 0 },
    })
    expect(kimlikler(g)).toEqual(['kirmizi', 'atanmamis', 'gorulmeyen', 'takas', 'izin', 'belge'])
  })

  it('kaynağı düşen (null) satır çıkmaz, diğerleri kalır', () => {
    expect(
      kimlikler(girdi({ satirlar: null, kirmizi: null, takas: 2, belge: null, izin: null })),
    ).toEqual(['takas'])
  })

  it('kırmızı: ilkKirmiziGun günü + kirmizi süzgeci; yoksa bugüne düşer', () => {
    const [i] = yapilacaklariOlustur(
      girdi({ kirmizi: { sefer: 2, atama: 3, intikal: 0, ilkKirmiziGun: '2026-10-25' } }),
    )
    expect(i!.hedef).toEqual({ name: 'atama', query: { tarih: '2026-10-25', suzgec: 'kirmizi' } })
    expect(i!.baslik).toContain('2 sefer')
    expect(i!.ayrinti).toContain('3 atama')
    expect(i!.ayrinti).toContain('25.10.2026')
    const [d] = yapilacaklariOlustur(
      girdi({ kirmizi: { sefer: 1, atama: 1, intikal: 0, ilkKirmiziGun: null } }),
    )
    expect(d!.hedef).toEqual({ name: 'atama', query: { tarih: '2026-10-23', suzgec: 'kirmizi' } })
  })

  it('yalnız kırmızı intikal varsa satır çıkar, süzgeçsiz bugünün masasına gider', () => {
    const [i] = yapilacaklariOlustur(girdi({ kirmizi: { sefer: 0, atama: 0, intikal: 2 } }))
    expect(i!.kimlik).toBe('kirmizi')
    expect(i!.baslik).toContain('2 kırmızı intikal')
    expect(i!.hedef).toEqual({ name: 'atama', query: { tarih: '2026-10-23' } })
  })

  it('atanmamış: bitmeyenler sayılır, en yakın kalkış metni ve bitmeyen (atanmamış) süzgeci', () => {
    const [i] = yapilacaklariOlustur(
      girdi({
        satirlar: [
          satir(1, { makinistler: [asil] }),
          satir(2, { guncelKalkis: dk(90) }),
          satir(3, { guncelKalkis: dk(31) }),
          satir(4, { durum: 'IPTAL' }),
          satir(5, { guncelVaris: dk(-1) }),
        ],
      }),
    )
    expect(i!.kimlik).toBe('atanmamis')
    expect(i!.baslik).toContain('2 sefer')
    expect(i!.ayrinti).toContain('31 dk sonra')
    expect(i!.hedef).toEqual({ name: 'atama', query: { tarih: '2026-10-23', suzgec: 'bitmeyen' } })
  })

  it('atanmamış seferin kalkışı geçtiyse "kalkışı geçti, henüz bitmedi" der', () => {
    const [i] = yapilacaklariOlustur(girdi({ satirlar: [satir(2, { guncelKalkis: dk(-10) })] }))
    expect(i!.ayrinti).toContain('1 seferin kalkışı geçti')
    expect(i!.ayrinti).toContain('henüz bitmedi')
  })

  it('OK-41a: sayı "henüz bitmeyen" seferlerdir; alt metin tüm sayı için "kalkış geçti" demez, kalkışı geçeni ayrı sayar', () => {
    const [i] = yapilacaklariOlustur(
      girdi({
        satirlar: [
          satir(2, { guncelKalkis: dk(-10) }),
          satir(3, { guncelKalkis: dk(-5) }),
          satir(4, { guncelKalkis: dk(40) }),
        ],
      }),
    )
    expect(i!.baslik).toContain('3 sefer atanmamış')
    expect(i!.ayrinti).toContain('2 seferin kalkışı geçti')
    expect(i!.ayrinti).toContain('40 dk sonra')
    expect(i!.ayrinti).not.toContain('Kalkış saati geçti')
  })

  it('takas, izin, belge ve görmeyen: doğru sayı ve hedef; her satırda tek eylem', () => {
    const l = yapilacaklariOlustur(
      girdi({ gorulmeyen: 3, takas: 2, izin: 4, belge: { dolmus: 2, yaklasan: 5 } }),
    )
    const k = Object.fromEntries(l.map((i) => [i.kimlik, i]))
    expect(k.gorulmeyen!.baslik).toContain('3')
    // OK-41c: görmeyen kalemi masaya "gormeyen" süzgeciyle gider
    expect(k.gorulmeyen!.hedef).toEqual({
      name: 'atama',
      query: { tarih: '2026-10-23', suzgec: 'gormeyen' },
    })
    expect(k.takas!.baslik).toContain('2 takas')
    expect(k.takas!.hedef).toEqual({ name: 'takas' })
    expect(k.izin!.baslik).toContain('4 izin')
    expect(k.izin!.hedef).toEqual({ name: 'izin' })
    expect(k.belge!.baslik).toContain('2 belge')
    expect(k.belge!.ayrinti).toContain('5')
    expect(k.belge!.hedef).toEqual({ name: 'belgeler' })
    for (const i of l) expect(typeof i.eylem).toBe('string')
  })

  it('yalnız yaklaşan belge de satır üretir', () => {
    expect(kimlikler(girdi({ belge: { dolmus: 0, yaklasan: 1 } }))).toEqual(['belge'])
  })
})
