import { describe, expect, it } from 'vitest'
import {
  atamaHaritasi,
  adayHaritasi,
  isaretMetinleri,
  isaretlerMetni,
  sureMetni,
  yorgunlukAlanlari,
  yorgunlukOzeti,
} from './yakinlik'
import type { YakinlikEsikleri, YakinlikYaniti, YorgunlukOzeti } from './atamaApi'

const esikler: YakinlikEsikleri = { pencereGun: 7, toplamSaat: 40, geceAdet: 3, dinlenmeSaat: 12 }
const yorgunluk = (ek: Partial<YorgunlukOzeti> = {}): YorgunlukOzeti => ({
  pencereGun: 7,
  toplamDk: 38 * 60,
  geceNobetSayisi: 2,
  enKisaDinlenmeDk: 11 * 60,
  ...ek,
})
const yanit = (ek: Partial<YakinlikYaniti> = {}): YakinlikYaniti => ({
  marjAktif: true,
  esikler,
  satirlar: [
    {
      makinistId: 5,
      atamaId: null,
      yakinlik: [
        {
          kod: 'K-13',
          olculen: 620,
          sinir: 660,
          kalan: 40,
          birim: 'dk',
          metin: 'Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s',
        },
      ],
      yorgunluk: yorgunluk(),
    },
    { makinistId: 6, atamaId: 70, yakinlik: [], yorgunluk: yorgunluk() },
  ],
  ...ek,
})

describe('sureMetni', () => {
  it('sunucunun biçimiyle aynı: "10 s 20 dk", "11 s", "30 dk", "0 dk"', () => {
    expect(sureMetni(620)).toBe('10 s 20 dk')
    expect(sureMetni(660)).toBe('11 s')
    expect(sureMetni(30)).toBe('30 dk')
    expect(sureMetni(0)).toBe('0 dk')
  })
})

describe('yorgunlukOzeti / yorgunlukAlanlari', () => {
  it('"7 g: 38 s · 2 gece · en kısa dinlenme 11 s" ve tek nöbette dinlenme "—"', () => {
    expect(yorgunlukOzeti(yorgunluk())).toBe('7 g: 38 s · 2 gece · en kısa dinlenme 11 s')
    expect(yorgunlukOzeti(yorgunluk({ enKisaDinlenmeDk: null }))).toBe(
      '7 g: 38 s · 2 gece · en kısa dinlenme —',
    )
  })

  it('eşik aşımı yalnız vurgu: toplam > eşik, gece >= eşik, dinlenme < eşik; sınırda vurgu yok/var', () => {
    const al = (y: YorgunlukOzeti) => yorgunlukAlanlari(y, esikler).map((a) => a.vurgulu)
    expect(al(yorgunluk())).toEqual([false, false, true]) // 11 s < 12 s
    expect(
      al(yorgunluk({ toplamDk: 40 * 60, geceNobetSayisi: 3, enKisaDinlenmeDk: 12 * 60 })),
    ).toEqual([false, true, false])
    expect(al(yorgunluk({ toplamDk: 40 * 60 + 1 }))[0]).toBe(true)
    expect(al(yorgunluk({ enKisaDinlenmeDk: null }))[2]).toBe(false)
  })

  it('her alan eşiğini metin olarak taşır (şeffaflık)', () => {
    const a = yorgunlukAlanlari(yorgunluk(), esikler)
    expect(a.map((x) => x.esik)).toEqual(['40 s üstü', '3 ve üstü', '12 s altı'])
    expect(a.map((x) => x.ad)).toEqual(['Çalışma', 'Gece nöbeti', 'En kısa dinlenme'])
    expect(a.map((x) => x.kisa)).toEqual(['38 s', '2 gece', 'en kısa dinlenme 11 s'])
  })
})

describe('haritalar', () => {
  it('aday haritası makinist kimliğine, atama haritası atama kimliğine göre; pasif/boş yanıt boş harita', () => {
    expect(adayHaritasi(yanit()).get(5)?.yakinlik).toHaveLength(1)
    expect(atamaHaritasi(yanit()).get(70)?.makinistId).toBe(6)
    expect(atamaHaritasi(yanit()).has(5)).toBe(false) // atamaId yok
    expect(adayHaritasi(yanit({ marjAktif: false })).size).toBe(0)
    expect(adayHaritasi(null).size).toBe(0)
    expect(atamaHaritasi(undefined).size).toBe(0)
  })

  it('D-12: isaretMetinleri yalnız ölçüm metinlerini birleştirir (rozet metni karışmaz); isaretlerMetni ona yaslanır', () => {
    const isaretler = yanit().satirlar[0]!.yakinlik
    expect(isaretMetinleri(isaretler)).toBe(isaretler.map((i) => i.metin).join('; '))
    expect(isaretMetinleri(isaretler)).not.toContain('Sınıra yakın')
    expect(isaretlerMetni(isaretler)).toBe(`Sınıra yakın: ${isaretMetinleri(isaretler)}`)
    expect(isaretMetinleri([])).toBe('')
  })

  it('isaretlerMetni: title için işaret metinlerini birleştirir', () => {
    expect(isaretlerMetni(yanit().satirlar[0]!.yakinlik)).toBe(
      'Sınıra yakın: Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s',
    )
    expect(isaretlerMetni([])).toBe('')
  })
})
