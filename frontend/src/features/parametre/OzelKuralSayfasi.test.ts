import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./ozelKuralApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./ozelKuralApi')>()),
  ozelKuralApi: {
    liste: vi.fn(),
    katalog: vi.fn(),
    ekle: vi.fn(),
    guncelle: vi.fn(),
    dene: vi.fn(),
  },
}))
vi.mock('../tanim/tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('../tanim/tanimApi')>()),
  tanimApi: { liste: vi.fn(async () => []), ekle: vi.fn(), adlandir: vi.fn() },
}))

import type { Rol } from '@/api/tipler'
import { kullanici } from '@/composables/oturum'
import OzelKuralSayfasi from './OzelKuralSayfasi.vue'
import { ozelKuralApi, type OlcuDto, type OzelKuralDto } from './ozelKuralApi'

const katalog: OlcuDto[] = [
  {
    kod: 'TOPLAM_GOREV_SURESI',
    ad: 'Toplam görev süresi',
    birim: 's',
    aciklama: 'Toplam süre.',
    pencereler: [
      { kod: 'GUN', ad: 'Gün', aciklama: 'Gün.', esikAlt: 0, esikAltDahil: false, esikUst: 24 },
    ],
    karsilastirmalar: [{ kod: 'EN_FAZLA', ad: 'en fazla', aciklama: 'Üst sınır.' }],
  },
]
const kural: OzelKuralDto = {
  id: 1,
  kod: 'Ö-001',
  ad: 'Sivas sınırı',
  olcu: 'TOPLAM_GOREV_SURESI',
  olcuAd: 'Toplam görev süresi',
  birim: 's',
  pencere: 'GUN',
  kayanGun: null,
  karsilastirma: 'EN_FAZLA',
  esik: 8,
  kapsam: {
    hatlar: [1],
    aracTipleri: [],
    kalkisGarlari: [],
    varisGarlari: [],
    trenTurleri: [],
    vardiya: 'HEPSI',
  },
  siddet: 'ENGEL',
  gecerlilikBas: '2026-10-12',
  gecerlilikBit: null,
  gerekce: 'TİS ek protokol',
  kaynak: 'md. 4',
  versiyon: 2,
  ozet: 'Toplam görev süresi (gün) en fazla 8 s · Hat: Ankara–Sivas',
  olusturan: 'a',
  olusturma: '2026-10-12T05:30:00Z',
  guncelleme: '2026-10-12T05:30:00Z',
}
const kapali: OzelKuralDto = { ...kural, id: 2, kod: 'Ö-002', siddet: 'KAPALI', kaynak: null }

async function kur(rol: Rol = 'YONETICI') {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
  const w = mount(OzelKuralSayfasi)
  await flushPromises()
  return w
}
const dugme = (w: Awaited<ReturnType<typeof kur>>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)

describe('OzelKuralSayfasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(ozelKuralApi.liste).mockResolvedValue([kural, kapali])
    vi.mocked(ozelKuralApi.katalog).mockResolvedValue(katalog)
  })

  it('satırlar sütunlarıyla görünür; açık diyalog yokken form yoktur', async () => {
    const w = await kur()
    const t = w.text()
    expect(t).toContain('Özel kurallar')
    expect(t).toContain('Ö-001')
    expect(t).toContain('Sivas sınırı')
    expect(t).toContain('Toplam görev süresi (gün) en fazla 8 s · Hat: Ankara–Sivas')
    expect(t).toContain('Engel')
    expect(t).toContain('Kapalı')
    expect(t).toContain('12.10.2026 – süresiz')
    expect(t).toContain('TİS ek protokol · md. 4')
    expect(w.find('form').exists()).toBe(false)
    expect(w.find('dialog').exists()).toBe(false)
  })

  it('boş durum ve yükleme hatası gösterilir', async () => {
    vi.mocked(ozelKuralApi.liste).mockResolvedValue([])
    const w = await kur()
    expect(w.text()).toContain('Henüz özel kural yok.')
    expect(dugme(w, 'Yeni kural')).toBeDefined()
    vi.mocked(ozelKuralApi.liste).mockRejectedValue(
      new ApiHata(500, 'HATA', 'Sunucu yanıt vermedi.'),
    )
    const h = await kur()
    expect(h.get('[role="alert"]').text()).toContain('Sunucu yanıt vermedi.')
  })

  it('ATAMACI yalnız okur: düğme ve eylem sütunu yok', async () => {
    const w = await kur('ATAMACI')
    expect(w.text()).toContain('Ö-001')
    expect(w.findAll('button')).toHaveLength(0)
    expect(w.text()).not.toContain('Eylemler')
  })

  it('ATAMACI boş listede de düğme görmez; yönetici boş durumda Yeni kural çağrısı görür', async () => {
    vi.mocked(ozelKuralApi.liste).mockResolvedValue([])
    const a = await kur('ATAMACI')
    expect(a.text()).toContain('Henüz özel kural yok.')
    expect(a.findAll('button')).toHaveLength(0)
    const y = await kur('YONETICI')
    expect(y.findAll('button').filter((b) => b.text() === 'Yeni kural')).toHaveLength(1)
  })

  it('YONETICI: Yeni kural diyaloğu açar; kaydedilince satır eklenir ve diyalog kapanır', async () => {
    const yeni = { ...kural, id: 3, kod: 'Ö-003', ad: 'Yeni kural adı' }
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(yeni)
    const w = await kur()
    await dugme(w, 'Yeni kural')!.trigger('click')
    await flushPromises()
    expect(w.find('dialog form').exists()).toBe(true)
    await w.get('#kk-ad').setValue('Yeni kural adı')
    await w.get('#kk-esik').setValue('8')
    await w.get('#kk-gerekce').setValue('Gerekçe')
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.text()).toContain('Yeni kural adı')
  })

  it('Düzenle seçili kuralla diyalog açar', async () => {
    const w = await kur()
    await w.get('[aria-label="Ö-001 kuralını düzenle"]').trigger('click')
    await flushPromises()
    expect((w.get('#kk-ad').element as HTMLInputElement).value).toBe('Sivas sınırı')
    expect(w.find('#kk-neden').exists()).toBe(true)
  })

  it('Kapat yalnız açık kurallarda var; neden zorunlu; PUT ile KAPALI yapar', async () => {
    vi.mocked(ozelKuralApi.guncelle).mockResolvedValue({ ...kural, siddet: 'KAPALI', versiyon: 3 })
    const w = await kur()
    expect(w.find('[aria-label="Ö-002 kuralını kapat"]').exists()).toBe(false)
    await w.get('[aria-label="Ö-001 kuralını kapat"]').trigger('click')
    expect(w.find('form').exists()).toBe(false)
    expect(dugme(w, 'Kapatmayı onayla')!.attributes('disabled')).toBeDefined()
    await w.get('#ozel-kapat-neden-1').setValue(' Protokol bitti ')
    expect(dugme(w, 'Kapatmayı onayla')!.attributes('disabled')).toBeUndefined()
    await dugme(w, 'Kapatmayı onayla')!.trigger('click')
    await flushPromises()
    expect(ozelKuralApi.guncelle).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        siddet: 'KAPALI',
        versiyon: 2,
        neden: 'Protokol bitti',
        ad: 'Sivas sınırı',
      }),
    )
    expect(w.find('#ozel-kapat-neden-1').exists()).toBe(false)
    expect(w.find('[aria-label="Ö-001 kuralını kapat"]').exists()).toBe(false)
  })

  it('Kapat hatası satır altında metinle gösterilir', async () => {
    vi.mocked(ozelKuralApi.guncelle).mockRejectedValue(
      new ApiHata(403, 'YETKISIZ', 'Yetkiniz yok.'),
    )
    const w = await kur()
    await w.get('[aria-label="Ö-001 kuralını kapat"]').trigger('click')
    await w.get('#ozel-kapat-neden-1').setValue('Neden')
    await dugme(w, 'Kapatmayı onayla')!.trigger('click')
    await flushPromises()
    expect(w.get('.kapat-alani [role="alert"]').text()).toBe('Yetkiniz yok.')
  })
})
