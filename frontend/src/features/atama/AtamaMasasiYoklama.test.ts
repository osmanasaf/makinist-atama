import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
  },
}))
vi.mock('@/features/takas/takasApi', () => ({ takasApi: { liste: vi.fn() } }))
vi.mock('@/features/intikal/intikalApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/intikal/intikalApi')>()),
  intikalApi: { liste: () => Promise.resolve([]) },
}))

import { takasApi } from '@/features/takas/takasApi'
import { YOKLAMA_MS } from '@/features/benim/useYoklama'
import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GordumOzeti, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }

const gun = (gordu: 'GORMEDI' | 'GORDU'): GunListesi => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [satir(1, { makinistler: [makinist({ gordu })] })],
})

const ozet = (gorulmeyen: number): GordumOzeti => ({
  bas: '2026-10-12',
  bit: '2026-10-19',
  toplam: 1,
  gorulen: 1 - gorulmeyen,
  gorulmeyen,
  hesapsiz: 0,
  uyari: 0,
})

const kurulan: VueWrapper[] = []
afterEach(() => {
  kurulan.splice(0).forEach((w) => w.unmount())
  vi.useRealTimers()
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/takas', name: 'takas', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('AtamaMasasi: takas rozeti ve yoklama', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue(gun('GORMEDI'))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue(ozet(1))
    vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 2, talepler: [] })
  })

  it('üst çubukta "Bekleyen talep: n" rozeti takas sayfasına bağlanır', async () => {
    const w = await kur()
    const rozet = w.get('[data-test=takas-sayac]')
    expect(rozet.text()).toContain('Bekleyen talep: 2')
    expect(rozet.get('a').attributes('href')).toBe('/takas')
    expect(takasApi.liste).toHaveBeenCalledWith('ATAMACI_ONAYI_BEKLIYOR') // yoklama tam liste çekmez (W4)
  })

  it('talep sayacı yüklenemezse masa çalışır, rozet gösterilmez', async () => {
    vi.mocked(takasApi.liste).mockRejectedValue(new Error('x'))
    const w = await kur()
    expect(w.find('[data-test=takas-sayac]').exists()).toBe(false)
    expect(w.find('tr.sefer-satiri').exists()).toBe(true)
  })

  it('60 sn yoklama: makinist "gördüm" deyince masa kendiliğinden "gördü"ye döner; sayaçlar da yenilenir', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const w = await kur()
    expect(w.get('[data-test=gormedi]').text()).toContain('görmedi')
    expect(w.get('[data-test=gordum-sayac]').text()).toContain('Görülmeyen: 1')

    vi.mocked(atamaApi.gun).mockResolvedValue(gun('GORDU'))
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue(ozet(0))
    vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 0, talepler: [] })
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS)
    await flushPromises()

    expect(w.find('[data-test=gormedi]').exists()).toBe(false)
    expect(w.get('[data-test=gordu]').text()).toContain('gördü')
    expect(w.get('[data-test=gordum-sayac]').text()).toContain('Görülmeyen: 0')
    expect(w.get('[data-test=takas-sayac]').text()).toContain('Bekleyen talep: 0')
  })

  it('yoklama hatası mevcut listeyi silmez ve hata bildirimi çıkarmaz', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    const w = await kur()
    vi.mocked(atamaApi.gun).mockRejectedValue(new Error('ağ'))
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS)
    await flushPromises()
    expect(w.find('tr.sefer-satiri').exists()).toBe(true)
    expect(w.find('.bildirim-hata').exists()).toBe(false)
  })
})
