import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { kullanici, yuklendi } from '@/composables/oturum'
import App from '@/App.vue'
import { yonlendiriciKur } from '@/router'
import { rozetYaz } from './rozet'

const bos = { template: '<div>sayfa</div>' }
const routelar = [
  { path: '/giris', name: 'giris', component: bos, meta: { acik: true } },
  { path: '/', name: 'ana', component: bos, meta: { menu: { ad: 'Ana Sayfa', sira: 10 } } },
  { path: '/benim/gorevler', name: 'benim-gorevler', component: bos, meta: { rol: 'M' as const } },
  { path: '/benim/parola', name: 'benim-parola', component: bos, meta: { rol: 'M' as const } },
]
const kisi = (rol: KullaniciDto['rol'], bayrak = false): KullaniciDto => ({
  id: 1,
  eposta: 'x',
  adSoyad: 'Ali Veli',
  rol,
  aktif: true,
  makinistId: rol === 'MAKINIST' ? 5 : null,
  parolaDegistirmeli: bayrak,
})

async function kur(rol: KullaniciDto['rol'], yol: string, bayrak = false) {
  vi.mocked(api.get).mockResolvedValue(kisi(rol, bayrak))
  const router = yonlendiriciKur(routelar, createMemoryHistory())
  await router.push(yol)
  const w = mount(App, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('BenimKabuk (makinist telefon kabuğu)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })
  afterEach(() => rozetYaz(''))

  it('makinistte masaüstü menüsü yok; dört alt sekme, ad ve çıkış düğmesi var', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler')
    expect(w.find('.ust-cubuk').exists()).toBe(false)
    expect(w.find('nav[aria-label="Ana menü"]').exists()).toBe(false)
    const sekmeler = w.findAll('nav[aria-label="Personel menüsü"] a').map((a) =>
      a
        .text()
        .replace(/^\S+\s/, '')
        .trim(),
    )
    expect(sekmeler).toEqual(['Görevler', 'Belgeler', 'İzin', 'Özet'])
    expect(w.get('.benim-ad').text()).toBe('Ali Veli')
    expect(w.findAll('button').map((b) => b.text())).toContain('Çıkış')
  })

  it('atamacıda kabuk yok, masaüstü üst çubuğu var', async () => {
    const w = await kur('ATAMACI', '/')
    expect(w.find('.benim').exists()).toBe(false)
    expect(w.find('.ust-cubuk').exists()).toBe(true)
  })

  it('aria-live="polite" rozet bölgesi var ve sayfanın yazdığı metni taşır', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler')
    const bolge = w.get('[aria-live="polite"]')
    expect(bolge.text()).toBe('')
    rozetYaz('3 görev listelendi')
    await flushPromises()
    expect(bolge.text()).toBe('3 görev listelendi')
  })

  it('geçici parola bayrağı açıkken sekmeler çizilmez (yalnız parola ekranı)', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler', true)
    expect(w.find('nav[aria-label="Personel menüsü"]').exists()).toBe(false)
  })
})
