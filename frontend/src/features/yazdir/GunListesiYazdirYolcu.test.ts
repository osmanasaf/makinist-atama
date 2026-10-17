import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))

import { atamaApi, type GunListesi } from '@/features/atama/atamaApi'
import { makinist, satir } from '@/features/atama/testVerisi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const bos = { template: '<div />' }

const liste: GunListesi = {
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:15:00Z',
  satirlar: [
    satir(1, {
      makinistler: [
        makinist({ atamaId: 1, sicil: 'D001', ad: 'Asıl Kişi' }),
        makinist({ atamaId: 2, rol: 'YOLCU', makinistId: 7, sicil: 'D007', ad: 'Yolcu Kişi' }),
        makinist({
          atamaId: 3,
          rol: 'YOLCU',
          makinistId: 8,
          sicil: 'D008',
          ad: 'Kırmızı Yolcu',
          kirmizi: true,
          kirmiziNedenler: ['Sefer iptal'],
        }),
      ],
    }),
    satir(2, {
      makinistler: [
        makinist({ atamaId: 4, rol: 'YOLCU', makinistId: 9, sicil: 'D009', ad: 'Tek Yolcu' }),
      ],
    }),
  ],
}

describe('GunListesiYazdir: yolcu (F2b)', () => {
  it('yolcular renksiz metinle "Yolcu: D007"; yalnız yolcusu olan sefer yine BOŞ', async () => {
    vi.mocked(atamaApi.gun).mockResolvedValue(liste)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/atama', name: 'atama', component: bos }],
    })
    const w = mount(GunListesiYazdir, {
      props: { tarih: '2026-10-12' },
      global: { plugins: [router] },
    })
    await flushPromises()
    const satirlar = w.findAll('tbody tr')

    expect(satirlar[0]!.text()).toContain('Asıl Kişi (D001)')
    expect(satirlar[0]!.text()).toContain('Yolcu: D007')
    expect(satirlar[0]!.text()).toContain('Yolcu: D008 (KONTROL: Sefer iptal)')
    expect(satirlar[0]!.text()).not.toContain('BOŞ')
    expect(satirlar[1]!.text()).toContain('BOŞ')
    expect(satirlar[1]!.text()).toContain('Yolcu: D009')
  })
})
