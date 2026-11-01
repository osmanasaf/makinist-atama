import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { liste: vi.fn().mockResolvedValue([]) },
}))

import type { KullaniciDto } from '@/api/tipler'
import { kullanici } from '@/composables/oturum'
import MakinistListesi from './MakinistListesi.vue'
import makinistRotalari from './routes'

const kisi = (rol: KullaniciDto['rol']): KullaniciDto => ({
  id: 1,
  eposta: 'x',
  adSoyad: 'Ali',
  rol,
  aktif: true,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'makinistler', component: { template: '<div />' } },
      { path: '/makinistler/yeni', name: 'makinist-yeni', component: { template: '<div />' } },
      { path: '/makinistler/yukle', name: 'makinist-yukle', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  const w = mount(MakinistListesi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('Personel listesi: Excel yükleme bağlantısı (F1)', () => {
  beforeEach(() => (kullanici.value = null))
  afterEach(() => (kullanici.value = null))

  it('yönetici görür', async () => {
    kullanici.value = kisi('YONETICI')
    const w = await kur()
    expect(w.get('[data-test=excel-yukle]').attributes('href')).toBe('/makinistler/yukle')
  })

  it('atamacı görmez (yükleme yalnız yönetici); tek tek ekleme kalır', async () => {
    kullanici.value = kisi('ATAMACI')
    const w = await kur()
    expect(w.find('[data-test=excel-yukle]').exists()).toBe(false)
    expect(w.find('a[href="/makinistler/yeni"]').exists()).toBe(true)
  })

  it('rota yalnız yönetici içindir (rol Y), menüde yoktur', () => {
    const r = makinistRotalari.find((x) => x.name === 'makinist-yukle')!
    expect(r.path).toBe('/makinistler/yukle')
    expect(r.meta).toMatchObject({ rol: 'Y' })
    expect(r.meta?.menu).toBeUndefined()
  })
})
