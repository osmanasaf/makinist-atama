import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DolulukEgrisi from './DolulukEgrisi.vue'
import type { KadroEgriNokta, KadroHedef } from './raporApi'

const noktalar: KadroEgriNokta[] = [
  { ek: 0, bos: 122, dolulukYuzde: 95.64, marjinal: null },
  { ek: 7, bos: 44, dolulukYuzde: 98.43, marjinal: 11.14 },
  { ek: 12, bos: 28, dolulukYuzde: 99, marjinal: 3.2 },
  { ek: 64, bos: 3, dolulukYuzde: 99.89, marjinal: 0.48 },
]
const hedefler: KadroHedef[] = [
  { yuzde: 98, ulasildi: true, ek: 7, bos: 44, dolulukYuzde: 98.43 },
  { yuzde: 99, ulasildi: true, ek: 12, bos: 28, dolulukYuzde: 99 },
  { yuzde: 100, ulasildi: false, ek: null, bos: 3, dolulukYuzde: 99.89 },
]

describe('DolulukEgrisi', () => {
  it('SVG: role=img + özet aria-label, çizgi path, hedef çizgileri doğrudan etiketli', () => {
    const w = mount(DolulukEgrisi, { props: { noktalar, hedefler } })
    const svg = w.get('svg[role="img"]')
    const ad = svg.attributes('aria-label')!
    expect(ad).toContain('4 nokta')
    expect(ad).toContain('%98 için +7')
    expect(ad).toContain('%100 için ulaşılamadı')
    expect(svg.find('path.egri-cizgi').attributes('d')).toMatch(/^M.*L/)
    expect(svg.findAll('line.egri-hedef')).toHaveLength(3)
    const etiketler = svg.findAll('.egri-hedef-etiket').map((t) => t.text())
    expect(etiketler).toEqual(['%100 hedef', '%99 hedef', '%98 hedef'])
    // renk dışı kodlama: hedefler kesikli çizgi sınıfında (stil), veri düz çizgi
    expect(svg.find('path.egri-cizgi').classes()).not.toContain('egri-hedef')
  })

  it('tablo görünümü <details> içinde: satır sayısı = nokta sayısı, marjinal değer görünür', () => {
    const w = mount(DolulukEgrisi, { props: { noktalar, hedefler } })
    const d = w.get('details[data-test="egri-tablo"]')
    expect(d.attributes('open')).toBeUndefined()
    expect(d.findAll('tbody tr')).toHaveLength(noktalar.length)
    expect(d.get('table').attributes('aria-label')).toBe('Doluluk eğrisi')
    expect(d.text()).toContain('11,14')
    expect(d.findAll('tbody tr')[0]!.text()).toContain('—')
  })

  it('60 noktadan uzun eğri: çizilen nokta ≤ 60, tablo tamamını gösterir', () => {
    const cok: KadroEgriNokta[] = Array.from({ length: 150 }, (_, i) => ({
      ek: i,
      bos: 150 - i,
      dolulukYuzde: 90 + i / 16,
      marjinal: i === 0 ? null : 1,
    }))
    const w = mount(DolulukEgrisi, { props: { noktalar: cok, hedefler } })
    expect(w.findAll('tbody tr')).toHaveLength(150)
    expect(w.findAll('circle.egri-nokta').length).toBeLessThanOrEqual(60)
    expect(
      (w.get('path.egri-cizgi').attributes('d')!.match(/[ML]/g) ?? []).length,
    ).toBeLessThanOrEqual(60)
  })

  it('klavye: odak ilk noktayı, ok tuşları sonrakini gösterir; Escape kapatır', async () => {
    const w = mount(DolulukEgrisi, { props: { noktalar, hedefler } })
    expect(w.find('[data-test="egri-ipucu"]').exists()).toBe(false)
    const kap = w.get('[data-test="egri-grafik"]')
    await kap.trigger('focus')
    expect(w.get('[data-test="egri-ipucu"]').text()).toContain('%95,64')
    await kap.trigger('keydown', { key: 'ArrowRight' })
    const t = w.get('[data-test="egri-ipucu"]').text()
    expect(t).toContain('%98,43')
    expect(t).toContain('+7 makinist')
    expect(t).toContain('44 boş sefer')
    await kap.trigger('keydown', { key: 'End' })
    expect(w.get('[data-test="egri-ipucu"]').text()).toContain('%99,89')
    await kap.trigger('keydown', { key: 'Escape' })
    expect(w.find('[data-test="egri-ipucu"]').exists()).toBe(false)
  })

  it('tek nokta: path çizilir, nokta işaretlenir, tablo tek satır', () => {
    const w = mount(DolulukEgrisi, { props: { noktalar: [noktalar[0]!], hedefler } })
    expect(w.find('path.egri-cizgi').exists()).toBe(true)
    expect(w.findAll('circle.egri-nokta')).toHaveLength(1)
    expect(w.findAll('tbody tr')).toHaveLength(1)
  })

  it('boş eğri: bos durumu, SVG ve tablo yok', () => {
    const w = mount(DolulukEgrisi, { props: { noktalar: [], hedefler: [] } })
    expect(w.find('svg[role="img"]').exists()).toBe(false)
    expect(w.find('table').exists()).toBe(false)
    expect(w.text()).toContain('Eğri verisi yok')
  })
})
