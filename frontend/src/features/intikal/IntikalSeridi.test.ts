import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./intikalApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./intikalApi')>()),
  intikalApi: { liste: vi.fn(), ekle: vi.fn(), guncelle: vi.fn(), iptal: vi.fn() },
}))
vi.mock('@/features/makinist/makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/makinist/makinistApi')>()),
  makinistApi: { liste: vi.fn() },
}))
vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))

import { makinistApi } from '@/features/makinist/makinistApi'
import { tanimApi } from '@/features/tanim/tanimApi'
import IntikalSeridi from './IntikalSeridi.vue'
import { intikalApi, type Intikal } from './intikalApi'

const intikal = (ek: Partial<Intikal> = {}): Intikal => ({
  id: 1,
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  kalkisGarId: 1,
  kalkisGar: 'Ankara',
  varisGarId: 2,
  varisGar: 'Eskişehir',
  kalkis: '2026-10-14T07:00:00+03:00',
  varis: '2026-10-14T08:30:00+03:00',
  aracTuru: 'SERVIS',
  notu: null,
  durum: 'PLANLI',
  versiyon: 3,
  gordu: 'YENI',
  gorduZamani: null,
  kirmizi: false,
  nedenler: [],
  uyarilar: [],
  ...ek,
})

async function kur(liste: Intikal[] = [intikal()]) {
  vi.mocked(intikalApi.liste).mockResolvedValue(liste)
  const w = mount(IntikalSeridi, { props: { tarih: '2026-10-14' } })
  await flushPromises()
  return w
}

describe('IntikalSeridi (İ-1)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(makinistApi.liste).mockResolvedValue([
      { id: 7, sicil: 'D007', adSoyad: 'Ali Veli' },
    ] as never)
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'Eskişehir' },
    ])
  })

  it('günün intikalleri istenir; boşsa mesaj ve "İntikal ekle" düğmesi görünür', async () => {
    const w = await kur([])

    expect(intikalApi.liste).toHaveBeenCalledWith('2026-10-14', '2026-10-14')
    expect(w.get('[data-test="intikal-bos"]').text()).toContain('intikal yok')
    expect(w.find('[data-test="intikal-ekle"]').exists()).toBe(true)
  })

  it('satır: kişi, gar ve saatler, araç; gördü rozeti; kırmızı ise KONTROL ve nedenler; uyarılar renksiz metinle', async () => {
    const w = await kur([
      intikal({ gordu: 'GORULDU', gorduZamani: '2026-10-14T03:00:00Z', notu: 'kapı 3' }),
      intikal({
        id: 2,
        kirmizi: true,
        nedenler: ['Çakışma: 1 s örtüşüyor'],
        uyarilar: ['Yolculuktan sonra bu garda görev yok'],
        gordu: 'DEGISTI',
      }),
    ])
    const satirlar = w.findAll('[data-test="intikal"]')

    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('Ali Veli (D007)')
    expect(satirlar[0]!.text()).toContain('Ankara 07:00 → Eskişehir 08:30')
    expect(satirlar[0]!.text()).toContain('Servis')
    expect(satirlar[0]!.text()).toContain('kapı 3')
    expect(satirlar[0]!.find('[data-test="intikal-gordu"]').text()).toContain('gördü 06:00')
    expect(satirlar[0]!.find('[data-test="intikal-kirmizi"]').exists()).toBe(false)
    expect(satirlar[1]!.find('[data-test="intikal-kirmizi"]').text()).toContain('KONTROL')
    expect(satirlar[1]!.text()).toContain('Çakışma: 1 s örtüşüyor')
    expect(satirlar[1]!.text()).toContain('Uyarı: Yolculuktan sonra bu garda görev yok')
    expect(satirlar[1]!.find('[data-test="intikal-degisti"]').text()).toContain('yeniden onay')
  })

  it('iptal edilmiş satırda İPTAL etiketi var, Düzelt / İptal düğmesi yok', async () => {
    const w = await kur([intikal({ durum: 'IPTAL' })])

    expect(w.get('[data-test="intikal-iptal"]').text()).toContain('İPTAL')
    expect(w.find('[data-test="intikal-duzelt"]').exists()).toBe(false)
    expect(w.find('[data-test="intikal-iptal-et"]').exists()).toBe(false)
  })

  it('liste yüklenemezse hata gösterilir', async () => {
    vi.mocked(intikalApi.liste).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası'),
    )
    const w = mount(IntikalSeridi, { props: { tarih: '2026-10-14' } })
    await flushPromises()

    expect(w.get('[data-test="intikal-hata"]').text()).toContain('Sunucu hatası')
  })

  it('ekle: formu doldurur, İstanbul ofsetiyle gönderir, başarıda masaya "degisti" bildirir', async () => {
    vi.mocked(intikalApi.ekle).mockResolvedValue({ intikal: intikal(), yeniKirmizi: [] })
    const w = await kur([])

    await w.get('[data-test="intikal-ekle"]').trigger('click')
    await flushPromises()
    await w.get('#intikal-makinist').setValue('7')
    await w.get('#intikal-kalkis-gar').setValue('1')
    await w.get('#intikal-varis-gar').setValue('2')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(intikalApi.ekle).toHaveBeenCalledWith({
      makinistId: 7,
      kalkisGarId: 1,
      varisGarId: 2,
      kalkis: '2026-10-14T07:00:00+03:00',
      varis: '2026-10-14T08:00:00+03:00',
      aracTuru: 'SERVIS',
      notu: undefined,
      gerekce: undefined,
    })
    expect(w.emitted('degisti')).toHaveLength(1)
    expect(w.get('[data-test="intikal-bilgi"]').text()).toBe('İntikal eklendi.')
    expect(w.find('dialog').exists()).toBe(false)
  })

  it('ekle: eksik alan istemcide yakalanır (istek gitmez); aynı gar reddedilir', async () => {
    const w = await kur([])

    await w.get('[data-test="intikal-ekle"]').trigger('click')
    await flushPromises()
    await w.get('form').trigger('submit')
    await w.get('#intikal-makinist').setValue('7')
    await w.get('#intikal-kalkis-gar').setValue('1')
    await w.get('#intikal-varis-gar').setValue('1')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(intikalApi.ekle).not.toHaveBeenCalled()
    expect(w.text()).toContain('Varış garı kalkıştan farklı olmalı.')
  })

  it('ekle: kural ENGEL (422) ihlallerle diyalogda gösterilir, diyalog açık kalır; UYARI için gerekçe alan hatası görünür', async () => {
    const w = await kur([])
    await w.get('[data-test="intikal-ekle"]').trigger('click')
    await flushPromises()
    await w.get('#intikal-makinist').setValue('7')
    await w.get('#intikal-kalkis-gar').setValue('1')
    await w.get('#intikal-varis-gar').setValue('2')

    vi.mocked(intikalApi.ekle).mockRejectedValueOnce(
      new ApiHata(422, 'KURAL_IHLALI', 'Kural ihlali: D007 için bu intikal yazılamaz.', {
        ihlaller: [
          {
            kod: 'K-10',
            siddet: 'ENGEL',
            olculen: 1,
            esik: 0,
            birim: 's',
            metin: 'Çakışma: örtüşüyor',
          },
        ],
      }),
    )
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('Çakışma: örtüşüyor')
    expect(w.find('dialog').exists()).toBe(true)

    vi.mocked(intikalApi.ekle).mockRejectedValueOnce(
      new ApiHata(400, 'DOGRULAMA', 'Kural uyarısı var', {
        alanlar: { gerekce: 'Kural uyarısı var; intikal için gerekçe yazılmalı.' },
      }),
    )
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('gerekçe yazılmalı')
  })

  it('düzelt: kayıt forma dolar, makinist değişmez, sürümle güncellenir; yeni kırmızı bildirimi', async () => {
    vi.mocked(intikalApi.guncelle).mockResolvedValue({
      intikal: intikal(),
      yeniKirmizi: [{ atamaId: 5, seferId: 9, trenNo: 'S1', nedenler: ['Konum uyuşmazlığı'] }],
    })
    const w = await kur()

    await w.get('[data-test="intikal-duzelt"]').trigger('click')
    await flushPromises()
    expect((w.get('#intikal-makinist').element as HTMLSelectElement).disabled).toBe(true)
    expect((w.get('#intikal-kalkis').element as HTMLInputElement).value).toBe('2026-10-14T07:00')
    await w.get('#intikal-varis').setValue('2026-10-14T09:00')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(intikalApi.guncelle).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ makinistId: 7, varis: '2026-10-14T09:00:00+03:00', versiyon: 3 }),
    )
    expect(w.get('[data-test="intikal-bilgi"]').text()).toContain('1 başka görevi kırmızı oldu: S1')
  })

  it('iptal: neden zorunlu (DIGER için not); sürümle gönderilir; silme yok', async () => {
    vi.mocked(intikalApi.iptal).mockResolvedValue({
      intikal: intikal({ durum: 'IPTAL' }),
      yeniKirmizi: [],
    })
    const w = await kur()

    await w.get('[data-test="intikal-iptal-et"]').trigger('click')
    await flushPromises()
    await w.get('form').trigger('submit')
    expect(intikalApi.iptal).not.toHaveBeenCalled()
    expect(w.text()).toContain('Neden seçin.')

    await w.get('#intikal-neden').setValue('DIGER')
    await w.get('form').trigger('submit')
    expect(intikalApi.iptal).not.toHaveBeenCalled()
    await w.get('#intikal-neden-notu').setValue('servis iptal')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(intikalApi.iptal).toHaveBeenCalledWith(1, {
      neden: 'DIGER',
      nedenNotu: 'servis iptal',
      versiyon: 3,
    })
    expect(w.get('[data-test="intikal-bilgi"]').text()).toContain('İntikal iptal edildi.')
    expect(w.emitted('degisti')).toHaveLength(1)
  })

  it('gün değişince yeni gün istenir ve açık diyalog kapanır', async () => {
    const w = await kur()
    await w.get('[data-test="intikal-duzelt"]').trigger('click')
    await flushPromises()

    await w.setProps({ tarih: '2026-10-15' })
    await flushPromises()

    expect(intikalApi.liste).toHaveBeenLastCalledWith('2026-10-15', '2026-10-15')
    expect(w.find('dialog').exists()).toBe(false)
  })
})
