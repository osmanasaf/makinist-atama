import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
    birlestir: vi.fn(),
  },
}))
vi.mock('@/features/sefer/seferApi', () => ({ seferApi: { getir: vi.fn() } }))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { seferApi, type Sefer } from '@/features/sefer/seferApi'
import { eylemTetikle, satir } from './testVerisi'

const bos = { template: '<div />' }
const GELECEK = '2099-01-01T05:00:00Z'

const liste: GunListesi = {
  tarih: '2099-01-01',
  uretimZamani: '2099-01-01T00:00:00Z',
  satirlar: [
    satir(1, { guncelKalkis: GELECEK, parcaNo: 1, parcaSayisi: 2 }), // Birleştir var
    satir(2, { guncelKalkis: GELECEK }), // parçasız: yok
    satir(3, { guncelKalkis: GELECEK, parcaNo: 2, parcaSayisi: 2, durum: 'IPTAL' }), // iptal: yok
    satir(4, { guncelKalkis: GELECEK, parcaNo: 2, parcaSayisi: 2 }), // Birleştir var
  ],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2099-01-01')
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type W = Awaited<ReturnType<typeof kur>>
/** K7: Birleştir artık ⋯ menüsünde; menü satır satır açılıp "Birleştir" öğesi aranır (menüsüz satır = false). */
async function birlestirDugmeleri(w: W) {
  const sonuc: boolean[] = []
  for (const r of w.findAll('tr.sefer-satiri')) {
    const menu = r.find('button[aria-haspopup="menu"]')
    if (menu.exists()) await menu.trigger('click')
    sonuc.push(r.findAll('[role="menuitem"]').some((b) => b.text() === 'Birleştir'))
  }
  return sonuc
}
const birlestirTetikle = (w: W, i: number) =>
  eylemTetikle(w.findAll('tr.sefer-satiri')[i]!, 'seferini birleştir')

describe('AtamaMasasi: Birleştir', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(liste))
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2099-01-01',
      bit: '2099-01-08',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(seferApi.getir).mockResolvedValue({ id: 1, versiyon: 3 } as Sefer)
    vi.mocked(atamaApi.birlestir).mockResolvedValue({
      sefer: {
        id: 1,
        kalkisGar: 'Ankara',
        varisGar: 'Kars',
        guncelKalkis: GELECEK,
        guncelVaris: '2099-01-01T10:00:00Z',
        km: null,
      } as Sefer,
      silinenSeferIdler: [2],
      yeniKirmizi: [{ atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme'] }],
    })
  })

  it('"Birleştir" yalnız parçalı + PLANLI satırda görünür', async () => {
    const w = await kur()
    expect(await birlestirDugmeleri(w)).toEqual([true, false, false, true])
  })

  it('tıklayınca diyalog açılır (satır seçilmez); vazgeçince kapanır ve masa yenilenmez', async () => {
    const w = await kur()
    await birlestirTetikle(w, 0)
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(true)
    expect(atamaApi.birlestir).toHaveBeenCalledWith(1, { versiyon: 3, onizle: true })
    expect(atamaApi.adaylar).not.toHaveBeenCalled()
    await w
      .findAll('dialog button')
      .find((b) => b.text() === 'Vazgeç')!
      .trigger('click')
    expect(w.find('dialog').exists()).toBe(false)
    expect(atamaApi.gun).toHaveBeenCalledTimes(1)
  })

  it('başarıda diyalog kapanır, yeniKirmizi bildirimi gösterilir ve masa yenilenir', async () => {
    const w = await kur()
    await birlestirTetikle(w, 3)
    await flushPromises()
    await w.get('dialog form').trigger('submit')
    await flushPromises()
    expect(atamaApi.birlestir).toHaveBeenLastCalledWith(4, { versiyon: 3, onizle: false })
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.get('[role="status"]').text()).toContain('T1030')
    expect(atamaApi.gun).toHaveBeenCalledTimes(2)
  })
})
