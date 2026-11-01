import { describe, expect, it } from 'vitest'
import type { Aday } from './atamaApi'
import { hizliAtanabilir, onerilenAday, ortalamayaFark, sonrakiBos } from './hizliDoldur'
import { makinist, satir } from './testVerisi'

const aday = (id: number, ek: Partial<Aday> = {}): Aday => ({
  makinistId: id,
  sicil: `D${id}`,
  adSoyad: `Kişi ${id}`,
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 100,
  buAyKm: 1000,
  buSeferdeRol: null,
  ...ek,
})
const uyari = { kod: 'K-07', siddet: 'UYARI', metin: 'x' } as Aday['ihlaller'][number]
const engel = { kod: 'K-06', siddet: 'ENGEL', metin: 'y' } as Aday['ihlaller'][number]

describe('onerilenAday (MK-151)', () => {
  it('listenin ilk temiz (uygun, bu seferde görevsiz, ihlalsiz) adayı', () => {
    const l = [aday(1, { ihlaller: [uyari] }), aday(2), aday(3)]
    expect(onerilenAday(l)?.makinistId).toBe(2)
  })
  it('uygun değil / bu seferde görevli / uyarılı adaylar önerilmez; hiç temiz yoksa null', () => {
    const l = [
      aday(1, { uygun: false, ihlaller: [engel] }),
      aday(2, { buSeferdeRol: 'YARDIMCI' }),
      aday(3, { ihlaller: [uyari] }),
    ]
    expect(onerilenAday(l)).toBeNull()
    expect(onerilenAday([])).toBeNull()
  })
  it('tek temiz aday listede son ise yine o önerilir', () => {
    expect(onerilenAday([aday(1, { ihlaller: [uyari] }), aday(9)])?.makinistId).toBe(9)
  })
})

describe('hizliAtanabilir (MK-150)', () => {
  it('temiz aday + boş rol + yolcu değil → true', () => {
    for (const rol of ['ASIL', 'YARDIMCI', 'KONDUKTOR'] as const)
      expect(hizliAtanabilir(aday(1), rol, null)).toBe(true)
  })
  it('YOLCU, dolu rol (değiştir), uyarılı, engelli, bu seferde görevli → false', () => {
    expect(hizliAtanabilir(aday(1), 'YOLCU', null)).toBe(false)
    expect(hizliAtanabilir(aday(1), 'ASIL', makinist())).toBe(false)
    expect(hizliAtanabilir(aday(1, { ihlaller: [uyari] }), 'ASIL', null)).toBe(false)
    expect(hizliAtanabilir(aday(1, { uygun: false, ihlaller: [engel] }), 'ASIL', null)).toBe(false)
    expect(hizliAtanabilir(aday(1, { buSeferdeRol: 'ASIL' }), 'ASIL', null)).toBe(false)
  })
})

describe('sonrakiBos (MK-154)', () => {
  const dolu = (id: number) => satir(id, { makinistler: [makinist()] })
  const iptal = (id: number) => satir(id, { durum: 'IPTAL' })

  it('şu andan sonraki ilk atanmamış, iptal olmayan sefer', () => {
    const l = [satir(1), dolu(2), iptal(3), satir(4), satir(5)]
    expect(sonrakiBos(l, 1)).toBe(4)
    expect(sonrakiBos(l, 4)).toBe(5)
  })
  it('sona gelince başa sarar; kendisini döndürmez', () => {
    const l = [satir(1), dolu(2), satir(3)]
    expect(sonrakiBos(l, 3)).toBe(1)
    expect(sonrakiBos([satir(1), dolu(2)], 1)).toBeNull() // yalnız kendisi boş
  })
  it('yardımcısı olup ASIL’ı olmayan sefer boştur; kalmadıysa null', () => {
    const yardimci = satir(2, { makinistler: [makinist({ rol: 'YARDIMCI' })] })
    expect(sonrakiBos([dolu(1), yardimci], 1)).toBe(2)
    expect(sonrakiBos([dolu(1), dolu(2), iptal(3)], 1)).toBeNull()
    expect(sonrakiBos([], 1)).toBeNull()
  })
  it('geçerli sefer listede yoksa (süzgeç) baştan ilk boş; suAnId null aynı', () => {
    const l = [dolu(1), satir(2), satir(3)]
    expect(sonrakiBos(l, 99)).toBe(2)
    expect(sonrakiBos(l, null)).toBe(2)
  })
})

describe('ortalamayaFark (OK-35, MK-155)', () => {
  it('havuz ortalamasına göre işaretli tam sayı yüzde', () => {
    const l = [aday(1, { buAySaat: 90 }), aday(2, { buAySaat: 100 }), aday(3, { buAySaat: 110 })]
    const f = ortalamayaFark(l, 'ASIL')
    expect(f.get(1)).toBe(-10)
    expect(f.get(2)).toBe(0)
    expect(f.get(3)).toBe(10)
  })
  it('yüzde yuvarlanır (ort 100, 108.4 → 8)', () => {
    const l = [aday(1, { buAySaat: 108.4 }), aday(2, { buAySaat: 91.6 })]
    expect(ortalamayaFark(l, 'ASIL').get(1)).toBe(8)
  })
  it('ortalama 0 ise null (bölme yok)', () => {
    const f = ortalamayaFark([aday(1, { buAySaat: 0 }), aday(2, { buAySaat: 0 })], 'ASIL')
    expect(f.get(1)).toBeNull()
    expect(f.get(2)).toBeNull()
  })
  it('OK-39: havuz ortalaması 1 saatin altındaysa yüzde yerine null ("-%100" gürültüsü yok); tam 1 saatte hesaplanır', () => {
    // ort 0.25 sa: bir kişi 1 sa çalışınca diğerleri "-%100" görünmesin
    const dusuk = ortalamayaFark(
      [
        aday(1, { buAySaat: 1 }),
        aday(2, { buAySaat: 0 }),
        aday(3, { buAySaat: 0 }),
        aday(4, { buAySaat: 0 }),
      ],
      'ASIL',
    )
    for (const id of [1, 2, 3, 4]) expect(dusuk.get(id)).toBeNull()
    const sinir = ortalamayaFark([aday(1, { buAySaat: 2 }), aday(2, { buAySaat: 0 })], 'ASIL') // ort tam 1 sa
    expect(sinir.get(1)).toBe(100)
    expect(sinir.get(2)).toBe(-100)
  })
  it('YOLCU rolünde boş harita (havuz karma); boş listede boş harita', () => {
    expect(ortalamayaFark([aday(1)], 'YOLCU').size).toBe(0)
    expect(ortalamayaFark([], 'ASIL').size).toBe(0)
  })
  it('tek aday kendi ortalaması: 0', () => {
    expect(ortalamayaFark([aday(1, { buAySaat: 50 })], 'KONDUKTOR').get(1)).toBe(0)
  })
})
