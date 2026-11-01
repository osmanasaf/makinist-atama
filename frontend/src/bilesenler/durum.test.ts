import { describe, expect, it } from 'vitest'
import { SIMGELER } from './simgeler'
import { durumOzellikleri, ROL_METINLERI } from './durum'

describe('durumOzellikleri (tek kaynak, MK-138)', () => {
  it.each([
    ['ENGEL', 'engel'],
    ['KONTROL', 'engel'],
    ['IPTAL', 'engel'],
    ['UYARI', 'uyari'],
    ['BOS', 'uyari'],
    ['YAKLASIYOR', 'uyari'],
    ['GORDU', 'basari'],
    ['GECERLI', 'basari'],
    ['UYGULANDI', 'basari'],
    ['GORMEDI', 'notr'],
    ['KONDUKTOR_EKSIK', 'bilgi'],
    ['BILGI', 'bilgi'],
    ['GECE', 'gece'],
    ['DEGISIR', 'bilgi'],
    ['AYNI', 'notr'],
    ['GECERSIZ', 'engel'],
    ['BILINMEYEN', 'uyari'],
    ['PROFILDE_YOK', 'notr'],
  ])('%s -> ton %s, geçerli simge ve metin', (kod, ton) => {
    const o = durumOzellikleri(kod)
    expect(o.ton).toBe(ton)
    expect(o.simge in SIMGELER).toBe(true)
    expect(o.metin.length).toBeGreaterThan(0)
  })

  it('BOŞ (uyarı) ile Kondüktör eksik (bilgi) ayrı tondur', () => {
    expect(durumOzellikleri('BOS').ton).not.toBe(durumOzellikleri('KONDUKTOR_EKSIK').ton)
  })

  it('bilinmeyen kod nötr döner ve kodu metin yapar (metinsiz rozet olmaz)', () => {
    expect(durumOzellikleri('XYZ')).toEqual({ ton: 'notr', simge: 'bilgi', metin: 'XYZ' })
  })

  it('rol metinleri dört rol için tanımlı', () => {
    expect(Object.keys(ROL_METINLERI).sort()).toEqual(['ASIL', 'KONDUKTOR', 'YARDIMCI', 'YOLCU'])
  })
})
