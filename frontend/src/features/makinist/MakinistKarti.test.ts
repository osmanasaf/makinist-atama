import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))
vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { getir: vi.fn(), olustur: vi.fn(), guncelle: vi.fn(), izinEkle: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import { tanimApi } from '@/features/tanim/tanimApi'
import MakinistKarti from './MakinistKarti.vue'
import { makinistApi, type MakinistDetay } from './makinistApi'

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
  broveAracTipleri: [
    { aracTipiId: 7, aracTipi: 'E68000', sinavTarihi: '2022-03-10', gecerlilikBitis: '2027-03-10' },
  ],
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

describe('MakinistKarti', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockImplementation(async (tip) =>
      tip === 'GAR'
        ? [{ id: 1, tip, ad: 'Ankara' }]
        : tip === 'ARAC_TIPI'
          ? [{ id: 7, tip, ad: 'E68000' }]
          : [],
    )
    vi.mocked(makinistApi.getir).mockResolvedValue(detay)
  })

  it('bröve geçerlilik bitişini gg.aa.yyyy gösterir', async () => {
    const w = await kur('/makinistler/5')
    expect(w.text()).toContain('Geçerlilik bitişi: 10.03.2027')
    expect((w.get('#sicil').element as HTMLInputElement).value).toBe('M001')
  })

  it('sicil boşken kaydet istek göndermez ve hata gösterir', async () => {
    const w = await kur('/makinistler/yeni')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Sicil zorunludur.')
    expect(makinistApi.olustur).not.toHaveBeenCalled()
  })

  it('SICIL_TEKRARI sicil alanında gösterilir', async () => {
    vi.mocked(makinistApi.olustur).mockRejectedValue(
      new ApiHata(409, 'SICIL_TEKRARI', 'Bu sicil zaten kayıtlı.'),
    )
    const w = await kur('/makinistler/yeni')
    await w.get('#sicil').setValue('M001')
    await w.get('#adSoyad').setValue('Ali Veli')
    await w.get('#bagliGar').setValue('1')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(makinistApi.olustur).toHaveBeenCalled()
    expect(w.get('.alan-hata').text()).toBe('Bu sicil zaten kayıtlı.')
  })

  it('VERSIYON_CAKISMASI üst mesaj ve yeniden yükle sunar', async () => {
    vi.mocked(makinistApi.guncelle).mockRejectedValue(
      new ApiHata(409, 'VERSIYON_CAKISMASI', 'Kayıt başkası tarafından değiştirildi.'),
    )
    const w = await kur('/makinistler/5')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('başkası tarafından değiştirildi')
    expect(w.get('[role=alert] button').text()).toBe('Yeniden yükle')
  })
})
