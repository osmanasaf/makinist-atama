import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))
vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { getir: vi.fn(), olustur: vi.fn(), guncelle: vi.fn(), izinEkle: vi.fn() },
}))

import { tanimApi } from '@/features/tanim/tanimApi'
import MakinistKarti from './MakinistKarti.vue'
import { makinistApi, type MakinistDetay } from './makinistApi'

const kondukturDetay: MakinistDetay = {
  id: 8,
  sicil: 'K001',
  adSoyad: 'Şef Kişi',
  bagliGarId: 1,
  bagliGar: 'Ankara',
  durum: 'AKTIF',
  tur: 'KONDUKTOR',
  ehliyetBitis: '2027-01-01',
  saglikBitis: '2027-01-01',
  psikoteknikBitis: '2027-01-01',
  sonSurus: null,
  versiyon: 1,
  broveAracTipleri: [],
  broveHatlar: [],
  izinler: [],
}

async function kur(yol: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/makinistler', name: 'makinistler', component: { template: '<div />' } },
      { path: '/makinistler/yeni', name: 'makinist-yeni', component: MakinistKarti },
      { path: '/makinistler/:id(\\d+)', name: 'makinist-kart', component: MakinistKarti },
    ],
  })
  await router.push(yol)
  await router.isReady()
  const w = mount(MakinistKarti, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('MakinistKarti: personel türü (F3b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockImplementation(async (tip) =>
      tip === 'GAR' ? [{ id: 1, tip, ad: 'Ankara' }] : [],
    )
  })

  it('yeni kartta tür seçilebilir; makinist: bröve bölümleri ve "Ehliyet bitişi" var', async () => {
    const w = await kur('/makinistler/yeni')
    const tur = w.get('#tur')
    expect((tur.element as HTMLSelectElement).disabled).toBe(false)
    expect((tur.element as HTMLSelectElement).value).toBe('MAKINIST')
    expect(w.text()).toContain('Yeni personel')
    expect(w.text()).toContain('Araç tipi bröveleri')
    expect(w.text()).toContain('Ehliyet bitişi')
  })

  it('yeni kartta Kondüktör seçilince bröve ve son sürüş gizlenir, belge etiketi değişir, gövdeye tur gider', async () => {
    vi.mocked(makinistApi.olustur).mockResolvedValue(kondukturDetay)
    const w = await kur('/makinistler/yeni')
    await w.get('#tur').setValue('KONDUKTOR')

    expect(w.text()).not.toContain('Araç tipi bröveleri')
    expect(w.text()).not.toContain('Hat bröveleri')
    expect(w.find('#sonSurus').exists()).toBe(false)
    expect(w.text()).toContain('Kişisel emniyet belgesi bitişi')

    await w.get('#sicil').setValue('K001')
    await w.get('#adSoyad').setValue('Şef Kişi')
    await w.get('#bagliGar').setValue('1')
    await w.get('form').trigger('submit')
    await flushPromises()
    const govde = vi.mocked(makinistApi.olustur).mock.calls[0]![0]
    expect(govde.tur).toBe('KONDUKTOR')
    expect(govde.broveAracTipleri).toEqual([])
    expect(govde.broveHatlar).toEqual([])
    expect(govde.sonSurus).toBeNull()
  })

  it('kayıtlı kartta tür değiştirilemez (select kapalı); başlık "Personel: ad", tür rozeti türü söyler', async () => {
    vi.mocked(makinistApi.getir).mockResolvedValue(kondukturDetay)
    const w = await kur('/makinistler/8')
    expect((w.get('#tur').element as HTMLSelectElement).disabled).toBe(true)
    expect((w.get('#tur').element as HTMLSelectElement).value).toBe('KONDUKTOR')
    expect(w.get('h1').text()).toBe('Personel: Şef Kişi')
    expect(w.get('[data-test="personel-turu"]').text()).toBe('Kondüktör')
  })
})

describe('MakinistKarti: izin durumu (H5 erişilebilirlik)', () => {
  it('izin durumu düz metin değil rozet: ton + simge + metin', async () => {
    vi.mocked(tanimApi.liste).mockResolvedValue([])
    vi.mocked(makinistApi.getir).mockResolvedValue({
      ...kondukturDetay,
      izinler: [
        {
          id: 1,
          baslangic: '2026-11-01',
          bitis: '2026-11-03',
          tur: 'YILLIK',
          durum: 'ONAY',
          olusturan: 'a',
        },
        {
          id: 2,
          baslangic: '2026-12-01',
          bitis: '2026-12-03',
          tur: 'RAPOR',
          durum: 'RED',
          olusturan: 'a',
        },
      ],
    })
    const w = await kur('/makinistler/8')
    const rozetler = w
      .findAll('table .rozet')
      .filter((r) => ['Onaylı', 'Reddedildi'].includes(r.text()))
    expect(rozetler.map((r) => [r.text(), r.classes().find((c) => c !== 'rozet')])).toEqual([
      ['Onaylı', 'rozet-basari'],
      ['Reddedildi', 'rozet-engel'],
    ])
    expect(rozetler.map((r) => r.get('svg').attributes('data-simge'))).toEqual(['basari', 'iptal'])
  })
})
