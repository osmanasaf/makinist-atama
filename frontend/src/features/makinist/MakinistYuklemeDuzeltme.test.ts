import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { iceAktar: vi.fn(), iceAktarSablon: vi.fn() },
}))

import MakinistYukleme from './MakinistYukleme.vue'
import { makinistApi, type MakinistYuklemeSonucu } from './makinistApi'

// OK-50 (O-2): önizleme sürerken başka dosya seçilirse eski dosyanın yanıtı atılır; onay her zaman SON seçilen dosyanın önizlemesine dayanır.
const sonuc = (eklenen: number): MakinistYuklemeSonucu => ({
  onizleme: true,
  toplamSatir: eklenen,
  eklenen,
  zatenVar: 0,
  hataSayisi: 0,
  uyariSayisi: 0,
  satirlar: [],
  hatalar: [],
  eksikTanimlar: [],
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/makinistler/yukle', name: 'makinist-yukle', component: MakinistYukleme },
      { path: '/makinistler', name: 'makinistler', component: { template: '<div />' } },
      { path: '/tanimlar', name: 'tanimlar', component: { template: '<div />' } },
    ],
  })
  await router.push('/makinistler/yukle')
  const w = mount(MakinistYukleme, { attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return w
}
async function sec(w: Awaited<ReturnType<typeof kur>>, ad: string) {
  const girdi = w.get('input[type="file"]')
  Object.defineProperty(girdi.element, 'files', {
    value: [new File([new Uint8Array(10)], ad)],
    configurable: true,
  })
  await girdi.trigger('change')
}

describe('Personel yükleme: dosya seçim yarışı (O-2)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it("A seçilip önizleme sürerken B seçilirse: A geç gelse de ekranda B'nin önizlemesi kalır; onay B ile yapılır", async () => {
    let bitirA!: (s: MakinistYuklemeSonucu) => void
    let bitirB!: (s: MakinistYuklemeSonucu) => void
    vi.mocked(makinistApi.iceAktar)
      .mockReturnValueOnce(new Promise((c) => (bitirA = c)))
      .mockReturnValueOnce(new Promise((c) => (bitirB = c)))
    const w = await kur()
    await sec(w, 'a.xlsx')
    await sec(w, 'b.xlsx')

    bitirB(sonuc(2))
    await flushPromises()
    bitirA(sonuc(5)) // eski dosyanın geç yanıtı
    await flushPromises()

    expect(w.text()).toContain('Onayla ve yükle (2 personel)')
    expect(w.text()).not.toContain('Onayla ve yükle (5 personel)')
    vi.mocked(makinistApi.iceAktar).mockResolvedValueOnce({ ...sonuc(2), onizleme: false })
    await w
      .findAll('button')
      .find((b) => b.text().startsWith('Onayla'))!
      .trigger('click')
    await flushPromises()
    const cagri = vi.mocked(makinistApi.iceAktar).mock.calls.at(-1)!
    expect((cagri[0] as File).name).toBe('b.xlsx')
    expect(cagri[1]).toBe(false)
  })

  it("B önizlemesi sürerken A'nın yanıtı gelirse düğme erken açılmaz (mesgul B bitene kadar sürer)", async () => {
    let bitirA!: (s: MakinistYuklemeSonucu) => void
    let bitirB!: (s: MakinistYuklemeSonucu) => void
    vi.mocked(makinistApi.iceAktar)
      .mockReturnValueOnce(new Promise((c) => (bitirA = c)))
      .mockReturnValueOnce(new Promise((c) => (bitirB = c)))
    const w = await kur()
    await sec(w, 'a.xlsx')
    await sec(w, 'b.xlsx')
    bitirA(sonuc(5))
    await flushPromises()
    expect(w.text()).not.toContain('Onayla ve yükle')
    expect(w.text()).toContain('Dosya okunuyor')
    bitirB(sonuc(2))
    await flushPromises()
    expect(w.text()).toContain('Onayla ve yükle (2 personel)')
  })
})
