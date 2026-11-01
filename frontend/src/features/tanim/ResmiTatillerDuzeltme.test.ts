import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./tanimApi')>()),
  tanimApi: { resmiTatiller: vi.fn(), tatilEkle: vi.fn(), tatilSil: vi.fn() },
}))

import { kullanici } from '@/composables/oturum'
import ResmiTatiller from './ResmiTatiller.vue'
import { tanimApi, type ResmiTatil } from './tanimApi'

// OK-50 (O-5, D-5): odak kaybolmaz, yıl yarışı yok, başka yıla eklenen tatil görünür.
async function kur() {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'YONETICI', aktif: true }
  const w = mount(ResmiTatiller, { attachTo: document.body })
  await flushPromises()
  return w
}
const SIL = '[aria-label="29.10.2026 Cumhuriyet Bayramı resmi tatilini sil"]'

describe('ResmiTatiller düzeltmeleri (OK-50)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(tanimApi.resmiTatiller).mockResolvedValue([
      { tarih: '2026-10-29', ad: 'Cumhuriyet Bayramı' },
    ])
  })

  it('O-5: Ekle sürerken disabled değil aria-disabled; hata olunca odak Ekle düğmesinde kalır, form değerleri korunur', async () => {
    const w = await kur()
    let reddet!: (e: unknown) => void
    vi.mocked(tanimApi.tatilEkle).mockReturnValueOnce(new Promise((_, r) => (reddet = r)))
    await w.get('#tatil-tarih').setValue('2026-10-30')
    await w.get('#tatil-ad').setValue('Ara')
    const ekle = w.get('button[type="submit"]')
    ;(ekle.element as HTMLButtonElement).focus()
    await w.get('[data-test="tatil-formu"]').trigger('submit')
    expect(ekle.attributes('disabled')).toBeUndefined()
    expect(ekle.attributes('aria-disabled')).toBe('true')
    await w.get('[data-test="tatil-formu"]').trigger('submit') // sürerken ikinci gönderim yok sayılır
    expect(tanimApi.tatilEkle).toHaveBeenCalledTimes(1)
    reddet(new ApiHata(409, 'TATIL_VAR', 'Bu tarih için resmi tatil zaten tanımlı.'))
    await flushPromises()
    expect(document.activeElement).toBe(ekle.element)
    expect((w.get('#tatil-ad').element as HTMLInputElement).value).toBe('Ara')
  })

  it('O-5: silme başarılı olunca satır (ve düğmesi) kaybolur; odak forma (Tarih alanına) döner', async () => {
    const w = await kur()
    let bitir!: () => void
    vi.mocked(tanimApi.tatilSil).mockReturnValue(new Promise((c) => (bitir = () => c(undefined))))
    vi.mocked(tanimApi.resmiTatiller).mockResolvedValue([])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const sil = w.get(SIL)
    ;(sil.element as HTMLButtonElement).focus()
    await sil.trigger('click')
    expect(sil.attributes('disabled')).toBeUndefined() // sürerken de disabled değil
    expect(sil.attributes('aria-disabled')).toBe('true')
    bitir()
    await flushPromises()
    expect(w.find(SIL).exists()).toBe(false)
    expect(document.activeElement).toBe(w.get('#tatil-tarih').element)
  })

  it('D-5: başka yıla eklenen tatil görünen listede olur (yıl o yıla geçer) ve bilgi bunu söyler', async () => {
    const w = await kur()
    vi.mocked(tanimApi.tatilEkle).mockResolvedValue({ tarih: '2027-01-01', ad: 'Yılbaşı' })
    vi.mocked(tanimApi.resmiTatiller).mockResolvedValue([{ tarih: '2027-01-01', ad: 'Yılbaşı' }])
    await w.get('#tatil-tarih').setValue('2027-01-01')
    await w.get('#tatil-ad').setValue('Yılbaşı')
    await w.get('[data-test="tatil-formu"]').trigger('submit')
    await flushPromises()
    expect(tanimApi.resmiTatiller).toHaveBeenLastCalledWith(2027)
    expect((w.get('#tatil-yil').element as HTMLInputElement).value).toBe('2027')
    expect(w.text()).toContain('2027 yılının listesi gösteriliyor')
    expect(w.findAll('[data-test="tatil-satiri"]')).toHaveLength(1)
  })

  it('D-5: yıl hızlı değişirse eski yılın geç yanıtı yeni yılın listesini ezmez', async () => {
    const w = await kur()
    let eskiBitir!: (l: ResmiTatil[]) => void
    vi.mocked(tanimApi.resmiTatiller).mockImplementation((yil) =>
      yil === 2027
        ? new Promise((c) => (eskiBitir = c)) // yavaş
        : Promise.resolve(yil === 2028 ? [{ tarih: '2028-05-01', ad: 'Emek' }] : []),
    )
    await w.get('#tatil-yil').setValue('2027') // yavaş istek (2027)
    await w.get('#tatil-yil').setValue('2028') // hızlı istek (2028)
    await flushPromises()
    eskiBitir([{ tarih: '2027-01-01', ad: 'Yılbaşı' }]) // eski yılın geç yanıtı
    await flushPromises()
    expect(w.findAll('[data-test="tatil-satiri"]')).toHaveLength(1)
    expect(w.text()).toContain('Emek')
    expect(w.text()).not.toContain('Yılbaşı')
  })
})
