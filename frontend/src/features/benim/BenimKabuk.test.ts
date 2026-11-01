import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { kullanici, yuklendi } from '@/composables/oturum'
import App from '@/App.vue'
import { yonlendiriciKur } from '@/router'
import { onayBekleyen, rozetYaz } from './rozet'

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
  afterEach(() => {
    rozetYaz('')
    onayBekleyen.value = 0
  })

  it('makinistte masaüstü menüsü yok; dört alt sekme, ad ve çıkış düğmesi var', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler')
    expect(w.find('.ust-cubuk').exists()).toBe(false)
    expect(w.find('nav[aria-label="Ana menü"]').exists()).toBe(false)
    const sekmeler = w.findAll('nav[aria-label="Personel menüsü"] a').map((a) => a.text())
    expect(sekmeler).toEqual(['Görevler', 'Belgeler', 'İzin', 'Özet'])
    const simgeler = w
      .findAll('nav[aria-label="Personel menüsü"] a svg[data-simge]')
      .map((s) => s.attributes('data-simge'))
    expect(simgeler).toEqual(['tren', 'belge', 'izin', 'rapor'])
    expect(w.get('.benim-ad').text()).toBe('Ali Veli')
    expect(w.findAll('button').map((b) => b.text())).toContain('Çıkış')
  })

  it('AK-7.5: onay bekleyen varsa Görevler sekmesinde metinli rozet ve erişilebilir ad; yoksa rozet yok', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler')
    const gorevler = () => w.get('nav[aria-label="Personel menüsü"] a')
    expect(gorevler().attributes('aria-label')).toBeUndefined()
    expect(gorevler().find('.rozet').exists()).toBe(false)
    onayBekleyen.value = 2
    await flushPromises()
    expect(gorevler().attributes('aria-label')).toBe('Görevler, 2 onay bekliyor')
    expect(gorevler().get('.rozet').text()).toBe('2')
    expect(gorevler().text()).toContain('Görevler')
  })

  it('Çıkış ikincil (hayalet) tonda ve çıkınca onay sayısı sıfırlanır (bayat rozet sonraki kullanıcıya geçmez)', async () => {
    const w = await kur('MAKINIST', '/benim/gorevler')
    const cikis = w.findAll('button').find((b) => b.text() === 'Çıkış')!
    expect(cikis.classes()).toContain('btn-hayalet')
    onayBekleyen.value = 3
    vi.mocked(api.post).mockResolvedValue(undefined)
    await cikis.trigger('click')
    await flushPromises()
    expect(onayBekleyen.value).toBe(0)
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
