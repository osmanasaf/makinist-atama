import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./kurumApi', () => ({ kurumApi: { adYaz: vi.fn(), logoYaz: vi.fn(), logoSil: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import { kurumAdi, kurumGuncelle, kurumSifirla, logoAdresi } from '@/composables/kurum'
import KurumAyari from './KurumAyari.vue'
import { kurumApi } from './kurumApi'

const AD = 'Örnek İşletme (demo)'

function dosya(ad: string, tur: string, boyutKb = 1): File {
  return new File([new Uint8Array(boyutKb * 1024)], ad, { type: tur })
}

async function dosyaSec(w: ReturnType<typeof mount>, f: File) {
  const girdi = w.get('#kurum-logo')
  Object.defineProperty(girdi.element, 'files', { value: [f], configurable: true })
  await girdi.trigger('change')
  await flushPromises()
}

describe('KurumAyari (F1, AK-1.7/1.8)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kurumSifirla()
  })

  it('boş kurulumda ad alanı boş, logo yok; ürün adı yardım metninde geçer', () => {
    const w = mount(KurumAyari)
    expect((w.get('#kurum-ad').element as HTMLInputElement).value).toBe('')
    expect(w.get('[data-test=logo-yok]').text()).toBe('Logo yüklenmedi.')
    expect(w.get('#kurum-ad-yardim').text()).toContain('Makinist Atama')
    expect(w.find('img').exists()).toBe(false)
  })

  it('kurum adı girilmişken de yardım metni ürün adını söyler (kurum adını değil)', () => {
    kurumGuncelle({ ad: AD, logoVar: false, logoSurum: 1 })
    const w = mount(KurumAyari)
    expect(w.get('#kurum-ad-yardim').text()).toContain('ürün adı (Makinist Atama)')
  })

  it('adı kırpıp kaydeder; kabuk durumu güncellenir; bildirim role=status', async () => {
    vi.mocked(kurumApi.adYaz).mockResolvedValue({ ad: AD, logoVar: false, logoSurum: 1 })
    const w = mount(KurumAyari)
    await w.get('#kurum-ad').setValue(`  ${AD}  `)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(kurumApi.adYaz).toHaveBeenCalledWith(AD)
    expect(kurumAdi.value).toBe(AD)
    expect(w.get('[role=status]').text()).toBe('Kurum adı kaydedildi.')
    expect(w.emitted('degisti')).toHaveLength(1)
  })

  it('boş ad uca gitmez; hata alana bağlanır (aria-invalid + describedby)', async () => {
    const w = mount(KurumAyari)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(kurumApi.adYaz).not.toHaveBeenCalled()
    expect(w.get('#kurum-ad-hata').text()).toBe('Kurum adı boş olamaz.')
    expect(w.get('#kurum-ad').attributes('aria-invalid')).toBe('true')
    expect(w.get('#kurum-ad').attributes('aria-describedby')).toContain('kurum-ad-hata')
  })

  it('sunucu ad hatasını gösterir', async () => {
    vi.mocked(kurumApi.adYaz).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Kurum adı en fazla 120 karakter olabilir.'),
    )
    const w = mount(KurumAyari)
    await w.get('#kurum-ad').setValue('x')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('#kurum-ad-hata').text()).toContain('120')
  })

  it('PNG/JPEG yüklenir: ham dosya uca gider, logo önizlemesi alt metinle çıkar', async () => {
    kurumGuncelle({ ad: AD, logoVar: false, logoSurum: 1 })
    const png = dosya('logo.png', 'image/png', 10)
    vi.mocked(kurumApi.logoYaz).mockResolvedValue({ ad: AD, logoVar: true, logoSurum: 2 })
    const w = mount(KurumAyari)
    await dosyaSec(w, png)
    expect(kurumApi.logoYaz).toHaveBeenCalledWith(png)
    expect(logoAdresi.value).toBe('/api/kurum/logo?s=2')
    expect(w.get('img').attributes('src')).toBe('/api/kurum/logo?s=2')
    expect(w.get('img').attributes('alt')).toBe(`${AD} logosu`)
    expect(w.get('[role=status]').text()).toBe('Logo yüklendi.')
  })

  it.each([
    ['logo.svg', 'image/svg+xml', 1],
    ['anim.gif', 'image/gif', 1],
    ['foto.webp', 'image/webp', 1],
    ['metin.txt', 'text/plain', 1],
  ])('%s türü istemcide reddedilir, uca gitmez', async (ad, tur, kb) => {
    const w = mount(KurumAyari)
    await dosyaSec(w, dosya(ad, tur, kb))
    expect(kurumApi.logoYaz).not.toHaveBeenCalled()
    expect(w.get('#kurum-logo-hata').text()).toBe('Logo yalnız PNG veya JPEG olabilir.')
    expect(w.get('#kurum-logo').attributes('aria-invalid')).toBe('true')
  })

  it('256 KB üstü istemcide reddedilir; sınırda (256 KB) uca gider', async () => {
    vi.mocked(kurumApi.logoYaz).mockResolvedValue({ ad: null, logoVar: true, logoSurum: 1 })
    const w = mount(KurumAyari)
    await dosyaSec(w, dosya('buyuk.png', 'image/png', 257))
    expect(kurumApi.logoYaz).not.toHaveBeenCalled()
    expect(w.get('#kurum-logo-hata').text()).toContain('256 KB')
    await dosyaSec(w, dosya('sinir.png', 'image/png', 256))
    expect(kurumApi.logoYaz).toHaveBeenCalledTimes(1)
  })

  it('sunucu logo hatasını (piksel, bozuk içerik) olduğu gibi gösterir', async () => {
    vi.mocked(kurumApi.logoYaz).mockRejectedValue(
      new ApiHata(400, 'GECERSIZ_DOSYA', 'Logo en çok 1024 x 1024 piksel olabilir.'),
    )
    const w = mount(KurumAyari)
    await dosyaSec(w, dosya('logo.png', 'image/png'))
    expect(w.get('#kurum-logo-hata').text()).toContain('1024')
    expect(w.find('[role=status]').exists()).toBe(false)
  })

  it('logoyu kaldır: uç çağrılır, önizleme kalkar', async () => {
    kurumGuncelle({ ad: AD, logoVar: true, logoSurum: 3 })
    vi.mocked(kurumApi.logoSil).mockResolvedValue({ ad: AD, logoVar: false, logoSurum: 4 })
    const w = mount(KurumAyari)
    const dugme = w.findAll('button').find((b) => b.text() === 'Logoyu kaldır')!
    await dugme.trigger('click')
    await flushPromises()
    expect(kurumApi.logoSil).toHaveBeenCalled()
    expect(w.find('img').exists()).toBe(false)
    expect(w.find('[data-test=logo-yok]').exists()).toBe(true)
  })

  it('dosya alanı ve ad alanı etiketli; yalnız PNG/JPEG kabul eden accept', () => {
    const w = mount(KurumAyari)
    expect(w.get('label[for=kurum-ad]').text()).toBe('Kurum adı')
    expect(w.get('label[for=kurum-logo]').text()).toContain('Logo')
    expect(w.get('#kurum-logo').attributes('accept')).toBe('image/png,image/jpeg')
  })
})
