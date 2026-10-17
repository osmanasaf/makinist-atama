import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, type RouteRecordRaw } from 'vue-router'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { kullanici, yuklendi } from '@/composables/oturum'
import { yonlendiriciKur } from './index'

const bos = { template: '<div />' }
const routelar: RouteRecordRaw[] = [
  { path: '/giris', name: 'giris', component: bos, meta: { acik: true } },
  { path: '/', name: 'ana', component: bos },
  { path: '/makinist', name: 'makinistler', component: bos },
  { path: '/benim', redirect: '/benim/gorevler' },
  { path: '/benim/gorevler', name: 'benim-gorevler', component: bos, meta: { rol: 'M' } },
  { path: '/benim/parola', name: 'benim-parola', component: bos, meta: { rol: 'M' } },
]
const kisi = (rol: KullaniciDto['rol'], bayrak = false): KullaniciDto => ({
  id: 1,
  eposta: rol === 'MAKINIST' ? 'd001' : 'a@b.c',
  adSoyad: 'Ad Soyad',
  rol,
  aktif: true,
  makinistId: rol === 'MAKINIST' ? 7 : null,
  parolaDegistirmeli: bayrak,
})

async function git(yol: string) {
  const router = yonlendiriciKur(routelar, createMemoryHistory())
  await router.push(yol)
  return router.currentRoute.value
}

describe('makinist rota koruması (yalnız UX; gerçek koruma sunucuda)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })

  it('makinist yalnız M rotalarına girer; masaüstü sayfaları ve ana sayfa /benim e döner', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('MAKINIST'))
    expect((await git('/benim/gorevler')).name).toBe('benim-gorevler')
    expect((await git('/makinist')).name).toBe('benim-gorevler')
    expect((await git('/')).name).toBe('benim-gorevler')
  })

  it('giriş yapmış makinist giriş sayfasına gidince kendi ekranına döner', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('MAKINIST'))
    expect((await git('/giris')).name).toBe('benim-gorevler')
  })

  it('geçici parola bayrağı açıkken yalnız parola ekranı açılır', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('MAKINIST', true))
    expect((await git('/benim/gorevler')).name).toBe('benim-parola')
    expect((await git('/makinist')).name).toBe('benim-parola')
    expect((await git('/benim/parola')).name).toBe('benim-parola')
  })

  it('atamacı ve yönetici makinist sayfalarına giremez, ana sayfaya döner', async () => {
    vi.mocked(api.get).mockResolvedValue(kisi('ATAMACI'))
    expect((await git('/benim/gorevler')).name).toBe('ana')
    kullanici.value = null
    yuklendi.value = false
    vi.mocked(api.get).mockResolvedValue(kisi('YONETICI'))
    expect((await git('/benim/parola')).name).toBe('ana')
  })
})
