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

const satir = (ek: Partial<MakinistYuklemeSonucu['satirlar'][number]> = {}) => ({
  satirNo: 2,
  sicil: 'M001',
  adSoyad: 'Örnek Personel 1',
  tur: 'MAKINIST' as const,
  bagliGar: 'Ankara',
  makinistDurumu: 'AKTIF' as const,
  aracTipiSayisi: 1,
  hatSayisi: 2,
  durum: 'YENI' as const,
  uyarilar: [],
  ...ek,
})

const onizleme: MakinistYuklemeSonucu = {
  onizleme: true,
  toplamSatir: 5,
  eklenen: 2,
  zatenVar: 1,
  hataSayisi: 2,
  uyariSayisi: 1,
  satirlar: [
    satir(),
    satir({
      satirNo: 3,
      sicil: 'K001',
      adSoyad: 'Örnek Kondüktör',
      tur: 'KONDUKTOR',
      aracTipiSayisi: 0,
      hatSayisi: 0,
      uyarilar: ['Ehliyet bitişi geçmişte (31.12.2025): belge süresi dolmuş, atama yapılamaz'],
    }),
    satir({ satirNo: 4, sicil: 'M003', durum: 'ZATEN_VAR' }),
  ],
  hatalar: [
    { sayfa: 'Makinistler', satirNo: 5, sicil: 'M004', neden: "Bağlı gar bilinmiyor: 'Sivas'" },
    { sayfa: 'Araç Tipi Bröveleri', satirNo: 3, sicil: 'M009', neden: 'Sicil bu dosyada yok' },
  ],
  eksikTanimlar: [
    { tip: 'GAR', ad: 'Sivas' },
    { tip: 'ARAC_TIPI', ad: 'E99' },
  ],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/makinistler', name: 'makinistler', component: { template: '<div />' } },
      { path: '/tanimlar', name: 'tanimlar', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  return mount(MakinistYukleme, { global: { plugins: [router] }, attachTo: document.body })
}

async function dosyaSec(w: ReturnType<typeof mount>, f: File) {
  const girdi = w.get('#dosya')
  Object.defineProperty(girdi.element, 'files', { value: [f], configurable: true })
  await girdi.trigger('change')
  await flushPromises()
}

const xlsx = (kb = 1) => new File([new Uint8Array(kb * 1024)], 'makinistler.xlsx')
const onayDugmesi = (w: ReturnType<typeof mount>) =>
  w.findAll('button').find((b) => b.text().startsWith('Onayla ve yükle'))

describe('MakinistYukleme (F1, AK-1.2..1.4)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('açılışta başlık, şablon düğmesi, dosya alanı ve OK-45 notu; önizleme yok', async () => {
    const w = await kur()
    expect(w.get('h1').text()).toBe('Personel yükleme')
    expect(w.findAll('button').some((b) => b.text() === 'Şablonu indir')).toBe(true)
    expect(w.get('label[for=dosya]').text()).toContain('Excel dosyası')
    expect(w.text()).toContain('zaten olan personel güncellenmez')
    expect(w.find('table').exists()).toBe(false)
  })

  it('dosya seçilince OTOMATİK önizleme (onizle=true); özet, hata tablosu satır/neden, geçerli satırlar', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue(onizleme)
    const w = await kur()
    const f = xlsx()
    await dosyaSec(w, f)
    expect(makinistApi.iceAktar).toHaveBeenCalledWith(f, true)
    const ozet = w.get('[role=status]').text()
    expect(ozet).toContain('Toplam satır: 5')
    expect(ozet).toContain('Eklenecek: 2')
    expect(ozet).toContain('Atlanacak (zaten var): 1')
    expect(ozet).toContain('Hatalı: 2')
    expect(ozet).toContain('Uyarı: 1')

    const hatalar = w.get('table[aria-label="Hatalı satırlar"]')
    const hSatir = hatalar.findAll('tbody tr').map((r) => r.findAll('td').map((c) => c.text()))
    expect(hSatir[0]).toEqual(['Makinistler', '5', 'M004', "Bağlı gar bilinmiyor: 'Sivas'"])
    expect(hSatir[1]![0]).toBe('Araç Tipi Bröveleri')

    const g = w.get('table[aria-label="Geçerli satırlar"]').findAll('tbody tr')
    expect(g).toHaveLength(3)
    expect(g[0]!.text()).toContain('M001')
    expect(g[0]!.text()).toContain('Yeni')
    expect(g[1]!.text()).toContain('Kondüktör')
    expect(g[1]!.text()).toContain('belge süresi dolmuş')
    expect(g[2]!.text()).toContain('Atlandı: zaten var')
  })

  it('eksik tanımlar tipine göre listelenir ve Tanımlar sayfasına bağlantı verilir', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue(onizleme)
    const w = await kur()
    await dosyaSec(w, xlsx())
    const kutu = w.get('[data-test=eksik-tanimlar]')
    expect(kutu.text()).toContain('Gar: Sivas')
    expect(kutu.text()).toContain('Araç tipi: E99')
    const baglanti = kutu.get('a')
    expect(baglanti.text()).toBe("Tanımlar'a git")
    expect(baglanti.attributes('href')).toBe('/tanimlar')
  })

  it('Onayla yalnız eklenecek satır varken açık; onay onizle=false ile gider ve sonuç özeti gösterilir', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValueOnce(onizleme)
    const w = await kur()
    const f = xlsx()
    await dosyaSec(w, f)
    const onay = onayDugmesi(w)!
    expect(onay.attributes('disabled')).toBeUndefined()
    vi.mocked(makinistApi.iceAktar).mockResolvedValueOnce({
      ...onizleme,
      onizleme: false,
      satirlar: [],
      hatalar: [],
      eksikTanimlar: [],
    })
    await onay.trigger('click')
    await flushPromises()
    expect(makinistApi.iceAktar).toHaveBeenLastCalledWith(f, false)
    expect(w.get('[role=status]').text()).toContain('Yükleme tamamlandı. Eklenen: 2')
    expect(onayDugmesi(w)).toBeUndefined()
    expect(w.find('a[href="/makinistler"]').exists()).toBe(true)
  })

  it('eklenecek satır yoksa (hepsi zaten var) Onayla kilitli', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue({
      ...onizleme,
      eklenen: 0,
      satirlar: [satir({ durum: 'ZATEN_VAR' })],
    })
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(onayDugmesi(w)!.attributes('disabled')).toBeDefined()
  })

  it('5 MB üstü dosya hiç gönderilmez', async () => {
    const w = await kur()
    await dosyaSec(w, xlsx(5 * 1024 + 1))
    expect(makinistApi.iceAktar).not.toHaveBeenCalled()
    expect(w.get('#dosya-hata').text()).toContain('5 MB')
    expect(w.get('#dosya').attributes('aria-invalid')).toBe('true')
  })

  it('sunucu hatası (GECERSIZ_DOSYA) dosya alanında gösterilir; tablo çıkmaz', async () => {
    vi.mocked(makinistApi.iceAktar).mockRejectedValue(
      new ApiHata(400, 'GECERSIZ_DOSYA', 'Dosya .xlsx biçiminde değil.'),
    )
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(w.get('#dosya-hata').text()).toContain('.xlsx')
    expect(w.find('table').exists()).toBe(false)
  })

  it('şablon indirme hatası uyarı bandında (role=alert) görünür', async () => {
    vi.mocked(makinistApi.iceAktarSablon).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Şablon üretilemedi.'),
    )
    const w = await kur()
    await w
      .findAll('button')
      .find((b) => b.text() === 'Şablonu indir')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('Şablon üretilemedi')
  })

  it('hatalı satırların atlanacağı ve mevcut personelin bröve bilgisinin güncellenmeyeceği söylenir', async () => {
    vi.mocked(makinistApi.iceAktar).mockResolvedValue(onizleme)
    const w = await kur()
    await dosyaSec(w, xlsx())
    expect(w.text()).toContain('Hatalı satırlar atlanır')
    expect(w.text()).toContain('bröveleri güncellenmez')
    expect(w.text()).toContain(
      'herhangi bir hücresi (bröve sayfaları dahil) hatalıysa o personel hiç eklenmez',
    ) // OK-46
  })
})
