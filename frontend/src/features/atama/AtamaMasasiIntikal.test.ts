import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { gun: vi.fn(), adaylar: vi.fn(), kirmiziSayisi: vi.fn(), gordumOzeti: vi.fn() },
}))
vi.mock('@/features/takas/takasApi', () => ({ takasApi: { liste: vi.fn() } }))
vi.mock('@/features/intikal/intikalApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/intikal/intikalApi')>()),
  intikalApi: { liste: vi.fn(), ekle: vi.fn(), guncelle: vi.fn(), iptal: vi.fn() },
}))

import { takasApi } from '@/features/takas/takasApi'
import { intikalApi, type Intikal } from '@/features/intikal/intikalApi'
import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type KirmiziSayisi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const kirmizi = (ek: Partial<KirmiziSayisi> = {}): KirmiziSayisi => ({
  bas: '2026-10-12',
  bit: '2026-10-19',
  kirmiziSefer: 0,
  kirmiziAtama: 0,
  ...ek,
})
const intikal: Intikal = {
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
  aracTuru: 'SERVIS',
  notu: null,
  durum: 'PLANLI',
  versiyon: 0,
  gordu: 'YENI',
  gorduZamani: null,
  kirmizi: false,
  nedenler: [],
  uyarilar: [],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: AtamaMasasi },
      { path: '/yazdir/:tarih', name: 'yazdir-gun', component: bos },
      { path: '/takas', name: 'takas', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  await router.isReady()
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('AtamaMasasi: serbest intikal şeridi ve ayrı kırmızı sayacı (OK-28)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-12',
      uretimZamani: '2026-10-12T07:00:00Z',
      satirlar: [satir(1, { makinistler: [makinist()] })],
    })
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      toplam: 0,
      gorulen: 0,
      gorulmeyen: 0,
      hesapsiz: 0,
      uyari: 0,
    })
    vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 0 } as never)
    vi.mocked(intikalApi.liste).mockResolvedValue([])
  })

  it('şerit masanın günüyle yüklenir; sefer satırları ve BOŞ sayacı değişmez', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(kirmizi())
    const w = await kur()

    expect(intikalApi.liste).toHaveBeenCalledWith('2026-10-12', '2026-10-12')
    expect(w.find('[data-test="intikal-seridi"]').exists()).toBe(true)
    expect(w.findAll('tr.sefer-satiri')).toHaveLength(1)
    expect(w.get('[data-test="atanmamis-sayac"]').text()).toContain('0')
  })

  it('kırmızı intikal varsa ayrı sayaç görünür ve atama sayacını DEĞİŞTİRMEZ', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(
      kirmizi({ kirmiziAtama: 1, kirmiziSefer: 1, kirmiziIntikal: 2 }),
    )
    const w = await kur()

    expect(w.get('[data-test="kirmizi-intikal-sayac"]').text()).toContain('Kırmızı intikal: 2')
    expect(w.get('[data-test="kirmizi-sayac"]').text()).toContain('1 sefer')
  })

  it('kırmızı intikal 0 ya da alan yoksa sayaç çıkmaz', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(kirmizi())
    const w = await kur()

    expect(w.find('[data-test="kirmizi-intikal-sayac"]').exists()).toBe(false)
  })

  it('şeritte bir değişiklik masayı yeniler (gün listesi yeniden okunur)', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(kirmizi())
    vi.mocked(intikalApi.iptal).mockResolvedValue({ intikal, yeniKirmizi: [] })
    vi.mocked(intikalApi.liste).mockResolvedValue([intikal])
    const w = await kur()
    const once = vi.mocked(atamaApi.gun).mock.calls.length

    await w.get('[data-test="intikal-iptal-et"]').trigger('click')
    await flushPromises()
    await w.get('#intikal-neden').setValue('IZIN')
    await w.get('.intikal-dialog form').trigger('submit')
    await flushPromises()

    expect(intikalApi.iptal).toHaveBeenCalled()
    expect(vi.mocked(atamaApi.gun).mock.calls.length).toBeGreaterThan(once)
  })
})
