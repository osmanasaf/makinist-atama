import { describe, expect, it } from 'vitest'
import { SIMGELER } from '@/bilesenler/simgeler'
import {
  degerMetni,
  durumGorunumu,
  gorunenSatirlar,
  gruplaSatirlar,
  secilebilir,
  secimGecersizMi,
  varsayilanGorunur,
  varsayilanSecim,
} from './profilFark'
import type { ProfilSatiri, SatirDurumu, SatirTuru } from './profilApi'

const s = (kimlik: string, durum: SatirDurumu, tur: SatirTuru = 'PARAMETRE'): ProfilSatiri => ({
  kimlik,
  tur,
  anahtar: kimlik,
  ad: kimlik,
  mevcut: '1',
  yeni: '2',
  durum,
  neden: null,
  dayanak: null,
  kaynak: null,
  guvenilirlik: null,
})
const SATIRLAR = [
  s('a', 'DEGISIR'),
  s('b', 'AYNI'),
  s('c', 'GECERSIZ', 'SIDDET'),
  s('d', 'BILINMEYEN', 'AYAR'),
  s('e', 'PROFILDE_YOK'),
  s('f', 'DEGISIR', 'OZEL_KURAL'),
]

describe('profil fark yardımcıları (saf)', () => {
  it('beş durumun görünümü durum.ts tek kaynağından: ton + geçerli simge + metin', () => {
    const tonlar = (['DEGISIR', 'AYNI', 'GECERSIZ', 'BILINMEYEN', 'PROFILDE_YOK'] as const).map(
      (d) => {
        const g = durumGorunumu(d)
        expect(g.simge in SIMGELER).toBe(true)
        expect(g.metin).not.toBe(d) // bilinmeyen kod geri düşüşü değil, tanımlı metin
        return g.ton
      },
    )
    expect(tonlar).toEqual(['bilgi', 'notr', 'engel', 'uyari', 'notr'])
  })

  it('varsayılan görünüm yalnız DEGISIR ve GECERSIZ; "hepsini göster" tümünü açar', () => {
    expect(SATIRLAR.filter((x) => varsayilanGorunur(x.durum)).map((x) => x.kimlik)).toEqual([
      'a',
      'c',
      'f',
    ])
    expect(gorunenSatirlar(SATIRLAR, false).map((x) => x.kimlik)).toEqual(['a', 'c', 'f'])
    expect(gorunenSatirlar(SATIRLAR, true)).toHaveLength(6)
  })

  it('yalnız DEGISIR seçilebilir; ilk seçim tüm DEGISIR satırlar', () => {
    expect(SATIRLAR.filter(secilebilir).map((x) => x.kimlik)).toEqual(['a', 'f'])
    expect([...varsayilanSecim(SATIRLAR)]).toEqual(['a', 'f'])
  })

  it('seçilenlerden biri uygulanamazsa seçim geçersiz (sunucu 422 verirdi)', () => {
    expect(secimGecersizMi(SATIRLAR, new Set(['a', 'f']))).toBe(false)
    expect(secimGecersizMi(SATIRLAR, new Set(['a', 'c']))).toBe(true)
    expect(secimGecersizMi(SATIRLAR, new Set())).toBe(false)
  })

  it('değer metni: null "—", boş metin "(boş)"', () => {
    expect(degerMetni(null)).toBe('—')
    expect(degerMetni('')).toBe('(boş)')
    expect(degerMetni('12')).toBe('12')
  })

  it('gruplar sabit sırada ve boş grup yok', () => {
    expect(gruplaSatirlar(SATIRLAR).map((g) => [g.tur, g.satirlar.length])).toEqual([
      ['PARAMETRE', 3],
      ['SIDDET', 1],
      ['AYAR', 1],
      ['OZEL_KURAL', 1],
    ])
    expect(gruplaSatirlar([s('x', 'AYNI', 'AYAR')]).map((g) => g.tur)).toEqual(['AYAR'])
  })
})
