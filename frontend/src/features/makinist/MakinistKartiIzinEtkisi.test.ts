import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))
vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: {
    getir: vi.fn(),
    olustur: vi.fn(),
    guncelle: vi.fn(),
    izinEkle: vi.fn(),
    izinEtkisi: vi.fn(),
  },
}))

import { tanimApi } from '@/features/tanim/tanimApi'
import MakinistKarti from './MakinistKarti.vue'
import { makinistApi, type IzinEtkisiSatiri, type MakinistDetay } from './makinistApi'

const detay: MakinistDetay = {
  id: 5,
  sicil: 'M001',
  adSoyad: 'Ali Veli',
  bagliGarId: 1,
  bagliGar: 'Ankara',
  durum: 'AKTIF',
  ehliyetBitis: '2027-01-01',
  saglikBitis: null,
  psikoteknikBitis: null,
  sonSurus: null,
  versiyon: 3,
  broveAracTipleri: [],
  broveHatlar: [],
  izinler: [],
}

const etki: IzinEtkisiSatiri = {
  atamaId: 11,
  seferId: 21,
  tarih: '2026-10-20',
  trenNo: '1234',
  rol: 'ASIL',
  kalkisGar: 'Ankara',
  varisGar: 'İzmir',
  kalkis: '2026-10-20T05:30:00Z', // 08:30 İstanbul
  varis: '2026-10-20T11:00:00Z', // 14:00 İstanbul
}

async function izinEkle() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/makinistler', name: 'makinistler', component: { template: '<div />' } },
      { path: '/makinistler/:id(\\d+)', name: 'makinist-kart', component: MakinistKarti },
    ],
  })
  await router.push('/makinistler/5')
  await router.isReady()
  const w = mount(MakinistKarti, { global: { plugins: [router] } })
  await flushPromises()
  await w.get('#izin-baslangic').setValue('2026-10-19')
  await w.get('#izin-bitis').setValue('2026-10-22')
  await w.get('#izin-baslangic').element.closest('form')!.dispatchEvent(new Event('submit'))
  await flushPromises()
  return w
}

describe('MakinistKarti izin etkisi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockResolvedValue([])
    vi.mocked(makinistApi.getir).mockResolvedValue(detay)
    vi.mocked(makinistApi.izinEkle).mockResolvedValue({
      id: 9,
      baslangic: '2026-10-19',
      bitis: '2026-10-22',
      tur: 'YILLIK',
      durum: 'TALEP',
      olusturan: 'Ayşe',
    })
  })

  it('izin sonrası çakışan seferler uyarı olarak listelenir', async () => {
    vi.mocked(makinistApi.izinEtkisi).mockResolvedValue([etki])
    const w = await izinEkle()
    expect(makinistApi.izinEtkisi).toHaveBeenCalledWith(5, '2026-10-19', '2026-10-22')
    const blok = w.get('.izin-etkisi')
    expect(blok.text()).toContain(
      'Bu izin aralığında atanmış 1 sefer var — atamaları gözden geçirin',
    )
    expect(blok.text()).toContain('20.10.2026')
    expect(blok.text()).toContain('1234')
    expect(blok.text()).toContain('Ankara → İzmir')
    expect(blok.text()).toContain('08:30–14:00')
    expect(blok.text()).toContain('Asıl')
  })

  it('serbest intikal satırı (atamaId yok) listelenir; iki intikal anahtar çakışması üretmez', async () => {
    const intikal = (id: number, kalkis: string): IzinEtkisiSatiri => ({
      ...etki,
      atamaId: null,
      seferId: -id,
      trenNo: 'İntikal',
      rol: 'YOLCU',
      kalkis,
      varis: '2026-10-20T07:00:00Z',
    })
    vi.mocked(makinistApi.izinEtkisi).mockResolvedValue([
      intikal(1, '2026-10-20T04:00:00Z'),
      intikal(2, '2026-10-20T05:00:00Z'),
    ])
    const w = await izinEkle()

    const satirlar = w.findAll('.izin-etkisi tbody tr')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('İntikal')
    expect(satirlar[0]!.text()).toContain('Yolcu')
  })

  it('çakışan sefer yoksa uyarı bloğu görünmez', async () => {
    vi.mocked(makinistApi.izinEtkisi).mockResolvedValue([])
    const w = await izinEkle()
    expect(makinistApi.izinEtkisi).toHaveBeenCalled()
    expect(w.find('.izin-etkisi').exists()).toBe(false)
  })

  it('etki sorgusu hata verirse sessizce yok sayılır, izin kaydı kalır', async () => {
    vi.mocked(makinistApi.izinEtkisi).mockRejectedValue(new Error('ağ'))
    const w = await izinEkle()
    expect(w.find('.izin-etkisi').exists()).toBe(false)
    expect(w.find('[role=alert]').exists()).toBe(false)
    expect(w.text()).toContain('Talep')
  })
})
