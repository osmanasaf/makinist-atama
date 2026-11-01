import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./tanimApi')>()),
  tanimApi: { resmiTatiller: vi.fn(), tatilEkle: vi.fn(), tatilSil: vi.fn() },
}))

import { kullanici } from '@/composables/oturum'
import type { Rol } from '@/api/tipler'
import ResmiTatiller from './ResmiTatiller.vue'
import { tanimApi } from './tanimApi'

const girisYap = (rol: Rol) => {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
}
async function kur() {
  const w = mount(ResmiTatiller)
  await flushPromises()
  return w
}

describe('ResmiTatiller (F5, MK-178): yalnız gösterim listesi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.resmiTatiller).mockResolvedValue([
      { tarih: '2026-01-01', ad: 'Yılbaşı' },
      { tarih: '2026-10-29', ad: 'Cumhuriyet Bayramı' },
    ])
  })

  it('personel (atamacı) salt okur: liste görünür, form ve silme düğmesi yok', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    const satirlar = w.findAll('[data-test="tatil-satiri"]')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[1]!.text()).toContain('29.10.2026')
    expect(satirlar[1]!.text()).toContain('Cumhuriyet Bayramı')
    expect(w.find('[data-test="tatil-formu"]').exists()).toBe(false)
    expect(w.findAll('button')).toHaveLength(0)
  })

  it('bilgi notu: yalnız gösterim, kuralları ve hesapları etkilemez; yönetici elle girer', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    expect(w.text()).toContain('yalnız gösterim')
    expect(w.text()).toContain('etkilemez')
  })

  it('açılışta bu yılın listesi istenir', async () => {
    girisYap('ATAMACI')
    await kur()
    expect(tanimApi.resmiTatiller).toHaveBeenCalledWith(new Date().getFullYear())
  })

  it('boş liste: mesaj', async () => {
    girisYap('ATAMACI')
    vi.mocked(tanimApi.resmiTatiller).mockResolvedValue([])
    expect((await kur()).text()).toContain('Tanımlı resmi tatil yok.')
  })

  it('yönetici ekler: tarih + ad gider, liste yenilenir, durum bildirilir', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.tatilEkle).mockResolvedValue({ tarih: '2026-10-30', ad: 'Ara tatil' })
    const w = await kur()
    await w.get('#tatil-tarih').setValue('2026-10-30')
    await w.get('#tatil-ad').setValue('  Ara tatil ')
    await w.get('[data-test="tatil-formu"]').trigger('submit')
    await flushPromises()
    expect(tanimApi.tatilEkle).toHaveBeenCalledWith({ tarih: '2026-10-30', ad: 'Ara tatil' })
    expect(w.text()).toContain('Resmi tatil eklendi.')
    expect(tanimApi.resmiTatiller).toHaveBeenCalledTimes(2)
  })

  it('tarih ya da ad boşken Ekle pasif; ad en çok 80 karakter', async () => {
    girisYap('YONETICI')
    const w = await kur()
    const ekle = () => w.get('[data-test="tatil-formu"] button[type="submit"]')
    expect(ekle().attributes('disabled')).toBeDefined()
    await w.get('#tatil-tarih').setValue('2026-10-30')
    expect(ekle().attributes('disabled')).toBeDefined()
    await w.get('#tatil-ad').setValue('Ara')
    expect(ekle().attributes('disabled')).toBeUndefined()
    expect(w.get('#tatil-ad').attributes('maxlength')).toBe('80')
  })

  it('tekrar eden tarih (409): sunucu mesajı alan altında/uyarıda görünür, form değerleri korunur', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.tatilEkle).mockRejectedValue(
      new ApiHata(409, 'TATIL_VAR', 'Bu tarih için resmi tatil zaten tanımlı.'),
    )
    const w = await kur()
    await w.get('#tatil-tarih').setValue('2026-10-29')
    await w.get('#tatil-ad').setValue('Çift')
    await w.get('[data-test="tatil-formu"]').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('zaten tanımlı')
    expect((w.get('#tatil-ad').element as HTMLInputElement).value).toBe('Çift')
  })

  it('yönetici siler: onaydan sonra tarih ile silinir ve liste yenilenir; vazgeçilirse işlem yok', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.tatilSil).mockResolvedValue(undefined)
    const w = await kur()
    const onay = vi.spyOn(window, 'confirm').mockReturnValue(false)
    await w.get('[aria-label="29.10.2026 Cumhuriyet Bayramı resmi tatilini sil"]').trigger('click')
    expect(tanimApi.tatilSil).not.toHaveBeenCalled()
    onay.mockReturnValue(true)
    await w.get('[aria-label="29.10.2026 Cumhuriyet Bayramı resmi tatilini sil"]').trigger('click')
    await flushPromises()
    expect(tanimApi.tatilSil).toHaveBeenCalledWith('2026-10-29')
    expect(w.text()).toContain('Resmi tatil silindi.')
    expect(tanimApi.resmiTatiller).toHaveBeenCalledTimes(2)
    onay.mockRestore()
  })

  it('yıl değişince o yılın listesi istenir', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    await w.get('#tatil-yil').setValue('2027')
    await flushPromises()
    expect(tanimApi.resmiTatiller).toHaveBeenLastCalledWith(2027)
  })

  it('liste yüklenemezse hata görünür (sayfa bozulmaz)', async () => {
    girisYap('ATAMACI')
    vi.mocked(tanimApi.resmiTatiller).mockRejectedValue(new ApiHata(500, 'X', 'Sunucu hatası'))
    expect((await kur()).text()).toContain('Sunucu hatası')
  })
})
