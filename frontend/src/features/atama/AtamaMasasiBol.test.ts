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
    bol: vi.fn(),
  },
}))
vi.mock('@/features/sefer/seferApi', () => ({ seferApi: { getir: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { seferApi, type Sefer } from '@/features/sefer/seferApi'
import { tanimApi } from '@/features/tanim/tanimApi'
import { eylemTetikle, makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const GELECEK = '2099-01-01T05:00:00Z'
const GECMIS = '2020-01-01T05:00:00Z'

const liste: GunListesi = {
  tarih: '2099-01-01',
  uretimZamani: '2099-01-01T00:00:00Z',
  satirlar: [
    satir(1, { guncelKalkis: GELECEK, makinistler: [makinist()] }), // Böl var
    satir(2, { guncelKalkis: GELECEK, parcaNo: 1, parcaSayisi: 2 }), // parçalı: yok
    satir(3, { guncelKalkis: GELECEK, durum: 'IPTAL' }), // iptal: yok
    satir(4, { guncelKalkis: GECMIS }), // kalkışı geçmiş: yok
    satir(5, { guncelKalkis: GELECEK, parcaNo: null, parcaSayisi: null }), // Böl var
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
/** K7: Böl artık ⋯ menüsünde; menü satır satır açılıp "Böl" öğesi aranır (menüsüz satır = false). */
async function bolDugmeleri(w: Awaited<ReturnType<typeof kur>>) {
  const sonuc: boolean[] = []
  for (const r of w.findAll('tr.sefer-satiri')) {
    const menu = r.find('button[aria-haspopup="menu"]')
    if (menu.exists()) await menu.trigger('click')
    sonuc.push(r.findAll('[role="menuitem"]').some((b) => b.text() === 'Böl'))
  }
  return sonuc
}

describe('AtamaMasasi: Böl', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(liste))
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2099-01-01',
      bit: '2099-01-08',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(seferApi.getir).mockResolvedValue({
      id: 1,
      trenNo: 'T1001',
      kalkisGarId: 1,
      varisGarId: 3,
      km: 230,
      versiyon: 2,
    } as Sefer)
    vi.mocked(tanimApi.liste).mockResolvedValue([])
  })

  it('"Böl" yalnız parçasız + PLANLI + kalkışı geçmemiş satırda görünür', async () => {
    const w = await kur()
    expect(await bolDugmeleri(w)).toEqual([true, false, false, false, true])
  })

  it('tıklayınca diyalog açılır (satır seçilmez); vazgeçince kapanır ve masa yenilenmez', async () => {
    const w = await kur()
    await eylemTetikle(w.findAll('tr.sefer-satiri')[0]!, 'seferini böl')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(true)
    expect(seferApi.getir).toHaveBeenCalledWith(1)
    expect(atamaApi.adaylar).not.toHaveBeenCalled()
    await w
      .findAll('dialog button')
      .find((b) => b.text() === 'Vazgeç')!
      .trigger('click')
    expect(w.find('dialog').exists()).toBe(false)
    expect(atamaApi.gun).toHaveBeenCalledTimes(1)
  })

  it('başarıda diyalog kapanır, mesaj gösterilir ve masa yenilenir', async () => {
    vi.mocked(seferApi.getir).mockResolvedValue({
      id: 1,
      trenNo: 'T1001',
      kalkisGarId: 1,
      varisGarId: 3,
      km: null,
      versiyon: 2,
    } as Sefer)
    vi.mocked(tanimApi.liste).mockResolvedValue([{ id: 2, tip: 'GAR', ad: 'Sivas' }])
    vi.mocked(atamaApi.bol).mockResolvedValue({
      parcalar: [],
      yeniKirmizi: [{ atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme'] }],
    })
    const w = await kur()
    await eylemTetikle(w.findAll('tr.sefer-satiri')[0]!, 'seferini böl')
    await flushPromises()
    await w.get('#bol-gar-0').setValue('2')
    await w.get('#bol-varis-0').setValue('13:30')
    await w.get('#bol-kalkis-0').setValue('13:45')
    await w.get('dialog form').trigger('submit') // önizle
    await flushPromises()
    await w.get('dialog form').trigger('submit') // böl
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.get('[role="status"]').text()).toContain('T1030')
    expect(atamaApi.gun).toHaveBeenCalledTimes(2)
  })
})
