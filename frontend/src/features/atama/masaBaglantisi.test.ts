import { describe, expect, it } from 'vitest'
import { masaBaglantisi, suzgecOku } from './masaBaglantisi'

describe('masaBaglantisi (derin bağlantı sözleşmesi, MK-156)', () => {
  it('tarih ve süzgeç sorguya yazılır; süzgeç yoksa anahtar hiç yok', () => {
    expect(masaBaglantisi('2026-10-12', 'kirmizi')).toEqual({
      name: 'atama',
      query: { tarih: '2026-10-12', suzgec: 'kirmizi' },
    })
    expect(masaBaglantisi('2026-10-12')).toEqual({ name: 'atama', query: { tarih: '2026-10-12' } })
  })

  it('suzgecOku: bilinen değerler döner, geçersiz/dizi/boş yok sayılır', () => {
    expect(suzgecOku('atanmamis')).toBe('atanmamis')
    expect(suzgecOku('kirmizi')).toBe('kirmizi')
    expect(suzgecOku('konduktor')).toBe('konduktor')
    expect(suzgecOku('gormeyen')).toBe('gormeyen')
    expect(suzgecOku('bitmeyen')).toBe('bitmeyen')
    expect(suzgecOku('hepsi')).toBeNull()
    expect(suzgecOku(['kirmizi'])).toBeNull()
    expect(suzgecOku(undefined)).toBeNull()
    expect(suzgecOku('')).toBeNull()
  })
})
