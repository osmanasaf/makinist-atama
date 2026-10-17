import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))

import { atamaApi, type GunListesi } from '@/features/atama/atamaApi'
import { makinist, satir } from '@/features/atama/testVerisi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const liste: GunListesi = {
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:15:00Z',
  satirlar: [
    satir(1, {
      makinistler: [
        makinist({ atamaId: 1, sicil: 'D001', ad: 'Asıl Kişi' }),
        makinist({ atamaId: 2, rol: 'KONDUKTOR', makinistId: 9, sicil: 'K001', ad: 'Şef Kişi' }),
        makinist({ atamaId: 3, rol: 'YOLCU', makinistId: 7, sicil: 'D007', ad: 'Yolcu Kişi' }),
      ],
    }),
  ],
}

describe('GunListesiYazdir: kondüktör (F3b)', () => {
  it('kondüktör "Kond: Ad" satırı yolcudan önce basılır; sütun başlığı Personel', async () => {
    vi.mocked(atamaApi.gun).mockResolvedValue(liste)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/atama', name: 'atama', component: { template: '<div />' } }],
    })
    const w = mount(GunListesiYazdir, {
      props: { tarih: '2026-10-12' },
      global: { plugins: [router] },
    })
    await flushPromises()

    const hucre = w.findAll('tbody tr')[0]!.text()
    expect(hucre).toContain('Kond: Şef Kişi')
    expect(hucre.indexOf('Kond: Şef Kişi')).toBeLessThan(hucre.indexOf('Yolcu: D007'))
    expect(w.findAll('thead th').map((t) => t.text())).toContain('Personel')
  })
})
