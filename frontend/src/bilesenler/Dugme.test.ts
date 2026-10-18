import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Dugme from './Dugme.vue'

describe('Dugme', () => {
  it('varsayılan: ikincil, type=button, yerel button', () => {
    const w = mount(Dugme, { slots: { default: 'Kaydet' } })
    expect(w.element.tagName).toBe('BUTTON')
    expect(w.attributes('type')).toBe('button')
    expect(w.classes()).toEqual(['btn'])
    expect(w.text()).toBe('Kaydet')
  })

  it.each([
    ['birincil', 'btn-birincil'],
    ['hayalet', 'btn-hayalet'],
    ['tehlike', 'btn-tehlike'],
  ] as const)('varyant %s -> %s', (varyant, sinif) => {
    expect(mount(Dugme, { props: { varyant }, slots: { default: 'x' } }).classes()).toContain(sinif)
  })

  it('boyut sm/dokunma sınıfları', () => {
    expect(mount(Dugme, { props: { boyut: 'sm' }, slots: { default: 'x' } }).classes()).toContain(
      'btn-kucuk',
    )
    expect(
      mount(Dugme, { props: { boyut: 'dokunma' }, slots: { default: 'x' } }).classes(),
    ).toContain('btn-dokunma')
  })

  it('yükleniyor: devre dışı + aria-busy, metin DEĞİŞMEZ', () => {
    const w = mount(Dugme, { props: { yukleniyor: true }, slots: { default: 'Giriş yap' } })
    expect(w.attributes('disabled')).toBeDefined()
    expect(w.attributes('aria-busy')).toBe('true')
    expect(w.text()).toBe('Giriş yap')
  })

  it('tıklama olayı ebeveyne geçer; disabled devre dışı bırakır', async () => {
    const tikla = vi.fn()
    const w = mount(Dugme, { attrs: { onClick: tikla }, slots: { default: 'x' } })
    await w.trigger('click')
    expect(tikla).toHaveBeenCalledTimes(1)
    await w.setProps({ disabled: true })
    expect(w.attributes('disabled')).toBeDefined()
  })

  it('yalnız simge: aria-label ve title etiketten, btn-kare, simge svg var', () => {
    const w = mount(Dugme, { props: { simge: 'kapat', etiket: 'Kapat' } })
    expect(w.attributes('aria-label')).toBe('Kapat')
    expect(w.attributes('title')).toBe('Kapat')
    expect(w.classes()).toContain('btn-kare')
    expect(w.find('svg[data-simge="kapat"]').exists()).toBe(true)
  })

  it('yalnız simge + etiket yok: uyarı', () => {
    const uyari = vi.spyOn(console, 'warn').mockImplementation(() => {})
    mount(Dugme, { props: { simge: 'kapat' } })
    expect(uyari).toHaveBeenCalled()
    uyari.mockRestore()
  })

  it('simge + metin: erişilebilir ad metindir (aria-label yok, btn-kare yok)', () => {
    const w = mount(Dugme, { props: { simge: 'ekle' }, slots: { default: 'Ekle' } })
    expect(w.attributes('aria-label')).toBeUndefined()
    expect(w.classes()).not.toContain('btn-kare')
  })

  it('tip=submit', () => {
    expect(
      mount(Dugme, { props: { tip: 'submit' }, slots: { default: 'x' } }).attributes('type'),
    ).toBe('submit')
  })
})
