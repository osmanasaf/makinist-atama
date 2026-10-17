import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/makinist/makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/makinist/makinistApi')>()),
  makinistApi: { liste: vi.fn() },
}))

import { ROL_ETIKETLERI } from '@/api/tipler'
import { ROL_ETIKETLERI as ATAMA_ROL_ETIKETLERI } from '@/features/atama/atamaApi'
import MakinistListesi from '@/features/makinist/MakinistListesi.vue'
import { makinistApi } from '@/features/makinist/makinistApi'
import makinistRotalari from '@/features/makinist/routes'

/**
 * F3b MK-91: ortak ekranlar "Personel" der (yalnız GÖRÜNÜR metin); rota yolları, rota adları ve API yolları değişmez; rol-özgü yerler (Asıl/Yardımcı, bröve, yedek planı,
 * kadro tahmini) "Makinist" kalır. Envanter 04-gelistirme.md'dedir; burada seçili başlıklar doğrulanır.
 */
describe('etiket geçişi: Makinist -> Personel (görünür metin)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(makinistApi.liste).mockResolvedValue([])
  })

  it('menü ve sayfa başlıkları Personel; rota yolları ve adları aynı', () => {
    const liste = makinistRotalari.find((r) => r.name === 'makinistler')!
    expect(liste.path).toBe('/makinistler')
    expect((liste.meta as { menu: { ad: string } }).menu.ad).toBe('Personel')
    expect(
      (makinistRotalari.find((r) => r.name === 'makinist-yeni')!.meta as { baslik: string }).baslik,
    ).toBe('Yeni personel')
    expect(
      (makinistRotalari.find((r) => r.name === 'makinist-kart')!.meta as { baslik: string }).baslik,
    ).toBe('Personel kartı')
    expect(makinistRotalari.find((r) => r.name === 'makinist-kart')!.path).toBe(
      '/makinistler/:id([0-9]+)',
    )
  })

  it('liste başlığı, yeni düğmesi ve tür süzgeci', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/makinistler/yeni', name: 'makinist-yeni', component: { template: '<div />' } },
        { path: '/makinistler/:id', name: 'makinist-kart', component: { template: '<div />' } },
      ],
    })
    const w = mount(MakinistListesi, { global: { plugins: [router] } })
    await flushPromises()
    expect(w.get('h1').text()).toBe('Personel')
    expect(w.text()).toContain('Yeni personel')
    expect(w.findAll('#tur option').map((o) => o.text())).toEqual(['Tümü', 'Makinist', 'Kondüktör'])
  })

  it('kullanıcı rol etiketi Personel; atama rolleri Asıl/Yardımcı/Kondüktör/Yolcu kalır', () => {
    expect(ROL_ETIKETLERI.MAKINIST).toBe('Personel')
    expect(ATAMA_ROL_ETIKETLERI).toEqual({
      ASIL: 'Asıl',
      YARDIMCI: 'Yardımcı',
      KONDUKTOR: 'Kondüktör',
      YOLCU: 'Yolcu',
    })
  })
})
