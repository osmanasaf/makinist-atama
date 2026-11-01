import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    ata: vi.fn(),
    kaldir: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
    oneri: vi.fn(),
    uygunSeferler: vi.fn(),
    otomatik: vi.fn(),
    seferIptal: vi.fn(),
    seferGecikme: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import GunListesiYazdir from '@/features/yazdir/GunListesiYazdir.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }

/** F2c: Ankara–Sivas–Kars treni iki sefer satırı (1/2, 2/2); üçüncü satır parçasız. */
const liste: GunListesi = {
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [
    satir(1, { trenNo: 'DE-1503', parcaNo: 1, parcaSayisi: 2, makinistler: [makinist()] }),
    satir(2, { trenNo: 'DE-1503', kalkisGar: 'Sivas', parcaNo: 2, parcaSayisi: 2 }),
    satir(3, { trenNo: 'T1003', parcaNo: null, parcaSayisi: null }),
  ],
}

const router = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })

describe('parça etiketi (F2c, FR-08)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(liste))
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
  })

  it('atama masası: parçalı satırda "DE-1503 · 1/2", parçasızda yalnız tren no', async () => {
    const r = router()
    await r.push('/atama?tarih=2026-10-12')
    const w = mount(AtamaMasasi, { global: { plugins: [r] } })
    await flushPromises()
    const tren = w.findAll('tr.sefer-satiri').map((tr) => tr.findAll('td')[1]!.text())

    expect(tren).toEqual(['DE-1503 · 1/2', 'DE-1503 · 2/2', 'T1003'])
    // K7: gecikme eylemi ⋯ menüsünde; satır satır açılır
    const satirlar = w.findAll('tr.sefer-satiri')
    await satirlar[1]!.get('button[aria-haspopup="menu"]').trigger('click')
    expect(w.find('[aria-label="DE-1503 · 2/2 seferine gecikme gir"]').exists()).toBe(true)
    await satirlar[2]!.get('button[aria-haspopup="menu"]').trigger('click')
    expect(w.find('[aria-label="T1003 seferine gecikme gir"]').exists()).toBe(true)
  })

  it('yazdırma listesi: aynı etiket, renksiz metin', async () => {
    const r = router()
    const w = mount(GunListesiYazdir, { props: { tarih: '2026-10-12' }, global: { plugins: [r] } })
    await flushPromises()
    const tren = w.findAll('tbody tr').map((tr) => tr.findAll('td')[1]!.text())

    expect(tren).toEqual(['DE-1503 · 1/2', 'DE-1503 · 2/2', 'T1003'])
  })
})
