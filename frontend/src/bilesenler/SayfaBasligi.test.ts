import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SayfaBasligi from './SayfaBasligi.vue'

describe('SayfaBasligi', () => {
  it('tek h1, sayfa-baslik sınıfı korunur', () => {
    const w = mount(SayfaBasligi, { props: { baslik: 'Seferler' } })
    expect(w.classes()).toContain('sayfa-baslik')
    expect(w.findAll('h1')).toHaveLength(1)
    expect(w.get('h1').text()).toBe('Seferler')
    expect(w.find('.sayfa-baslik-eylemler').exists()).toBe(false)
  })

  it('üst başlık, sayaçlar ve eylemler slotları', () => {
    const w = mount(SayfaBasligi, {
      props: { baslik: 'Atama masası', ust: 'Atama' },
      slots: { sayaclar: '<span data-test="s">3</span>', eylemler: '<button>Ekle</button>' },
    })
    expect(w.get('.sayfa-baslik-ust').text()).toBe('Atama')
    expect(w.get('[data-test=s]').text()).toBe('3')
    expect(w.get('.sayfa-baslik-eylemler button').text()).toBe('Ekle')
  })
})
