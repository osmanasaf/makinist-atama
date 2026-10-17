import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./tanimApi')>()),
  tanimApi: {
    liste: vi.fn(),
    intikalSureleri: vi.fn(),
    intikalSuresiYaz: vi.fn(),
    intikalSuresiSil: vi.fn(),
  },
}))

import { kullanici } from '@/composables/oturum'
import type { Rol } from '@/api/tipler'
import GarSureleri from './GarSureleri.vue'
import { tanimApi, type GarIntikalSuresi } from './tanimApi'

const sure = (ek: Partial<GarIntikalSuresi> = {}): GarIntikalSuresi => ({
  garAId: 1,
  garA: 'Ankara',
  garBId: 2,
  garB: 'Eskişehir',
  dakika: 90,
  versiyon: 4,
  ...ek,
})

function girisYap(rol: Rol) {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
}

async function kur() {
  const w = mount(GarSureleri)
  await flushPromises()
  return w
}

describe('GarSureleri (İ-2, MK-109)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.intikalSureleri).mockResolvedValue([sure()])
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'Eskişehir' },
      { id: 3, tip: 'GAR', ad: 'Konya' },
    ])
  })

  it('personel salt okur: süre tablosu görünür, form ve düğmeler yok', async () => {
    girisYap('ATAMACI')
    const w = await kur()

    expect(w.get('[data-test="sure-satiri"]').text()).toContain('Ankara – Eskişehir')
    expect(w.get('[data-test="sure-satiri"]').text()).toContain('90 dk')
    expect(w.find('[data-test="sure-formu"]').exists()).toBe(false)
    expect(w.findAll('button')).toHaveLength(0)
    expect(tanimApi.liste).not.toHaveBeenCalled()
  })

  it('tablo boşsa mesaj gösterilir', async () => {
    girisYap('ATAMACI')
    vi.mocked(tanimApi.intikalSureleri).mockResolvedValue([])
    const w = await kur()

    expect(w.text()).toContain('Tanımlı süre yok.')
  })

  it('yönetici yeni çift ekler (sürümsüz); çift sırası önemsizdir', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.intikalSuresiYaz).mockResolvedValue(
      sure({ garBId: 3, garB: 'Konya', dakika: 150 }),
    )
    const w = await kur()

    expect(w.get('[data-test="sure-formu"] button[type="submit"]').text()).toBe('Ekle')
    await w.get('#sure-gar-a').setValue('3')
    await w.get('#sure-gar-b').setValue('1')
    await w.get('#sure-dk').setValue('150')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(tanimApi.intikalSuresiYaz).toHaveBeenCalledWith({ garAId: 3, garBId: 1, dakika: 150 })
    expect(w.text()).toContain('Süre eklendi.')
    expect(tanimApi.intikalSureleri).toHaveBeenCalledTimes(2)
  })

  it('aynı gar iki kez seçilince Ekle pasif ve nedeni yazılır (UX-1)', async () => {
    girisYap('YONETICI')
    const w = await kur()

    await w.get('#sure-gar-a').setValue('3')
    await w.get('#sure-gar-b').setValue('3')
    await w.get('#sure-dk').setValue('60')

    expect(
      w.get('[data-test="sure-formu"] button[type="submit"]').attributes('disabled'),
    ).toBeDefined()
    expect(w.get('[data-test="ayni-gar-ipucu"]').text()).toBe(
      'Süre iki farklı gar arasında tanımlanır.',
    )
  })

  it('kayıtlı çift seçilince "Güncelle" olur ve sürüm gönderilir; Düzenle formu doldurur', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.intikalSuresiYaz).mockResolvedValue(sure({ dakika: 120 }))
    const w = await kur()

    await w.get('[aria-label="Ankara – Eskişehir süresini düzenle"]').trigger('click')
    expect((w.get('#sure-dk').element as HTMLInputElement).value).toBe('90')
    expect(w.get('button[type="submit"]').text()).toBe('Güncelle')
    await w.get('#sure-dk').setValue('120')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(tanimApi.intikalSuresiYaz).toHaveBeenCalledWith({
      garAId: 1,
      garBId: 2,
      dakika: 120,
      versiyon: 4,
    })
    expect(w.text()).toContain('Süre güncellendi.')
  })

  it('aralık dışı süre ve aynı gar gönderilmez (düğme kapalı)', async () => {
    girisYap('YONETICI')
    const w = await kur()

    await w.get('#sure-gar-a').setValue('1')
    await w.get('#sure-gar-b').setValue('1')
    await w.get('#sure-dk').setValue('90')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await w.get('#sure-gar-b').setValue('2')
    await w.get('#sure-dk').setValue('1441')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await w.get('#sure-dk').setValue('1440')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('sürüm çakışması (409) hata gösterir ve listeyi yeniler; silme onayla çalışır', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.intikalSuresiYaz).mockRejectedValue(
      new ApiHata(
        409,
        'VERSIYON_CAKISMASI',
        'Kayıt siz düzenlerken başka biri tarafından değiştirildi.',
      ),
    )
    vi.mocked(tanimApi.intikalSuresiSil).mockResolvedValue(undefined)
    const w = await kur()

    await w.get('[aria-label="Ankara – Eskişehir süresini düzenle"]').trigger('click')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('başka biri')
    expect(tanimApi.intikalSureleri).toHaveBeenCalledTimes(2)

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await w.get('[aria-label="Ankara – Eskişehir süresini sil"]').trigger('click')
    await flushPromises()
    expect(tanimApi.intikalSuresiSil).toHaveBeenCalledWith(1, 2)
    expect(w.text()).toContain('Süre silindi.')
  })
})
