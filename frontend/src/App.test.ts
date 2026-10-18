import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createMemoryHistory, type RouteRecordRaw } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { CIKIS_SORUSU, useCikisKorumasi } from '@/composables/cikis-korumasi'
import { kullanici, yuklendi } from '@/composables/oturum'
import { yonlendiriciKur } from '@/router'
import parametreRotalari from '@/features/parametre/routes'
import App from './App.vue'

const bos = { template: '<div>sayfa</div>' }
const routelar = [
  { path: '/giris', name: 'giris', component: bos, meta: { acik: true } },
  { path: '/', name: 'ana', component: bos, meta: { menu: { ad: 'Ana Sayfa', sira: 10 } } },
  {
    path: '/kullanicilar',
    name: 'kullanicilar',
    component: bos,
    meta: { rol: 'Y' as const, menu: { ad: 'Kullanıcılar', sira: 90 } },
  },
]
const kisi = (rol: KullaniciDto['rol']): KullaniciDto => ({
  id: 1,
  eposta: 'a@b.c',
  adSoyad: 'Ayşe Kaya',
  rol,
  aktif: true,
})

async function kur(rol: KullaniciDto['rol'], rotalar: RouteRecordRaw[] = routelar) {
  vi.mocked(api.get).mockResolvedValue(kisi(rol))
  const router = yonlendiriciKur(rotalar, createMemoryHistory())
  await router.push('/')
  const w = mount(App, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  acik.push(w)
  return { w, router }
}

const acik: VueWrapper[] = []
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  vi.restoreAllMocks()
})

/** Kaydedilmemiş verisi olan sahte sayfa bileşeni (çıkış korumasına kayıtlı). */
function korumaliBilesen(kirli: () => boolean, kaydet?: () => Promise<void>) {
  const w = mount(
    defineComponent({
      setup() {
        useCikisKorumasi(kirli, kaydet)
        return () => h('div')
      },
    }),
  )
  acik.push(w)
}

describe('App düzeni', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })

  it('yönetici üst çubukta kullanıcı adı, rolü ve Kullanıcılar menüsünü görür', async () => {
    const { w } = await kur('YONETICI')
    expect(w.get('.kullanici-bilgisi').text()).toBe('Ayşe Kaya (Yönetici)')
    expect(w.findAll('nav a').map((a) => a.text())).toEqual(['Ana Sayfa', 'Kullanıcılar'])
  })

  it('gerçek parametre rotalarıyla yönetici menüde Ayarlar görür, Parametreler kalmamıştır', async () => {
    const { w } = await kur('YONETICI', [...routelar, ...parametreRotalari])
    const adlar = w.findAll('nav a').map((a) => a.text())
    expect(adlar).toContain('Ayarlar')
    expect(adlar).not.toContain('Parametreler')
  })

  it('atamacı menüde Ayarlar görmez', async () => {
    const { w } = await kur('ATAMACI', [...routelar, ...parametreRotalari])
    expect(w.findAll('nav a').map((a) => a.text())).not.toContain('Ayarlar')
  })

  it('atamacı Kullanıcılar menüsünü görmez', async () => {
    const { w } = await kur('ATAMACI')
    expect(w.findAll('nav a').map((a) => a.text())).toEqual(['Ana Sayfa'])
  })

  it('kabuk: içeriğe geç bağlantısı, odaklanabilir main#icerik, tek "Ana menü" gezinti bölgesi', async () => {
    const { w } = await kur('YONETICI')
    const atla = w.get('a.atla-baglanti')
    expect(atla.text()).toBe('İçeriğe geç')
    expect(w.findAll('nav[aria-label="Ana menü"]')).toHaveLength(1)
    const main = w.get('main#icerik')
    expect(main.attributes('tabindex')).toBe('-1')
    expect(w.findAll('main')).toHaveLength(1)
    await atla.trigger('click')
    expect(document.activeElement).toBe(main.element)
  })

  it('kabuk: tam genişlik rota (meta.tam) sayfa--tam sınıfı alır', async () => {
    const { w, router } = await kur('ATAMACI', [
      ...routelar,
      { path: '/genis', name: 'genis', component: bos, meta: { tam: true } },
    ])
    expect(w.get('main').classes()).not.toContain('sayfa--tam')
    await router.push('/genis')
    await flushPromises()
    expect(w.get('main').classes()).toContain('sayfa--tam')
  })

  it('yazdırma sayfasında kabuk (üst çubuk, menü, atla bağlantısı) çizilmez', async () => {
    const { w, router } = await kur('ATAMACI', [
      ...routelar,
      { path: '/yazdir', name: 'yz', component: bos, meta: { yazdir: true } },
    ])
    await router.push('/yazdir')
    await flushPromises()
    expect(w.find('header').exists()).toBe(false)
    expect(w.find('nav').exists()).toBe(false)
    expect(w.find('a.atla-baglanti').exists()).toBe(false)
    expect(w.get('main').classes()).not.toContain('sayfa')
  })

  it('oturum yokken üst çubuk çizilmez', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('401'))
    const router = yonlendiriciKur(routelar, createMemoryHistory())
    await router.push('/')
    const w = mount(App, { global: { plugins: [router] } })
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('giris')
    expect(w.find('header').exists()).toBe(false)
  })

  it('Çıkış oturumu kapatır ve giriş sayfasına götürür', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined)
    const { w, router } = await kur('ATAMACI')
    await w.get('header button').trigger('click')
    await flushPromises()
    expect(api.post).toHaveBeenCalledWith('/api/auth/cikis')
    expect(kullanici.value).toBeNull()
    expect(router.currentRoute.value.name).toBe('giris')
  })

  // Çıkış, kaydedilmemiş veri korumasından geçer; oturum ondan SONRA kapanır.
  describe('Çıkış, çıkış korumasına takılır', () => {
    const cikisTikla = async (w: VueWrapper) => {
      await w.get('header button').trigger('click')
      await flushPromises()
    }

    it('kirli form + Çıkış: onay sorulur; Vazgeç oturumu KAPATMAZ ve sayfada bırakır', async () => {
      const onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
      vi.mocked(api.post).mockResolvedValue(undefined)
      const { w, router } = await kur('ATAMACI')
      korumaliBilesen(() => true)
      await cikisTikla(w)
      expect(onay).toHaveBeenCalledWith(CIKIS_SORUSU)
      expect(api.post).not.toHaveBeenCalled()
      expect(kullanici.value).not.toBeNull()
      expect(router.currentRoute.value.name).toBe('ana')
    })

    it('kirli form + Çıkış + onay: oturum kapanır, giriş sayfasına gidilir', async () => {
      const onay = vi.spyOn(window, 'confirm').mockReturnValue(true)
      vi.mocked(api.post).mockResolvedValue(undefined)
      const { w, router } = await kur('ATAMACI')
      korumaliBilesen(() => true)
      await cikisTikla(w)
      expect(onay).toHaveBeenCalledTimes(1)
      expect(api.post).toHaveBeenCalledWith('/api/auth/cikis')
      expect(kullanici.value).toBeNull()
      expect(router.currentRoute.value.name).toBe('giris')
    })

    it('bekleyen düzenleme önce KAYDEDİLİR, sonra oturum kapanır (soru yok, kayıt oturum açıkken gider)', async () => {
      const onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
      vi.mocked(api.post).mockResolvedValue(undefined)
      const { w, router } = await kur('ATAMACI')
      const kirli = ref(true)
      const kaydet = vi.fn(async () => {
        expect(api.post).not.toHaveBeenCalled() // kayıt anında oturum hâlâ açık
        kirli.value = false
      })
      korumaliBilesen(() => kirli.value, kaydet)
      await cikisTikla(w)
      expect(kaydet).toHaveBeenCalledTimes(1)
      expect(onay).not.toHaveBeenCalled()
      expect(api.post).toHaveBeenCalledWith('/api/auth/cikis')
      expect(router.currentRoute.value.name).toBe('giris')
    })

    it('kaydedilemeyen veri varsa (kayıt kirliyi temizlemedi) sorulur', async () => {
      const onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
      vi.mocked(api.post).mockResolvedValue(undefined)
      const { w } = await kur('ATAMACI')
      korumaliBilesen(
        () => true,
        async () => {},
      )
      await cikisTikla(w)
      expect(onay).toHaveBeenCalledTimes(1)
      expect(api.post).not.toHaveBeenCalled()
    })

    it('sunucu çıkış isteği başarısız olsa da yerel oturum kapanır ve giriş sayfasına gidilir', async () => {
      vi.mocked(api.post).mockRejectedValue(new Error('ağ'))
      const { w, router } = await kur('ATAMACI')
      await cikisTikla(w)
      expect(kullanici.value).toBeNull()
      expect(router.currentRoute.value.name).toBe('giris')
    })
  })
})
