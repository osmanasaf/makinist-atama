import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./seferApi', () => ({ seferApi: { liste: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))

import { tanimApi } from '@/features/tanim/tanimApi'
import SeferListesi from './SeferListesi.vue'
import { seferApi, type Sefer } from './seferApi'

const sefer = (ek: Partial<Sefer> = {}): Sefer => ({
  id: 1,
  trenNo: '1001',
  tarih: '2026-10-12',
  kalkisGarId: 1,
  kalkisGar: 'Ankara',
  varisGarId: 2,
  varisGar: 'İzmir',
  planlananKalkis: '2026-10-12T05:00:00Z',
  planlananVaris: '2026-10-12T11:00:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  guncelKalkis: '2026-10-12T05:00:00Z',
  guncelVaris: '2026-10-12T11:00:00Z',
  aracTipiId: 1,
  aracTipi: 'E68000',
  hatId: 1,
  hat: 'Ankara-İzmir',
  km: 520,
  durum: 'PLANLI',
  versiyon: 0,
  ...ek,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'sefer-yukle', component: { template: '<div />' } }],
  })
  await router.push('/')
  const w = mount(SeferListesi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('SeferListesi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockResolvedValue([])
    vi.mocked(seferApi.liste).mockResolvedValue([sefer(), sefer({ id: 2, durum: 'IPTAL' })])
  })

  it('tek h1, başlık eylemleri; durum rozeti (iptal = ton + simge + metin), km sağa hizalı', async () => {
    const w = await kur()
    expect(w.findAll('h1')).toHaveLength(1)
    expect(w.get('h1').text()).toBe('Seferler')
    expect(w.get('.sayfa-baslik-eylemler').text()).toContain('Excel ile yükle')
    expect(w.get('.sayfa-baslik-eylemler').text()).toContain('Sefer ekle')
    const satirlar = w.findAll('tbody tr')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.get('.rozet').text()).toBe('Planlı')
    const iptal = satirlar[1]!.get('.rozet')
    expect(iptal.text()).toBe('İptal')
    expect(iptal.classes()).toContain('rozet-engel')
    expect(iptal.get('svg').attributes('data-simge')).toBe('iptal')
    expect(satirlar[0]!.findAll('td')[7]!.classes()).toContain('sayisal')
  })

  it('Düzenle düğmesi satır başına ayrı erişilebilir ad taşır (görünen metin adın başında)', async () => {
    vi.mocked(seferApi.liste).mockResolvedValue([sefer(), sefer({ id: 2, trenNo: 'T2' })])
    const w = await kur()
    const adlar = w.findAll('tbody tr').map((tr) => tr.get('button').attributes('aria-label'))
    expect(adlar).toHaveLength(2)
    expect(new Set(adlar).size).toBe(2)
    for (const a of adlar) expect(a!.startsWith('Düzenle: ')).toBe(true)
  })

  it('yüklenirken "Yükleniyor…", sefer yoksa boş durum, hata varsa hata durumu', async () => {
    let bitir!: (v: Sefer[]) => void
    vi.mocked(seferApi.liste).mockReturnValue(new Promise((c) => (bitir = c)))
    const w = await kur()
    expect(w.get('[role="status"]').text()).toContain('Yükleniyor…')
    bitir([])
    await flushPromises()
    expect(w.text()).toContain('Bu gün için sefer yok.')

    vi.mocked(seferApi.liste).mockRejectedValue(new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası.'))
    const h = await kur()
    expect(h.get('[role="alert"]').text()).toContain('Sunucu hatası.')
    expect(h.text()).not.toContain('Bu gün için sefer yok.')
  })

  it('Sefer ekle formu açar', async () => {
    const w = await kur()
    await w
      .findAll('button')
      .find((b) => b.text() === 'Sefer ekle')!
      .trigger('click')
    expect(w.find('form.sefer-formu').exists()).toBe(true)
  })
})
