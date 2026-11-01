import { describe, expect, it } from 'vitest'
import {
  gunBasligi,
  gunMetni,
  haftaSonuMu,
  hucreGorunumu,
  IZIN_KODLARI,
  tarihKodu,
} from './cizelgeHucre'
import type { CizelgeGorev } from './cizelgeApi'

const g = (ek: Partial<CizelgeGorev> = {}): CizelgeGorev => ({
  seferId: 7,
  trenNo: 'T1',
  kalkis: '06:00',
  varis: '14:00',
  rol: 'ASIL',
  ...ek,
})
const gorunum = (h: NonNullable<Parameters<typeof hucreGorunumu>[0]>, tatil?: string) =>
  hucreGorunumu(h, 'Ali Veli', gunMetni('2026-10', h.gun), tatil)!

describe('cizelgeHucre: hücre -> kısa kod, işaretler ve erişilebilir ad (F5, AK-5.7)', () => {
  it('hücre yoksa görünüm yok', () => {
    expect(hucreGorunumu(undefined, 'Ali Veli', '1 Ekim 2026')).toBeNull()
  })

  it('tek görev: kısa kod = tren no, saat = kalkış; renk dışı işaret yok', () => {
    const v = gorunum({ gun: 1, gorevler: [g()] })
    expect(v).toMatchObject({
      tur: 'gorev',
      kod: 'T1',
      saat: '06:00',
      ek: 0,
      gece: false,
      kirmizi: false,
      seferId: 7,
    })
    expect(v.ad).toBe('Ali Veli, 1 Ekim 2026: T1 06:00–14:00, Asıl')
  })

  it('çoklu görev: ilk görev + "+N", ad hepsini sayar; sefer bağlantısı ilk görevin seferi', () => {
    const v = gorunum({
      gun: 12,
      gorevler: [g({ seferId: 3, trenNo: 'T2' }), g({ seferId: 4, trenNo: 'T9', kalkis: '15:00' })],
    })
    expect(v.kod).toBe('T2')
    expect(v.ek).toBe(1)
    expect(v.seferId).toBe(3)
    expect(v.ad).toContain('T2 06:00–14:00, Asıl; T9 15:00–14:00, Asıl')
  })

  it('izin: "İ" yıllık, "R" rapor, "D" diğer; ad izin türünü söyler', () => {
    expect(IZIN_KODLARI.YILLIK.kod).toBe('İ')
    expect(IZIN_KODLARI.RAPOR.kod).toBe('R')
    expect(IZIN_KODLARI.DIGER.kod).toBe('D')
    const v = gorunum({ gun: 5, izin: 'RAPOR' })
    expect(v).toMatchObject({ tur: 'izin', kod: 'R', saat: '', ek: 0, seferId: null })
    expect(v.ad).toBe('Ali Veli, 5 Ekim 2026: rapor (onaylı izin)')
  })

  it('gece görevi: işaret + adda "gece görevi"', () => {
    const v = gorunum({ gun: 12, gorevler: [g({ gece: true, kalkis: '22:00', varis: '06:00' })] })
    expect(v.gece).toBe(true)
    expect(v.ad).toContain('gece görevi')
  })

  it('yolculuk: kodun önüne "Y", tür yolculuk; sürüş değildir', () => {
    const v = gorunum({ gun: 3, gorevler: [g({ yolculuk: true, rol: 'YOLCU', trenNo: 'T4' })] })
    expect(v).toMatchObject({ tur: 'yolculuk', kod: 'Y T4' })
    expect(v.ad).toContain('yolculuk')
  })

  it('serbest intikal: sefer bağlantısı yok, kısa kod "İnt"', () => {
    const v = gorunum({
      gun: 3,
      gorevler: [g({ seferId: undefined, trenNo: 'İntikal', yolculuk: true, rol: 'YOLCU' })],
    })
    expect(v.seferId).toBeNull()
    expect(v.kod).toBe('Y İnt')
  })

  it('kontrol bekleyen (kırmızı) görev: işaret + adda "kontrol bekliyor"', () => {
    const v = gorunum({ gun: 20, gorevler: [g({ kirmizi: true })] })
    expect(v.kirmizi).toBe(true)
    expect(v.ad).toContain('kontrol bekliyor')
  })

  it('resmi tatil günü: adda tatil adı (renk tek başına değil)', () => {
    const v = gorunum({ gun: 29, gorevler: [g()] }, 'Cumhuriyet Bayramı')
    expect(v.ad).toContain('resmi tatil: Cumhuriyet Bayramı')
  })

  it('hem izin hem görev (nadir): görev hücresi + izin kodu işareti; ad ikisini söyler', () => {
    const v = gorunum({ gun: 6, izin: 'YILLIK', gorevler: [g()] })
    expect(v.tur).toBe('gorev')
    expect(v.izinKodu).toBe('İ')
    expect(v.ad).toContain('yıllık (onaylı izin)')
  })
})

describe('cizelgeHucre: gün başlıkları', () => {
  it('tarih kodu, uzun gün metni, hafta sonu ve kısa gün adı', () => {
    expect(tarihKodu('2026-10', 5)).toBe('2026-10-05')
    expect(gunMetni('2026-10', 12)).toBe('12 Ekim 2026')
    expect(haftaSonuMu('2026-10', 3)).toBe(true) // Cumartesi
    expect(haftaSonuMu('2026-10', 4)).toBe(true) // Pazar
    expect(haftaSonuMu('2026-10', 5)).toBe(false) // Pazartesi
    expect(gunBasligi('2026-10', 5)).toEqual({ no: 5, ad: 'Pzt', haftaSonu: false })
    expect(gunBasligi('2026-10', 3).ad).toBe('Cmt')
    expect(gunBasligi('2026-10', 4).ad).toBe('Paz')
  })
})
