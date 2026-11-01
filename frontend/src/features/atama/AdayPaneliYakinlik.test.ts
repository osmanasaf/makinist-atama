import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdayPaneli from './AdayPaneli.vue'
import type { Aday, YakinlikYaniti } from './atamaApi'

const aday = (ek: Partial<Aday>): Aday => ({
  makinistId: 1,
  sicil: 'D001',
  adSoyad: 'Ali Veli',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 120,
  buAyKm: 8000,
  buSeferdeRol: null,
  ...ek,
})
const engelli = aday({
  makinistId: 2,
  sicil: 'D002',
  adSoyad: 'Engelli Kişi',
  uygun: false,
  ihlaller: [
    {
      kod: 'K-11',
      siddet: 'ENGEL',
      olculen: 8,
      esik: 11,
      birim: 's',
      metin: 'Dinlenme 8 s < 11 s',
    },
  ],
})
const uyarili = aday({
  makinistId: 3,
  sicil: 'D003',
  adSoyad: 'Uyarılı Kişi',
  ihlaller: [
    { kod: 'K-20', siddet: 'UYARI', olculen: 9, esik: 8, birim: 's', metin: 'Konum uyuşmazlığı' },
  ],
})
const seferde = aday({ makinistId: 4, sicil: 'D004', adSoyad: 'Zaten Var', buSeferdeRol: 'ASIL' })

const yorgunluk = {
  pencereGun: 7,
  toplamDk: 38 * 60,
  geceNobetSayisi: 2,
  enKisaDinlenmeDk: 11 * 60,
}
const isaret = {
  kod: 'K-13',
  olculen: 620,
  sinir: 660,
  kalan: 40,
  birim: 'dk',
  metin: 'Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s',
}
const yakinlik: YakinlikYaniti = {
  marjAktif: true,
  esikler: { pencereGun: 7, toplamSaat: 40, geceAdet: 3, dinlenmeSaat: 12 },
  satirlar: [1, 2, 3, 4].map((id) => ({
    makinistId: id,
    atamaId: null,
    yakinlik: [isaret],
    yorgunluk,
  })),
}

function kur(adaylar: Aday[], y?: YakinlikYaniti | null) {
  return mount(AdayPaneli, { props: { adaylar, rol: 'ASIL', yakinlik: y } })
}
const satir = (w: ReturnType<typeof kur>, i: number) => w.findAll('tr[data-aday]')[i]!

describe('AdayPaneli: sınıra yakınlık ve yorgunluk (F3, MK-176)', () => {
  it('yakınlık yoksa (prop verilmedi, null, marj kapalı) hiçbir işaret ve yorgunluk çizilmez', () => {
    for (const y of [undefined, null, { ...yakinlik, marjAktif: false }]) {
      const w = kur([aday({}), uyarili], y)
      expect(w.find('[data-test="yakinlik-notu"]').exists()).toBe(false)
      expect(w.text()).not.toContain('Sınıra yakın')
      expect(w.text()).not.toContain('Yorgunluk')
    }
  })

  it('işaret yalnız UYGUN adayda: engelli ve bu seferde zaten görevli adayda yok', () => {
    const w = kur([aday({}), engelli, uyarili, seferde], yakinlik)
    expect(satir(w, 0).find('[data-test="yakinlik-isareti"]').exists()).toBe(true)
    expect(satir(w, 1).find('[data-test="yakinlik-notu"]').exists()).toBe(false)
    expect(satir(w, 2).find('[data-test="yakinlik-isareti"]').exists()).toBe(true)
    expect(satir(w, 3).find('[data-test="yakinlik-notu"]').exists()).toBe(false)
  })

  it('metinli bilgi rozeti: "Sınıra yakın" + ölçüm metni; ton bilgi; kırmızı/uyarı/engel sınıfı YOK', () => {
    const w = kur([aday({}), uyarili], yakinlik)
    const not = satir(w, 0).get('[data-test="yakinlik-notu"]')
    expect(not.text()).toContain('Sınıra yakın')
    expect(not.text()).toContain('Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s')
    expect(not.find('.rozet-bilgi').exists()).toBe(true)
    expect(not.html()).not.toMatch(
      /rozet-(engel|uyari)|aday-uyari|aday-engel|kirmizi|bildirim-hata/,
    )
    expect(satir(w, 0).classes()).toContain('aday-basari') // satır durumu değişmedi
    expect(satir(w, 1).classes()).toContain('aday-uyari') // gerçek UYARI yakınlıktan bağımsız
  })

  it('yorgunluk özeti: "7 g" penceresi, üç ham değer, bileşik puan yok; eşik aşan değer vurgulu (simge + gizli metin)', () => {
    const w = kur([aday({})], yakinlik)
    const y = satir(w, 0).get('[data-test="yorgunluk"]')
    expect(y.text()).toMatch(/Yorgunluk \(bilgi\), 7 g:\s+38 s/)
    expect(y.text()).toMatch(/2 gece,\s*·\s*en kısa dinlenme/) // görünür ayraç ·, ekran okuyucu için gizli virgül
    expect(y.text()).toContain('38 s')
    expect(y.text()).toContain('2 gece')
    expect(y.text()).toContain('en kısa dinlenme 11 s')
    const vurgulu = y.findAll('[data-vurgulu="1"]')
    expect(vurgulu).toHaveLength(1) // yalnız 11 s < 12 s
    expect(vurgulu[0]!.text()).toContain('en kısa dinlenme 11 s')
    expect(vurgulu[0]!.text()).toContain('eşik: 12 s altı') // renk tek başına anlam taşımaz
    expect(vurgulu[0]!.find('svg, .simge').exists()).toBe(true)
    expect(y.attributes('title')).toContain('vurgu eşiği')
    expect(w.text()).not.toMatch(/puan|skor/i)
  })

  it('sıralama, "Önerilen" rozeti, seçilebilirlik ve sec olayı yakınlıktan etkilenmez', async () => {
    const adaylar = [aday({}), uyarili]
    const bos = kur(adaylar)
    const dolu = kur(adaylar, yakinlik)
    expect(dolu.findAll('tr[data-aday]').map((t) => t.find('.aday-ad').text())).toEqual(
      bos.findAll('tr[data-aday]').map((t) => t.find('.aday-ad').text()),
    )
    expect(dolu.findAll('.rozet').filter((r) => r.text() === 'Önerilen')).toHaveLength(1)
    expect(satir(dolu, 0).classes()).toContain('secilebilir')
    await satir(dolu, 0).trigger('click')
    expect(dolu.emitted('sec')).toEqual([[adaylar[0]]])
  })

  it('aday için yanıtta satır yoksa o satırda işaret yok (hata sessiz, ekran bozulmaz)', () => {
    const w = kur([aday({ makinistId: 99, sicil: 'D099' })], yakinlik)
    expect(w.find('[data-test="yakinlik-notu"]').exists()).toBe(false)
    expect(w.findAll('tr[data-aday]')).toHaveLength(1)
  })

  it('panel altında tek not: bilgi amaçlıdır, bilimsel model değildir; eşikler yazar', () => {
    const w = kur([aday({})], yakinlik)
    const not = w.get('[data-test="yakinlik-aciklama"]')
    expect(not.text()).toContain('bilgi amaçlıdır')
    expect(not.text()).toContain('bilimsel bir yorgunluk modeli değildir')
    expect(not.text()).toContain('40 s')
    expect(not.text()).toContain('12 s')
    expect(
      kur([aday({})])
        .find('[data-test="yakinlik-aciklama"]')
        .exists(),
    ).toBe(false)
  })
})
