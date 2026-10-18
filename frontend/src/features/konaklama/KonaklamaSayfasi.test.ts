import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./konaklamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./konaklamaApi')>()),
  konaklamaApi: { liste: vi.fn(), oneri: vi.fn(), ekle: vi.fn(), guncelle: vi.fn(), sil: vi.fn() },
}))
vi.mock('@/features/makinist/makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/makinist/makinistApi')>()),
  makinistApi: { liste: vi.fn() },
}))
vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))

import { makinistApi } from '@/features/makinist/makinistApi'
import { tanimApi } from '@/features/tanim/tanimApi'
import KonaklamaSayfasi from './KonaklamaSayfasi.vue'
import { konaklamaApi, type Konaklama, type KonaklamaOnerisi } from './konaklamaApi'

const oneri = (ek: Partial<KonaklamaOnerisi> = {}): KonaklamaOnerisi => ({
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  garId: 3,
  gar: 'Sivas',
  bas: '2026-10-14T11:30:00+03:00',
  bit: '2026-10-15T06:00:00+03:00',
  sureDk: 18 * 60 + 30,
  gece: 1,
  kayitli: false,
  ...ek,
})
const kayit = (ek: Partial<Konaklama> = {}): Konaklama => ({
  id: 5,
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  garId: 3,
  gar: 'Sivas',
  yerTipi: 'OTEL',
  giris: '2026-10-14T11:30:00+03:00',
  cikis: '2026-10-15T06:00:00+03:00',
  gece: 1,
  notu: null,
  versiyon: 0,
  ...ek,
})

async function kur() {
  const w = mount(KonaklamaSayfasi)
  await flushPromises()
  return w
}

describe('KonaklamaSayfasi (F2b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(makinistApi.liste).mockResolvedValue([
      { id: 7, sicil: 'D007', adSoyad: 'Ali Veli', durum: 'AKTIF', bagliGar: 'Ankara' },
    ] as never)
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 3, tip: 'GAR', ad: 'Sivas' },
    ])
    vi.mocked(konaklamaApi.oneri).mockResolvedValue([
      oneri(),
      oneri({ garId: 2, gar: 'Konya', kayitli: true }),
    ])
    vi.mocked(konaklamaApi.liste).mockResolvedValue([])
  })

  it('öneri görünür; kayıtlıysa işaretli ve "Kaydet" düğmesi yok; "Kaydet" öneriyi forma doldurur', async () => {
    const w = await kur()
    const satirlar = w.findAll('[data-test="oneri"]')

    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('Sivas')
    expect(satirlar[0]!.text()).toContain('18 s 30 dk')
    expect(satirlar[0]!.find('[data-test="kayitli"]').exists()).toBe(false)
    expect(satirlar[1]!.find('[data-test="kayitli"]').exists()).toBe(true)
    // kayıtlı göstergesi simge + metin (emoji yerine svg simgesi)
    expect(satirlar[1]!.get('[data-test="kayitli"]').text()).toBe('Kayıtlı')
    expect(satirlar[1]!.get('[data-test="kayitli"] svg').attributes('data-simge')).toBe('basari')
    expect(satirlar[1]!.findAll('button')).toHaveLength(0)

    await satirlar[0]!.get('button').trigger('click')
    expect((w.get('#kon-makinist').element as HTMLSelectElement).value).toBe('7')
    expect((w.get('#kon-gar').element as HTMLSelectElement).value).toBe('3')
    expect((w.get('#kon-giris').element as HTMLInputElement).value).toBe('2026-10-14T11:30')
    expect((w.get('#kon-cikis').element as HTMLInputElement).value).toBe('2026-10-15T06:00')
    expect(konaklamaApi.ekle).not.toHaveBeenCalled() // öneri yazılmaz
  })

  it('QA BUG-5 (WCAG 2.5.3): öneri düğmesinin görünür metni erişilebilir adın parçasıdır ve yanıltıcı "Kaydet" demez', async () => {
    const w = await kur()
    const dugme = w.findAll('[data-test="oneri"]')[0]!.get('button')
    expect(dugme.text()).toBe('Forma al')
    expect(dugme.attributes('aria-label')!.toLowerCase()).toContain(dugme.text().toLowerCase())
  })

  it('kaydet: istanbul ofsetli zamanlarla POST eder, sonra listeyi yeniler', async () => {
    vi.mocked(konaklamaApi.ekle).mockResolvedValue(kayit())
    const w = await kur()
    await w.findAll('[data-test="oneri"]')[0]!.get('button').trigger('click')
    await w.get('#kon-yer').setValue('OTEL')
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()

    expect(konaklamaApi.ekle).toHaveBeenCalledWith({
      makinistId: 7,
      garId: 3,
      yerTipi: 'OTEL',
      giris: '2026-10-14T11:30:00+03:00',
      cikis: '2026-10-15T06:00:00+03:00',
      notu: undefined,
    })
    expect(w.text()).toContain('Konaklama kaydedildi.')
    expect(konaklamaApi.liste).toHaveBeenCalledTimes(2) // ilk yükleme + yenileme
  })

  it('UX-1: öneriden "Kaydet" formu görünür kılar (kaydırma + odak); kayıt sonrası mesaj formun yanındadır ve görünür kılınır', async () => {
    const kaydir = vi.fn()
    Element.prototype.scrollIntoView = kaydir
    vi.mocked(konaklamaApi.ekle).mockResolvedValue(kayit())
    const w = await kur()

    await w.findAll('[data-test="oneri"]')[0]!.get('button').trigger('click')
    await flushPromises()
    expect(kaydir).toHaveBeenCalledTimes(1)
    expect(kaydir.mock.contexts[0]).toBe(w.findAll('h2')[1]!.element) // "Yeni konaklama" başlığı

    kaydir.mockClear()
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()
    expect(kaydir).toHaveBeenCalled()
    const bildirim = w.get('[role="status"]')
    expect(bildirim.text()).toContain('Konaklama kaydedildi.')
    expect(w.findAll('h2')[1]!.element.nextElementSibling!.contains(bildirim.element)).toBe(true) // sayfa başında değil, formun hemen üstünde
  })

  it('çakışma (409) mesajı gösterilir; çıkış girişten önceyse istek gitmez', async () => {
    vi.mocked(konaklamaApi.ekle).mockRejectedValue(
      new ApiHata(
        409,
        'KONAKLAMA_CAKISMASI',
        'Bu makinistin bu aralıkla kesişen başka bir konaklama kaydı var.',
      ),
    )
    const w = await kur()
    await w.get('#kon-makinist').setValue(7)
    await w.get('#kon-gar').setValue(3)
    await w.get('#kon-giris').setValue('2026-10-14T11:30')
    await w.get('#kon-cikis').setValue('2026-10-14T10:00')
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()
    expect(konaklamaApi.ekle).not.toHaveBeenCalled()
    expect(w.text()).toContain('Çıkış, girişten sonra olmalı.')
    const cikis = w.get('#kon-cikis')
    expect(cikis.attributes('aria-invalid')).toBe('true')
    expect(cikis.attributes('aria-describedby')).toBe('kon-cikis-hata')
    expect(w.get('#kon-cikis-hata').text()).toBe('Çıkış, girişten sonra olmalı.')

    await w.get('#kon-cikis').setValue('2026-10-15T06:00')
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('kesişen başka bir konaklama kaydı var')
  })

  it('zorunlu alan işareti; boş gönderimde hatalar alanın altında ve odak ilk hatalı alanda', async () => {
    const w = mount(KonaklamaSayfasi, { attachTo: document.body })
    await flushPromises()
    expect(w.findAll('form.kon-form .alan-zorunlu')).toHaveLength(4)
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()
    expect(konaklamaApi.ekle).not.toHaveBeenCalled()
    expect(w.get('#kon-makinist').attributes('aria-invalid')).toBe('true')
    expect(w.get('#kon-makinist-hata').text()).toBe('Makinist seçin.')
    expect(document.activeElement).toBe(w.get('#kon-makinist').element)
    w.unmount()
  })

  it('düzelt: sürümle PUT eder; sil: onaydan sonra DELETE', async () => {
    vi.mocked(konaklamaApi.liste).mockResolvedValue([kayit({ versiyon: 3 })])
    vi.mocked(konaklamaApi.guncelle).mockResolvedValue(kayit())
    vi.mocked(konaklamaApi.sil).mockResolvedValue(undefined as never)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const w = await kur()

    await w.get('[data-test="kayit"] button[aria-label*="düzelt"]').trigger('click')
    await w.get('#kon-yer').setValue('YATAKHANE')
    await w.get('form.kon-form').trigger('submit')
    await flushPromises()
    expect(konaklamaApi.guncelle).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ yerTipi: 'YATAKHANE', versiyon: 3 }),
    )

    await w.get('[data-test="kayit"] button[aria-label*="sil"]').trigger('click')
    await flushPromises()
    expect(konaklamaApi.sil).toHaveBeenCalledWith(5)
  })
})
