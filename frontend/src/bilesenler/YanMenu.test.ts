import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import type { KullaniciDto } from '@/api/tipler'
import { kullanici } from '@/composables/oturum'
import YanMenu from './YanMenu.vue'

const bos = { template: '<div />' }
const ROTALAR: RouteRecordRaw[] = [
  { path: '/', component: bos, meta: { menu: { ad: 'Ana Sayfa', sira: 0, simge: 'ev' } } },
  {
    path: '/sefer',
    component: bos,
    meta: { menu: { ad: 'Seferler', sira: 20, grup: 'Atama', simge: 'gar' } },
  },
  {
    path: '/atama',
    component: bos,
    meta: { menu: { ad: 'Atama Masası', sira: 25, grup: 'Atama', simge: 'tren' } },
  },
  {
    path: '/ayarlar',
    component: bos,
    meta: { rol: 'Y', menu: { ad: 'Ayarlar', sira: 40, grup: 'Ayarlar', simge: 'ayarlar' } },
  },
]
const kisi = (rol: KullaniciDto['rol']): KullaniciDto => ({
  id: 1,
  eposta: 'a@b.c',
  adSoyad: 'Ayşe',
  rol,
  aktif: true,
})

const acik: VueWrapper[] = []
async function kur(rol: KullaniciDto['rol'], yol = '/sefer') {
  kullanici.value = kisi(rol)
  const router = createRouter({ history: createMemoryHistory(), routes: ROTALAR })
  await router.push(yol)
  const w = mount(YanMenu, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  acik.push(w)
  return { w, router }
}

describe('YanMenu', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => {
    acik.splice(0).forEach((w) => w.unmount())
    kullanici.value = null
  })

  it('yönetici: gruplu öğeler; grup başlığı bağlantı değil, liste başlığa bağlı', async () => {
    const { w } = await kur('YONETICI')
    expect(w.get('nav').attributes('aria-label')).toBe('Ana menü')
    expect(w.findAll('nav a').map((a) => a.text())).toEqual([
      'Ana Sayfa',
      'Seferler',
      'Atama Masası',
      'Ayarlar',
    ])
    const basliklar = w.findAll('.menu-grup-baslik')
    expect(basliklar.map((b) => b.text())).toEqual(['Atama', 'Ayarlar'])
    expect(basliklar.every((b) => b.element.tagName === 'P')).toBe(true)
    const liste = w.findAll('ul')[1]!
    expect(liste.attributes('aria-labelledby')).toBe(basliklar[0]!.attributes('id'))
  })

  it('atamacı (rol Y yok): Ayarlar grubu hiç çizilmez', async () => {
    const { w } = await kur('ATAMACI')
    expect(w.findAll('nav a').map((a) => a.text())).not.toContain('Ayarlar')
    expect(w.findAll('.menu-grup-baslik').map((b) => b.text())).toEqual(['Atama'])
  })

  it('geçerli sayfa aria-current=page, ötekilerde yok', async () => {
    const { w } = await kur('ATAMACI', '/atama')
    const akim = w.findAll('nav a').filter((a) => a.attributes('aria-current') === 'page')
    expect(akim.map((a) => a.text())).toEqual(['Atama Masası'])
  })

  it('her öğede dekoratif simge; metin DOM’da', async () => {
    const { w } = await kur('ATAMACI')
    const a = w.findAll('nav a')[1]!
    expect(a.find('svg[data-simge="gar"]').attributes('aria-hidden')).toBe('true')
    expect(a.text()).toBe('Seferler')
  })

  it('daraltma: düğme aria-expanded; tercih localStorage’a yazılır; daraltılmışta metin DOM’da kalır', async () => {
    localStorage.setItem('yanmenu-daraltilmis', '0')
    const { w } = await kur('ATAMACI')
    const dugme = w.get('.menu-daralt')
    expect(dugme.attributes('aria-expanded')).toBe('true')
    expect(w.get('nav').classes()).not.toContain('daraltilmis')
    await dugme.trigger('click')
    expect(w.get('nav').classes()).toContain('daraltilmis')
    expect(dugme.attributes('aria-expanded')).toBe('false')
    expect(dugme.attributes('aria-label')).toBe('Menüyü genişlet')
    expect(localStorage.getItem('yanmenu-daraltilmis')).toBe('1')
    expect(w.findAll('nav a').map((a) => a.text())).toContain('Seferler')
    expect(w.findAll('nav a')[1]!.attributes('title')).toBe('Seferler')
  })

  it('kayıtlı tercih varsayılanı ezer', async () => {
    localStorage.setItem('yanmenu-daraltilmis', '1')
    const { w } = await kur('ATAMACI')
    expect(w.get('nav').classes()).toContain('daraltilmis')
  })

  it('dar ekran: Menü düğmesi listeyi açar/kapatır; sayfa değişince kapanır', async () => {
    const { w, router } = await kur('ATAMACI')
    const ac = w.get('.menu-ac')
    expect(ac.attributes('aria-expanded')).toBe('false')
    await ac.trigger('click')
    expect(ac.attributes('aria-expanded')).toBe('true')
    expect(w.get('nav').classes()).toContain('mobil-acik')
    await router.push('/atama')
    await flushPromises()
    expect(w.get('nav').classes()).not.toContain('mobil-acik')
  })

  it('Tab sırası = DOM sırası: bağlantılar menü sırasıyla, grup başlıkları odaklanamaz', async () => {
    const { w } = await kur('YONETICI')
    const odaklanabilir = w
      .findAll('nav a, nav button, nav p')
      .filter((e) => e.element.tagName !== 'P')
      .map((e) => e.text() || e.attributes('aria-label') || '')
    expect(odaklanabilir[0]).toBe('Menü') // dar ekran düğmesi (geniş ekranda CSS ile gizli)
    expect(odaklanabilir.slice(1, 5)).toEqual(['Ana Sayfa', 'Seferler', 'Atama Masası', 'Ayarlar'])
  })

  describe('varsayılan genişlik (tercih yoksa)', () => {
    const eski = window.innerWidth
    afterEach(() =>
      Object.defineProperty(window, 'innerWidth', { value: eski, configurable: true }),
    )
    const genislikte = (px: number) =>
      Object.defineProperty(window, 'innerWidth', { value: px, configurable: true })

    it('≥ 1200 px açık gelir (1366 dahil)', async () => {
      genislikte(1366)
      const { w } = await kur('ATAMACI')
      expect(w.get('nav').classes()).not.toContain('daraltilmis')
    })

    it('< 1200 px daraltılmış gelir', async () => {
      genislikte(1100)
      const { w } = await kur('ATAMACI')
      expect(w.get('nav').classes()).toContain('daraltilmis')
    })

    // QA BUG-14: pencere küçülüp büyüyünce (tercih yokken) varsayılan genişliği izler
    it('tercih yokken pencere yeniden boyutlanınca varsayılan izlenir (375 → 1366 açılır, 1366 → 1100 daralır)', async () => {
      genislikte(375)
      const { w } = await kur('ATAMACI')
      expect(w.get('nav').classes()).toContain('daraltilmis')
      genislikte(1366)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()
      expect(w.get('nav').classes()).not.toContain('daraltilmis')
      genislikte(1100)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()
      expect(w.get('nav').classes()).toContain('daraltilmis')
    })

    it('kullanıcı daraltma düğmesine bastıktan sonra yeniden boyutlama tercihi ezmez', async () => {
      genislikte(1366)
      const { w } = await kur('ATAMACI')
      await w.get('.menu-daralt').trigger('click') // kullanıcı daralttı
      genislikte(1600)
      window.dispatchEvent(new Event('resize'))
      await flushPromises()
      expect(w.get('nav').classes()).toContain('daraltilmis')
    })

    it('kullanıcı tercihi genişlikten üstündür (1366 px + kayıtlı daraltma)', async () => {
      genislikte(1366)
      localStorage.setItem('yanmenu-daraltilmis', '1')
      const { w } = await kur('ATAMACI')
      expect(w.get('nav').classes()).toContain('daraltilmis')
    })
  })
})
