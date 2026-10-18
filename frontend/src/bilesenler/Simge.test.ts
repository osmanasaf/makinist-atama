import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Simge from './Simge.vue'
import { SIMGELER, type SimgeAdi } from './simgeler'

describe('Simge', () => {
  it('simge seti 36 simgeyi aşmaz', () => {
    expect(Object.keys(SIMGELER).length).toBeLessThanOrEqual(36)
  })

  it.each(Object.keys(SIMGELER) as SimgeAdi[])('%s: path üretir, dekoratif', (ad) => {
    const w = mount(Simge, { props: { ad } })
    expect(w.find('path').attributes('d')).toBe(SIMGELER[ad])
    expect(w.element.getAttribute('aria-hidden')).toBe('true')
    expect(w.element.getAttribute('focusable')).toBe('false')
    expect(w.element.getAttribute('data-simge')).toBe(ad)
  })

  it('boyut verilmezse 1.25em, verilirse o', () => {
    expect(mount(Simge, { props: { ad: 'ev' } }).element.getAttribute('style')).toContain('1.25em')
    expect(
      mount(Simge, { props: { ad: 'ev', boyut: '2rem' } }).element.getAttribute('style'),
    ).toContain('2rem')
  })

  it('bilinmeyen ad uyarır ve path çizmez', () => {
    const uyari = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const w = mount(Simge, { props: { ad: 'yok' as SimgeAdi } })
    expect(uyari).toHaveBeenCalled()
    expect(w.find('path').exists()).toBe(false)
    uyari.mockRestore()
  })
})
