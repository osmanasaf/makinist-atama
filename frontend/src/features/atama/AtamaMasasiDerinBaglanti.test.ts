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
    adayYakinlik: vi.fn(),
    gunYakinlik: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

// F5 / AK-5.6: aylık çizelge hücresi `#/atama?tarih=<gün>&sefer=<id>` ile açar; gün yüklenince o sefer SEÇİLİ gelir (aday paneli açık).
const bos = { template: '<div />' }
const gunVerisi = (): GunListesi => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [
    satir(1, { makinistler: [makinist()] }),
    satir(2, { bosNeden: '0/3 uygun: dinlenme 3' }),
  ],
})

async function kur(sorgu: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push(`/atama?${sorgu}`)
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return { w, router }
}

describe('AtamaMasasi: derin bağlantı ?sefer= (F5, AK-5.6)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.gunYakinlik).mockResolvedValue(null as never)
    vi.mocked(atamaApi.adayYakinlik).mockResolvedValue(null as never)
  })

  it('tarih + sefer: gün yüklenince o sefer seçili (satır işaretli, adaylar o sefer için istenir)', async () => {
    const { w } = await kur('tarih=2026-10-12&sefer=2')
    const secili = w.findAll('tr.sefer-satiri.secili')
    expect(secili).toHaveLength(1)
    expect(secili[0]!.text()).toContain('T1002')
    expect(atamaApi.adaylar).toHaveBeenCalledWith(2, 'ASIL')
  })

  it('o günde olmayan, bozuk ya da sefer sorgusuz: seçim yok (masa normal açılır)', async () => {
    for (const sorgu of [
      'tarih=2026-10-12&sefer=99',
      'tarih=2026-10-12&sefer=abc',
      'tarih=2026-10-12&sefer=-3',
      'tarih=2026-10-12',
    ]) {
      const { w } = await kur(sorgu)
      expect(w.findAll('tr.sefer-satiri')).toHaveLength(2)
      expect(w.findAll('tr.sefer-satiri.secili')).toHaveLength(0)
    }
  })

  it('gün değiştirilince ?sefer= düşer: eski seçim yeni güne taşınmaz', async () => {
    const { w, router } = await kur('tarih=2026-10-12&sefer=2')
    await w
      .findAll('button')
      .find((b) => b.text().includes('Sonraki gün'))!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.sefer).toBeUndefined()
  })
})
