import { describe, expect, it } from 'vitest'
import { eslesir, normalle } from './aramaMetni'

describe('aramaMetni (MK-158)', () => {
  it('normalle: Türkçe büyük/küçük (İ/ı) ve aksan duyarsız', () => {
    expect(normalle('Geçiş SÜRESİ')).toBe('gecis suresi')
    expect(normalle('IĞDIR İÇ ŞÖFÖR')).toBe('igdir ic sofor')
    expect(normalle('ığüşöçİ')).toBe('igusoci')
  })

  it('eslesir: boşlukla ayrılmış TÜM sözcükler (sırası önemsiz) bulunmalı', () => {
    expect(eslesir('Asgari dinlenme süresi', 'dinlenme')).toBe(true)
    expect(eslesir('Asgari dinlenme süresi', 'SURESI asgari')).toBe(true)
    expect(eslesir('Asgari dinlenme süresi', 'dinlenme günlük')).toBe(false)
    expect(eslesir('Kural K-14 Gece', 'k-14')).toBe(true)
  })

  it('eslesir: boş / yalnız boşluk sorgu her şeyi eşler', () => {
    expect(eslesir('x', '')).toBe(true)
    expect(eslesir('x', '   ')).toBe(true)
  })
})
