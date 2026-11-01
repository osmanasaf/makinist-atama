import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdayPaneli from './AdayPaneli.vue'
import type { Aday, YakinlikIsareti, YakinlikYaniti } from './atamaApi'

// OK-48: F3 "Sınıra yakın" bilgisi aday panelinde AYRI satır eklemez: adayın durum satırında kısa rozet ("Sınıra yakın" / "Sınıra yakın (n)"),
// erişilebilir ad tüm ölçüm metinlerini içerir, görünür ölçüm metinleri yalnız odaklı adayda açılır.
const aday = (id: number): Aday => ({
  makinistId: id,
  sicil: `D00${id}`,
  adSoyad: `Aday ${id}`,
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 120,
  buAyKm: 8000,
  buSeferdeRol: null,
})
const yorgunluk = {
  pencereGun: 7,
  toplamDk: 38 * 60,
  geceNobetSayisi: 2,
  enKisaDinlenmeDk: 11 * 60,
}
const isaret = (kod: string, metin: string): YakinlikIsareti => ({
  kod,
  olculen: 1,
  sinir: 1,
  kalan: 0,
  birim: 'adet',
  metin,
})
const GECE = isaret('K-GECE', 'Ardışık gece haftası: 1 / 1')
const GUNLUK = isaret('K-13', 'Günlük süre (12 Eki 2026): 10 s 20 dk / 11 s')
const yakinlik: YakinlikYaniti = {
  marjAktif: true,
  esikler: { pencereGun: 7, toplamSaat: 40, geceAdet: 3, dinlenmeSaat: 12 },
  satirlar: [
    { makinistId: 1, atamaId: null, yakinlik: [GECE], yorgunluk },
    { makinistId: 2, atamaId: null, yakinlik: [GECE, GUNLUK], yorgunluk },
    { makinistId: 3, atamaId: null, yakinlik: [], yorgunluk },
  ],
}
const kur = () =>
  mount(AdayPaneli, { props: { adaylar: [aday(1), aday(2), aday(3)], rol: 'ASIL', yakinlik } })
const satir = (w: ReturnType<typeof kur>, i: number) => w.findAll('tr[data-aday]')[i]!

describe('AdayPaneli: "Sınıra yakın" kısa rozet (OK-48)', () => {
  it('rozet durum hücresinde satır içi; ayrı liste/blok yok; n=1 sayısız, n>1 "(n)"', () => {
    const w = kur()
    const not1 = satir(w, 0).get('[data-test="yakinlik-notu"]')
    expect(not1.element.tagName).toBe('SPAN')
    expect(not1.element.parentElement?.classList.contains('aday-durum')).toBe(true)
    expect(satir(w, 0).find('.yakinlik-listesi').exists()).toBe(false)
    expect(not1.get('.rozet').text()).toBe('Sınıra yakın')
    expect(satir(w, 1).get('[data-test="yakinlik-notu"] .rozet').text()).toBe('Sınıra yakın (2)')
    expect(satir(w, 1).findAll('[data-test="yakinlik-isareti"]')).toHaveLength(1) // n isaret = tek rozet
    expect(satir(w, 2).find('[data-test="yakinlik-notu"]').exists()).toBe(false)
  })

  it('erişilebilir ad TÜM ölçüm metinlerini içerir (gizli metin), odak yokken görünür ölçüm metni yok', () => {
    const w = kur()
    const gizli = satir(w, 1).get('[data-test="yakinlik-isareti"] .gorunmez').text()
    expect(gizli).toContain(GECE.metin)
    expect(gizli).toContain(GUNLUK.metin)
    expect(w.find('[data-test="yakinlik-ayrinti"]').exists()).toBe(false)
  })

  it('ölçüm metinleri yalnız odaklı adayda görünür (aria-hidden: çift okuma yok), odak çıkınca kapanır', async () => {
    const w = kur()
    await satir(w, 1).trigger('focusin')
    const ayrinti = satir(w, 1).get('[data-test="yakinlik-ayrinti"]')
    expect(ayrinti.attributes('aria-hidden')).toBe('true')
    expect(ayrinti.text()).toContain(GECE.metin)
    expect(ayrinti.text()).toContain(GUNLUK.metin)
    expect(satir(w, 0).find('[data-test="yakinlik-ayrinti"]').exists()).toBe(false)
    expect(w.findAll('[data-test="yakinlik-ayrinti"]')).toHaveLength(1)
    await satir(w, 1).trigger('focusout')
    expect(w.find('[data-test="yakinlik-ayrinti"]').exists()).toBe(false)
  })

  it('tek odak yöntemi: aynı satırda yorgunluk ayrıntısı ve yakınlık ayrıntısı birlikte açılır, rozet bilgi tonunda kalır', async () => {
    const w = kur()
    await satir(w, 0).trigger('focusin')
    expect(satir(w, 0).find('[data-test="yorgunluk-detay"]').exists()).toBe(true)
    expect(satir(w, 0).find('[data-test="yakinlik-ayrinti"]').exists()).toBe(true)
    expect(satir(w, 0).find('.rozet-bilgi').exists()).toBe(true)
  })
})
