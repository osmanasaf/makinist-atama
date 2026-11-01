import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { kurumGuncelle, kurumSifirla } from '@/composables/kurum'
import { kullanici, yuklendi } from '@/composables/oturum'
import App from '@/App.vue'
import GirisSayfasi from '@/features/giris/GirisSayfasi.vue'
import YazdirSayfasi from '@/features/yazdir/YazdirSayfasi.vue'
import { yonlendiriciKur } from '@/router'

/** F1 / AK-1.8: kurum adı ve logosu üst çubukta, giriş sayfasında, makinist kabuğunda ve yazdırma başlığında; girilmemişse ürün adı. */

const AD = 'Örnek İşletme (demo)'
const kisi = (rol: KullaniciDto['rol']): KullaniciDto => ({
  id: 1,
  eposta: 'x',
  adSoyad: 'Ali Veli',
  rol,
  aktif: true,
  makinistId: rol === 'MAKINIST' ? 5 : null,
  parolaDegistirmeli: false,
})
const bos = { template: '<div>sayfa</div>' }

async function uygulama(rol: KullaniciDto['rol']) {
  vi.mocked(api.get).mockResolvedValue(kisi(rol))
  const router = yonlendiriciKur(
    [
      { path: '/giris', name: 'giris', component: bos, meta: { acik: true } },
      { path: '/', name: 'ana', component: bos, meta: { menu: { ad: 'Ana Sayfa', sira: 10 } } },
      { path: '/benim', name: 'benim', component: bos, meta: { rol: 'M' as const } },
      { path: '/benim/gorevler', component: bos, meta: { rol: 'M' as const } },
    ],
    createMemoryHistory(),
  )
  await router.push(rol === 'MAKINIST' ? '/benim/gorevler' : '/')
  const w = mount(App, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

async function girisSayfasi() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: bos }],
  })
  return mount(GirisSayfasi, { global: { plugins: [router] } })
}

function yazdirSayfasi() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: bos }],
  })
  return mount(YazdirSayfasi, {
    props: {
      baslik: 'Gün listesi',
      filtre: 'Tarih: 12.10.2026',
      uretimZamani: null,
      kayitSayisi: 3,
      geri: '/',
    },
    global: { plugins: [router] },
  })
}

describe('kurum adı ve logosu kabukta (AK-1.8)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
    kurumSifirla()
  })
  afterEach(() => kurumSifirla())

  describe('girilmemişken ürün adı görünür, logo yok', () => {
    it('üst çubuk', async () => {
      const w = await uygulama('YONETICI')
      expect(w.get('.marka').text()).toBe('Makinist Atama')
      expect(w.find('.marka img').exists()).toBe(false)
      expect(w.find('.marka-isaret').exists()).toBe(true)
    })

    it('giriş sayfası', async () => {
      const w = await girisSayfasi()
      expect(w.get('h1').text()).toBe('Makinist Atama')
      expect(w.find('img').exists()).toBe(false)
    })

    it('makinist kabuğu', async () => {
      const w = await uygulama('MAKINIST')
      expect(w.get('.benim-marka').text()).toBe('Makinist Atama')
      expect(w.find('.benim-marka img').exists()).toBe(false)
    })

    it('yazdırma başlığı: kurum satırı hiç çizilmez (boş kurulum çıktısı değişmez)', () => {
      const w = yazdirSayfasi()
      expect(w.find('[data-test=yazdir-kurum]').exists()).toBe(false)
      expect(w.get('h1').text()).toBe('Gün listesi')
    })
  })

  describe('kurum adı girilmişken', () => {
    beforeEach(() => kurumGuncelle({ ad: AD, logoVar: false, logoSurum: 1 }))

    it('üst çubukta ad görünür, ürün adı görünmez', async () => {
      const w = await uygulama('YONETICI')
      expect(w.get('.marka').text()).toBe(AD)
      expect(w.text()).not.toContain('Makinist Atama')
    })

    it('giriş sayfasının başlığı kurum adıdır (tek h1)', async () => {
      const w = await girisSayfasi()
      expect(w.findAll('h1')).toHaveLength(1)
      expect(w.get('h1').text()).toBe(AD)
    })

    it('makinist kabuğunda ad görünür', async () => {
      const w = await uygulama('MAKINIST')
      expect(w.get('.benim-marka').text()).toBe(AD)
    })

    it('yazdırma başlığında ad h1 üstünde yer alır', () => {
      const w = yazdirSayfasi()
      expect(w.get('[data-test=yazdir-kurum]').text()).toBe(AD)
      const metin = w.text()
      expect(metin.indexOf(AD)).toBeLessThan(metin.indexOf('Gün listesi'))
    })
  })

  describe('logo varken', () => {
    beforeEach(() => kurumGuncelle({ ad: AD, logoVar: true, logoSurum: 7 }))

    it('üst çubuk: logo süs görseli (alt=""; ad yanında yazılı, çift okunmaz), tren simgesi çıkar', async () => {
      const w = await uygulama('YONETICI')
      const img = w.get('.marka img')
      expect(img.attributes('src')).toBe('/api/kurum/logo?s=7')
      expect(img.attributes('alt')).toBe('') // OK-50 (D-9): ad zaten metin olarak yanında
      expect(w.get('.marka').text()).toBe(AD)
      expect(w.find('.marka-isaret').exists()).toBe(false)
    })

    it('giriş sayfası', async () => {
      const w = await girisSayfasi()
      const img = w.get('.giris-marka img')
      expect(img.attributes('src')).toBe('/api/kurum/logo?s=7')
      expect(img.attributes('alt')).toBe('')
      expect(w.get('.giris-marka h1').text()).toBe(AD)
    })

    it('makinist kabuğu', async () => {
      const w = await uygulama('MAKINIST')
      const img = w.get('.benim-marka img')
      expect(img.attributes('src')).toBe('/api/kurum/logo?s=7')
      expect(img.attributes('alt')).toBe('')
      expect(w.get('.benim-marka').text()).toBe(AD)
    })

    it('yazdırma başlığı: ad yokken bile logo tek başına satır açar', () => {
      kurumGuncelle({ ad: null, logoVar: true, logoSurum: 7 })
      const w = yazdirSayfasi()
      expect(w.get('[data-test=yazdir-kurum] img').attributes('alt')).toBe('')
      expect(w.get('[data-test=yazdir-kurum]').text()).toBe('Makinist Atama')
    })
  })
})
