import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VeriDurumu from './VeriDurumu.vue'

describe('VeriDurumu', () => {
  it('yükleniyor: role=status + aria-live=polite, spinner dekoratif', () => {
    const w = mount(VeriDurumu, { props: { durum: 'yukleniyor', metin: 'Yükleniyor…' } })
    expect(w.attributes('role')).toBe('status')
    expect(w.attributes('aria-live')).toBe('polite')
    expect(w.get('.donen').attributes('aria-hidden')).toBe('true')
    expect(w.text()).toContain('Yükleniyor…')
  })

  it('hata: role=alert, engel simgesi, ayrıntı ve eylem slotu', () => {
    const w = mount(VeriDurumu, {
      props: {
        durum: 'hata',
        metin: 'Sunucuya ulaşılamıyor.',
        ayrinti: 'Bağlantıyı kontrol edin.',
      },
      slots: { eylem: '<button>Yeniden dene</button>' },
    })
    expect(w.attributes('role')).toBe('alert')
    expect(w.find('svg[data-simge="engel"]').exists()).toBe(true)
    expect(w.text()).toContain('Bağlantıyı kontrol edin.')
    expect(w.get('.veri-durumu-eylem button').text()).toBe('Yeniden dene')
  })

  it('boş: rol yok, düz metin; eylem yoksa eylem kabı çizilmez', () => {
    const w = mount(VeriDurumu, { props: { durum: 'bos', metin: 'Kayıt yok.' } })
    expect(w.attributes('role')).toBeUndefined()
    expect(w.find('.veri-durumu-eylem').exists()).toBe(false)
    expect(w.text()).toBe('Kayıt yok.')
  })
})
