import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Rozet from './Rozet.vue'

describe('Rozet', () => {
  it('metin her zaman görünür; ton verilmezse nötr', () => {
    const w = mount(Rozet, { props: { metin: 'Bilgi' } })
    expect(w.text()).toBe('Bilgi')
    expect(w.classes()).toEqual(['rozet', 'rozet-notr'])
  })

  it.each(['notr', 'basari', 'uyari', 'engel', 'bilgi', 'gece'] as const)('ton %s', (ton) => {
    expect(mount(Rozet, { props: { metin: 'x', ton } }).classes()).toContain(`rozet-${ton}`)
  })

  it.each([
    ['ASIL', 'rozet-rol-asil'],
    ['YARDIMCI', 'rozet-rol-yardimci'],
    ['KONDUKTOR', 'rozet-rol-konduktor'],
    ['YOLCU', 'rozet-rol-yolcu'],
  ] as const)('rol %s -> %s (ton sınıfı yok)', (rol, sinif) => {
    const w = mount(Rozet, { props: { metin: 'x', rol } })
    expect(w.classes()).toEqual(['rozet', sinif])
  })

  it('simge: svg + metin birlikte; boyut sm sınıfı', () => {
    const w = mount(Rozet, {
      props: { metin: 'Kırmızı', ton: 'engel', simge: 'engel', boyut: 'sm' },
    })
    expect(w.find('svg[data-simge="engel"]').exists()).toBe(true)
    expect(w.text()).toBe('Kırmızı')
    expect(w.classes()).toContain('rozet-sm')
  })
})
