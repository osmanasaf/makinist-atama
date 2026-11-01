import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdayPaneli from './AdayPaneli.vue'
import type { Aday, YakinlikYaniti } from './atamaApi'

// OK-47 (a): yorgunluk aday panelinde AYRI satır eklemez; adayın durum satırında tek satırlık özet, ayrıntı yalnız odaklı adayda.
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
const yakinlik: YakinlikYaniti = {
  marjAktif: true,
  esikler: { pencereGun: 7, toplamSaat: 40, geceAdet: 3, dinlenmeSaat: 12 },
  satirlar: [1, 2].map((id) => ({ makinistId: id, atamaId: null, yakinlik: [], yorgunluk })),
}
const kur = () =>
  mount(AdayPaneli, { props: { adaylar: [aday(1), aday(2)], rol: 'ASIL', yakinlik } })
const satir = (w: ReturnType<typeof kur>, i: number) => w.findAll('tr[data-aday]')[i]!

describe('AdayPaneli: yorgunluk tek satırlık özet (OK-47a)', () => {
  it('özet ayrı blok değil, durum hücresinde satır içi (rozetle aynı satır)', () => {
    const w = kur()
    const ozet = satir(w, 0).get('[data-test="yorgunluk"]')
    expect(ozet.element.tagName).toBe('SPAN')
    expect(ozet.element.parentElement?.classList.contains('aday-durum')).toBe(true)
    expect(ozet.text()).toContain('7 g:')
    expect(ozet.text()).toContain('38 s')
  })

  it('ayrıntı (eşikler) odak yokken hiçbir satırda yok; odaklı TEK adayda açılır, odak çıkınca kapanır', async () => {
    const w = kur()
    expect(w.find('[data-test="yorgunluk-detay"]').exists()).toBe(false)
    await satir(w, 0).trigger('focusin')
    expect(satir(w, 0).find('[data-test="yorgunluk-detay"]').exists()).toBe(true)
    expect(satir(w, 1).find('[data-test="yorgunluk-detay"]').exists()).toBe(false)
    const detay = satir(w, 0).get('[data-test="yorgunluk-detay"]').text()
    expect(detay).toContain('Çalışma 38 s')
    expect(detay).toContain('40 s üstü')
    expect(detay).toContain('12 s altı')
    await satir(w, 1).trigger('focusin')
    expect(satir(w, 0).find('[data-test="yorgunluk-detay"]').exists()).toBe(false)
    expect(satir(w, 1).find('[data-test="yorgunluk-detay"]').exists()).toBe(true)
    await satir(w, 1).trigger('focusout')
    expect(w.find('[data-test="yorgunluk-detay"]').exists()).toBe(false)
  })

  it('satır içindeki bir öğeye odak geçişi (focusout, ilgili hedef satırın içinde) ayrıntıyı kapatmaz', async () => {
    const w = kur()
    await satir(w, 0).trigger('focusin')
    await satir(w, 0).trigger('focusout', {
      relatedTarget: satir(w, 0).element.querySelector('td'),
    })
    expect(satir(w, 0).find('[data-test="yorgunluk-detay"]').exists()).toBe(true)
  })
})
