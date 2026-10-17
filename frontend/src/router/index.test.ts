import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { createMemoryHistory, type RouteRecordRaw } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { useCikisKorumasi } from '@/composables/cikis-korumasi'
import { kullanici, yuklendi } from '@/composables/oturum'
import { oturumDustu, yonlendiriciKur } from './index'

const bos = { template: '<div />' }
const routelar: RouteRecordRaw[] = [
  { path: '/giris', name: 'giris', component: bos, meta: { acik: true } },
  { path: '/', name: 'ana', component: bos },
  { path: '/kullanicilar', name: 'kullanicilar', component: bos, meta: { rol: 'Y' } },
  { path: '/spike', name: 'spike', component: bos, meta: { acik: true } },
  { path: '/yeni', name: 'yeni', component: bos, meta: { baslik: 'Yeni makinist' } },
  { path: '/detay/:id', name: 'detay', component: bos },
]
const kisi = (rol: 'YONETICI' | 'ATAMACI'): KullaniciDto => ({
  id: 1,
  eposta: 'a@b.c',
  adSoyad: 'Ad Soyad',
  rol,
  aktif: true,
})

async function git(yol: string) {
  const router = yonlendiriciKur(routelar, createMemoryHistory())
  await router.push(yol)
  return router.currentRoute.value
}

describe('router koruması', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })

  it('oturum yoksa korumalı sayfadan giriş sayfasına yönlendirir ve dönüş adresini saklar', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('401'))
    const r = await git('/kullanicilar')
    expect(r.name).toBe('giris')
    expect(r.query.git).toBe('/kullanicilar')
  })

  it('oturum varsa ana sayfa açılır; giriş sayfasına gidilirse ana sayfaya döner', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
    expect((await git('/')).name).toBe('ana')
    expect((await git('/giris')).name).toBe('ana')
  })

  it('atamacı yönetici sayfasına giremez, ana sayfaya döner', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
    expect((await git('/kullanicilar')).name).toBe('ana')
  })

  it('yönetici yönetici sayfasına girer', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('YONETICI'))
    expect((await git('/kullanicilar')).name).toBe('kullanicilar')
  })

  it('açık route oturum sorgulamadan geçer (backend gerekmez)', async () => {
    expect((await git('/spike')).name).toBe('spike')
    expect(api.get).not.toHaveBeenCalled()
  })

  it('kaydedilmemiş veri varken gezinme onay ister; vazgeçilirse sayfada kalınır, onaylanırsa gidilir', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
    const router = yonlendiriciKur(routelar, createMemoryHistory())
    await router.push('/yeni')
    const onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const w = mount(
      defineComponent({
        setup() {
          useCikisKorumasi(() => kirli.value)
          return () => h('div')
        },
      }),
    )
    const kirli = ref(true)
    await router.push('/')
    expect(onay).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('yeni')
    onay.mockReturnValue(true)
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('ana')
    w.unmount()
    onay.mockRestore()
  })

  it('bilinmeyen adres ana sayfaya yönlenir', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
    expect((await git('/yok/boyle/bir/yer')).name).toBe('ana')
  })

  describe('sayfa başlığı (<title>)', () => {
    it('route başlığı / menü adı belgeye yazılır; yoksa uygulama adı', async () => {
      vi.mocked(api.get).mockResolvedValue(kisi('YONETICI'))
      const router = yonlendiriciKur(
        [
          ...routelar,
          {
            path: '/menu',
            name: 'menu',
            component: bos,
            meta: { menu: { ad: 'Kullanıcılar', sira: 1 } },
          },
        ],
        createMemoryHistory(),
      )
      await router.push('/yeni')
      expect(document.title).toBe('Yeni makinist · Makinist Atama')
      await router.push('/menu')
      expect(document.title).toBe('Kullanıcılar · Makinist Atama')
      await router.push('/')
      expect(document.title).toBe('Makinist Atama')
    })

    it('aynı sayfada yalnız sorgu değişince (sekme geçişi) başlık sıfırlanmaz: sayfanın kendi başlığı korunur', async () => {
      vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
      const router = yonlendiriciKur(routelar, createMemoryHistory())
      await router.push('/detay/5')
      document.title = 'Demo Makinist 01 · Makinist Atama' // sayfa (MakinistDetay) yüklenince kendi başlığını yazar
      await router.replace({ path: '/detay/5', query: { sekme: 'ozet' } })
      expect(document.title).toBe('Demo Makinist 01 · Makinist Atama')
      await router.push('/detay/6') // başka makinist: başlık sıfırlanır, yeni sayfa yazar
      expect(document.title).toBe('Makinist Atama')
    })
  })

  describe('401 (oturum düştü) işleyicisi', () => {
    it('giriş sayfasına dönüş adresiyle ve "oturum sona erdi" işaretiyle yönlendirir', async () => {
      vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
      const router = yonlendiriciKur(routelar, createMemoryHistory())
      await router.push('/detay/5?sekme=ozet')
      kullanici.value = null // main.ts: oturumuTemizle() sonra oturumDustu()
      await oturumDustu(router)
      const r = router.currentRoute.value
      expect(r.name).toBe('giris')
      expect(r.query.git).toBe('/detay/5?sekme=ozet')
      expect(r.query.oturum).toBe('bitti')
    })

    it('giriş sayfasındayken yeniden çağrılınca dönüş adresi İÇ İÇE geçmez, hiçbir şey yapılmaz', async () => {
      vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
      const router = yonlendiriciKur(routelar, createMemoryHistory())
      await router.push('/detay/5?sekme=ozet')
      kullanici.value = null
      await oturumDustu(router)
      const once = router.currentRoute.value.fullPath
      await oturumDustu(router) // ikinci 401 (odak yenilemesinin ikinci GET'i, sökülürken giden PATCH)
      await oturumDustu(router)
      expect(router.currentRoute.value.fullPath).toBe(once)
      expect(router.currentRoute.value.query.git).toBe('/detay/5?sekme=ozet')
    })
  })
})
