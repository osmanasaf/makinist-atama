import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))
vi.mock('@/features/intikal/intikalApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/intikal/intikalApi')>()),
  intikalApi: { liste: vi.fn() },
}))

import { atamaApi } from '@/features/atama/atamaApi'
import { intikalApi, type Intikal } from '@/features/intikal/intikalApi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const intikal = (ek: Partial<Intikal> = {}): Intikal => ({
  id: 1,
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  kalkisGarId: 1,
  kalkisGar: 'Ankara',
  varisGarId: 2,
  varisGar: 'Eskişehir',
  kalkis: '2026-10-12T07:00:00+03:00',
  varis: '2026-10-12T08:30:00+03:00',
  aracTuru: 'TAKSI',
  notu: 'iç not',
  durum: 'PLANLI',
  versiyon: 0,
  gordu: 'YENI',
  gorduZamani: null,
  kirmizi: false,
  nedenler: [],
  uyarilar: [],
  ...ek,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'atama', component: { template: '<div />' } }],
  })
  const w = mount(GunListesiYazdir, {
    props: { tarih: '2026-10-12' },
    global: { plugins: [router] },
  })
  await flushPromises()
  return w
}

describe('GunListesiYazdir: serbest intikaller', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-12',
      uretimZamani: '2026-10-12T07:15:00Z',
      satirlar: [],
    })
  })

  it('PLANLI intikaller ayrı tabloda, renksiz metinle (kırmızıysa KONTROL); iptal yok; atamacının notu basılmaz', async () => {
    vi.mocked(intikalApi.liste).mockResolvedValue([
      intikal(),
      intikal({ id: 2, kirmizi: true, nedenler: ['Dinlenme 9 s < 11 s'] }),
      intikal({ id: 3, durum: 'IPTAL', adSoyad: 'İptal Kişi' }),
    ])
    const w = await kur()
    const blok = w.get('[data-test="yazdir-intikal"]')

    expect(intikalApi.liste).toHaveBeenCalledWith('2026-10-12', '2026-10-12')
    expect(blok.findAll('tbody tr')).toHaveLength(2)
    expect(blok.text()).toContain('07:00–08:30')
    expect(blok.text()).toContain('Ali Veli (D007)')
    expect(blok.text()).toContain('Taksi')
    expect(blok.text()).toContain('(KONTROL: Dinlenme 9 s < 11 s)')
    expect(blok.text()).not.toContain('İptal Kişi')
    expect(blok.text()).not.toContain('iç not')
  })

  it('intikal yoksa ya da yüklenemezse hiçbir şey çizilmez, ana liste bozulmaz', async () => {
    vi.mocked(intikalApi.liste).mockRejectedValue(new Error('ağ'))
    const w = await kur()

    expect(w.find('[data-test="yazdir-intikal"]').exists()).toBe(false)
    expect(w.find('.bildirim-hata').exists()).toBe(false)
  })
})
