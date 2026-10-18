import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { liste: vi.fn() },
}))

import MakinistListesi from './MakinistListesi.vue'
import { makinistApi, type MakinistOzet } from './makinistApi'

const ozet = (ek: Partial<MakinistOzet> = {}): MakinistOzet => ({
  id: 1,
  sicil: 'M001',
  adSoyad: 'Ali Veli',
  bagliGarId: 1,
  bagliGar: 'Ankara',
  durum: 'AKTIF',
  tur: 'MAKINIST',
  ehliyetBitis: '2027-01-01',
  saglikBitis: null,
  psikoteknikBitis: null,
  ...ek,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'makinist-yeni', component: { template: '<div />' } },
      { path: '/m/:id', name: 'makinist-kart', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  const w = mount(MakinistListesi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('MakinistListesi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(makinistApi.liste).mockResolvedValue([ozet(), ozet({ id: 2, sicil: 'M002' })])
  })

  it('tek h1 "Personel", "Yeni personel" başlık eylemi; sicil kart bağlantısı', async () => {
    const w = await kur()
    expect(w.findAll('h1')).toHaveLength(1)
    expect(w.get('h1').text()).toBe('Personel')
    expect(w.get('.sayfa-baslik-eylemler').text()).toBe('Yeni personel')
    expect(w.findAll('tbody tr')).toHaveLength(2)
    expect(w.get('tbody a').attributes('href')).toBe('/m/1')
  })

  it('yüklenirken "Yükleniyor…", kayıt yoksa boş durum, hata varsa hata durumu ve yeniden dene', async () => {
    let bitir!: (v: MakinistOzet[]) => void
    vi.mocked(makinistApi.liste).mockReturnValue(new Promise((c) => (bitir = c)))
    const w = await kur()
    expect(w.get('[role="status"]').text()).toContain('Yükleniyor…')
    bitir([])
    await flushPromises()
    expect(w.text()).toContain('Kayıt bulunamadı.')

    vi.mocked(makinistApi.liste).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası.'),
    )
    const h = await kur()
    expect(h.get('[role="alert"]').text()).toContain('Sunucu hatası.')
    expect(h.text()).not.toContain('Kayıt bulunamadı.')
    vi.mocked(makinistApi.liste).mockResolvedValue([ozet()])
    await h
      .findAll('button')
      .find((b) => b.text() === 'Yeniden dene')!
      .trigger('click')
    await flushPromises()
    expect(h.findAll('tbody tr')).toHaveLength(1)
  })
})
