import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { iceAktar: vi.fn(), iceAktarSablon: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import MakinistYukleme from './MakinistYukleme.vue'
import { makinistApi, type MakinistYuklemeSonucu } from './makinistApi'

/** QA (yeni-ozellikler): makinist Excel yükleme ekranı: boş dosya, yalnız hatalı dosya, çift seçim, büyük dosya, onay hatası (409 yarışı). */
const bosSonuc: MakinistYuklemeSonucu = {
  onizleme: true,
  toplamSatir: 0,
  eklenen: 0,
  zatenVar: 0,
  hataSayisi: 0,
  uyariSayisi: 0,
  satirlar: [],
  hatalar: [],
  eksikTanimlar: [],
}

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

async function dosyaSec(w: Awaited<ReturnType<typeof kur>>, dosya: File) {
  const girdi = w.get('input[type="file"]')
  Object.defineProperty(girdi.element, 'files', { value: [dosya], configurable: true })
  await girdi.trigger('change')
  await flushPromises()
}
const xlsx = (boyut = 10) => new File([new Uint8Array(boyut)], 'm.xlsx')

describe('Personel yükleme ekranı (QA)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('satırı olmayan dosya: kullanıcıya "dosyada satır yok" denir (sessiz "0 personel" onay düğmesi değil)', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue(bosSonuc)
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(w.text()).toMatch(/satır yok|boş|veri yok/i)
    expect(w.text()).not.toContain('Onayla ve yükle (0 personel)')
  })

  it('yalnız hatalı satırlı dosya: onay düğmesi pasif, hata tablosu görünür', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue({
      ...bosSonuc,
      toplamSatir: 1,
      hataSayisi: 1,
      hatalar: [{ sayfa: 'Makinistler', satirNo: 2, sicil: 'M1', neden: 'Ad soyad boş' }],
    })
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(w.text()).toContain('Ad soyad boş')
    const dugme = w.findAll('button').find((b) => b.text().startsWith('Onayla'))
    expect(dugme?.attributes('disabled')).toBeDefined()
  })

  it('5 MB üstü dosya sunucuya hiç gönderilmez', async () => {
    const w = await kur()
    await dosyaSec(w, xlsx(5 * 1024 * 1024 + 1))
    expect(makinistApi.iceAktar).not.toHaveBeenCalled()
    expect(w.text()).toContain('çok büyük')
  })

  it('onay sırasında sunucu 409 (sicil yarışı) verirse hata alanda gösterilir, sonuç "tamamlandı" demez', async () => {
    vi.mocked(makinistApi.iceAktar)
      .mockResolvedValueOnce({
        ...bosSonuc,
        toplamSatir: 1,
        eklenen: 1,
        satirlar: [
          {
            satirNo: 2,
            sicil: 'M1',
            adSoyad: 'A',
            tur: 'MAKINIST',
            bagliGar: 'Ankara',
            aracTipiSayisi: 0,
            hatSayisi: 0,
            durum: 'YENI',
            uyarilar: [],
          } as never,
        ],
      })
      .mockRejectedValueOnce(
        new ApiHata(409, 'SICIL_TEKRARI', "'M1' sicilli bir makinist zaten var."),
      )
    const w = await kur()
    await dosyaSec(w, xlsx())
    await w
      .findAll('button')
      .find((b) => b.text().startsWith('Onayla'))!
      .trigger('click')
    await flushPromises()
    expect(w.text()).toContain('zaten var')
    expect(w.text()).not.toContain('Yükleme tamamlandı')
  })

  it('ikinci dosya seçilince önceki sonuç silinir (eski önizleme yeni dosyanın onayına karışmaz)', async () => {
    vi.mocked(makinistApi.iceAktar)
      .mockResolvedValueOnce({ ...bosSonuc, toplamSatir: 3, eklenen: 3 })
      .mockRejectedValueOnce(new ApiHata(400, 'GECERSIZ_DOSYA', 'Dosya boş.'))
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(w.text()).toContain('Toplam satır: 3')
    await dosyaSec(w, xlsx())
    expect(w.text()).not.toContain('Toplam satır: 3')
    expect(w.text()).toContain('Dosya boş.')
  })
})
