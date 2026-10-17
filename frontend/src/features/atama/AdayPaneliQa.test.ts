import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdayPaneli from './AdayPaneli.vue'
import type { Aday } from './atamaApi'

// M2 QA: sunucu bu ay saatini ham double olarak döner (örn. 1.4666666666666666, 28.700000000000003).
// Sütunlar (BUG-8 sonrası): 0 simge, 1 sicil+ad, 2 bu ay saat, 3 bu ay km, 4 durum.
const aday = (buAySaat: number, buAyKm = 245): Aday => ({
  makinistId: 1,
  sicil: 'D004',
  adSoyad: 'Demo Makinist 04',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat,
  buAyKm,
  buSeferdeRol: null,
})
const hucre = (w: ReturnType<typeof mount>, sutun: number) =>
  w.findAll('tr[data-aday]').map((r) => r.findAll('td')[sutun]!.text())

describe('AdayPaneli (QA, M2)', () => {
  it('BUG-7: bu ay saat en çok 2 ondalıkla ve Türkçe virgülle gösterilir (ham double sızmaz)', () => {
    const w = mount(AdayPaneli, {
      props: { adaylar: [aday(1.4666666666666666), aday(28.700000000000003)], rol: 'ASIL' },
    })
    expect(hucre(w, 2)).toEqual(['1,47', '28,7'])
  })

  it('bu ay km de aynı biçimde gösterilir', () => {
    const w = mount(AdayPaneli, {
      props: { adaylar: [aday(1, 28.700000000000003)], rol: 'ASIL' },
    })
    expect(hucre(w, 3)).toEqual(['28,7'])
  })

  it('tam sayı ve yarım saat değerleri bozulmadan görünür', () => {
    const w = mount(AdayPaneli, { props: { adaylar: [aday(0), aday(120)], rol: 'ASIL' } })
    expect(hucre(w, 2)).toEqual(['0', '120'])
  })
})
