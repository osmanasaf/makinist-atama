import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { bol: vi.fn() },
}))
vi.mock('@/features/sefer/seferApi', () => ({ seferApi: { getir: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))

import SeferBolDialog from './SeferBolDialog.vue'
import { atamaApi, type BolSonucu } from './atamaApi'
import { seferApi, type Sefer } from '@/features/sefer/seferApi'
import { tanimApi, type TanimTipi } from '@/features/tanim/tanimApi'

const sefer = (ek: Partial<Sefer> = {}): Sefer => ({
  id: 11,
  trenNo: 'DE-1503',
  tarih: '2026-10-20',
  kalkisGarId: 1,
  kalkisGar: 'Ankara',
  varisGarId: 3,
  varisGar: 'Kars',
  planlananKalkis: '2026-10-20T05:00:00Z',
  planlananVaris: '2026-10-20T20:00:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  guncelKalkis: '2026-10-20T05:00:00Z',
  guncelVaris: '2026-10-20T20:00:00Z',
  aracTipiId: 9,
  aracTipi: 'DE',
  hatId: 7,
  hat: 'Doğu',
  km: 1000,
  durum: 'PLANLI',
  versiyon: 4,
  ...ek,
})

const TANIMLAR: Record<TanimTipi, { id: number; tip: TanimTipi; ad: string }[]> = {
  GAR: [
    { id: 1, tip: 'GAR', ad: 'Ankara' },
    { id: 2, tip: 'GAR', ad: 'Sivas' },
    { id: 3, tip: 'GAR', ad: 'Kars' },
    { id: 4, tip: 'GAR', ad: 'Kayseri' },
  ],
  HAT: [{ id: 7, tip: 'HAT', ad: 'Doğu' }],
  ARAC_TIPI: [{ id: 9, tip: 'ARAC_TIPI', ad: 'DE' }],
}

const sonuc = (ek: Partial<BolSonucu> = {}): BolSonucu => ({
  parcalar: [
    sefer({
      parcaNo: 1,
      parcaSayisi: 2,
      kalkisGar: 'Ankara',
      varisGar: 'Sivas',
      guncelKalkis: '2026-10-20T05:00:00Z', // İstanbul 08:00
      guncelVaris: '2026-10-20T10:30:00Z', // 13:30
      km: 603,
    }),
    sefer({
      id: 12,
      parcaNo: 2,
      parcaSayisi: 2,
      kalkisGar: 'Sivas',
      varisGar: 'Kars',
      guncelKalkis: '2026-10-20T10:45:00Z', // 13:45
      guncelVaris: '2026-10-20T20:00:00Z', // 23:00
      km: 397,
    }),
  ],
  yeniKirmizi: [],
  ...ek,
})

async function kur(seferEk: Partial<Sefer> = {}) {
  vi.mocked(seferApi.getir).mockResolvedValue(sefer(seferEk))
  vi.mocked(tanimApi.liste).mockImplementation((tip) => Promise.resolve(TANIMLAR[tip]))
  const w = mount(SeferBolDialog, {
    props: { seferId: 11, seferEtiketi: 'DE-1503 Ankara – Kars 08:00' },
  })
  await flushPromises()
  return w
}
type W = Awaited<ReturnType<typeof kur>>
const dugme = (w: W, metin: string) => w.findAll('button').find((b) => b.text() === metin)!
const doldur = async (w: W, i = 0, km = '603') => {
  await w.get(`#bol-gar-${i}`).setValue('2')
  await w.get(`#bol-varis-${i}`).setValue('13:30')
  await w.get(`#bol-kalkis-${i}`).setValue('13:45')
  if (km) await w.get(`#bol-km-${i}`).setValue(km)
}
const gonder = async (w: W) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}
const satirMetinleri = (w: W) =>
  w.findAll('table tbody tr').map((r) =>
    r
      .findAll('td')
      .map((h) => h.text())
      .join(' '),
  )

describe('SeferBolDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('açılışta seferi ve tanımları okur; bir ara istasyonla başlar; seferin uç garları listede yok', async () => {
    const w = await kur()
    expect(seferApi.getir).toHaveBeenCalledWith(11)
    expect(w.text()).toContain('DE-1503 Ankara – Kars 08:00')
    expect(w.findAll('fieldset')).toHaveLength(1)
    const garlar = w.findAll('#bol-gar-0 option').map((o) => o.text())
    expect(garlar).toEqual(['Seçin…', 'Sivas', 'Kayseri'])
    expect(w.find('label[for="bol-varis-0"]').exists()).toBe(true)
    expect(w.find('label[for="bol-kalkis-0"]').exists()).toBe(true)
  })

  it('nokta ekle/çıkar: en çok 5, en az 1', async () => {
    const w = await kur()
    expect(dugme(w, 'Noktayı çıkar').attributes('disabled')).toBeDefined()
    for (let i = 0; i < 4; i++) await dugme(w, '+ Nokta ekle').trigger('click')
    expect(w.findAll('fieldset')).toHaveLength(5)
    expect(dugme(w, '+ Nokta ekle').attributes('disabled')).toBeDefined()
    await w
      .findAll('button')
      .filter((b) => b.text() === 'Noktayı çıkar')[4]!
      .trigger('click')
    expect(w.findAll('fieldset')).toHaveLength(4)
    expect(dugme(w, '+ Nokta ekle').attributes('disabled')).toBeUndefined()
  })

  it('km alanı yalnız seferin km değeri doluysa görünür', async () => {
    const dolu = await kur()
    expect(dolu.find('#bol-km-0').exists()).toBe(true)
    const bos = await kur({ km: null })
    expect(bos.find('#bol-km-0').exists()).toBe(false)
  })

  it('alanlar dolmadan Önizle kapalı; doluyken gövde onizle:true ile gider, km yoksa gönderilmez', async () => {
    vi.mocked(atamaApi.bol).mockResolvedValue(sonuc())
    const w = await kur({ km: null })
    expect(dugme(w, 'Önizle').attributes('disabled')).toBeDefined()
    await doldur(w, 0, '')
    await w.get('#bol-hat-0').setValue('7')
    await w.get('#bol-arac-0').setValue('9')
    expect(dugme(w, 'Önizle').attributes('disabled')).toBeUndefined()
    await gonder(w)
    expect(atamaApi.bol).toHaveBeenCalledWith(11, {
      versiyon: 4,
      noktalar: [{ garId: 2, varisSaat: '13:30', kalkisSaat: '13:45', hatId: 7, aracTipiId: 9 }],
      onizle: true,
    })
  })

  it('km dolu seferde nokta km zorunlu ve gövdeye sayı olarak girer', async () => {
    vi.mocked(atamaApi.bol).mockResolvedValue(sonuc())
    const w = await kur()
    await doldur(w, 0, '')
    expect(dugme(w, 'Önizle').attributes('disabled')).toBeDefined()
    await w.get('#bol-km-0').setValue('603')
    await gonder(w)
    expect(atamaApi.bol).toHaveBeenCalledWith(11, {
      versiyon: 4,
      noktalar: [{ garId: 2, varisSaat: '13:30', kalkisSaat: '13:45', km: 603 }],
      onizle: true,
    })
  })

  it('Önizle: sunucu 400 mesajı diyalogda gösterilir, tablo çıkmaz', async () => {
    vi.mocked(atamaApi.bol).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', '1. ara istasyon: varış saati seferin penceresi dışında.'),
    )
    const w = await kur()
    await doldur(w)
    await gonder(w)
    expect(w.get('[role="alert"]').text()).toBe(
      '1. ara istasyon: varış saati seferin penceresi dışında.',
    )
    expect(w.find('table').exists()).toBe(false)
    expect(w.emitted('tamam')).toBeUndefined()
  })

  it('önizleme: parça tablosu, uyarı metni ve yeniKirmizi listesi; yazma yok', async () => {
    vi.mocked(atamaApi.bol).mockResolvedValue(
      sonuc({
        yeniKirmizi: [
          { atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme yetersiz'] },
        ],
      }),
    )
    const w = await kur()
    await doldur(w)
    await gonder(w)
    expect(satirMetinleri(w)).toEqual([
      '1/2 Ankara→Sivas 08:00–13:30 5s30dk 603 km',
      '2/2 Sivas→Kars 13:45–23:00 9s15dk 397 km',
    ])
    expect(w.text()).toContain(
      'Mevcut atamalar 1. parçada kalır; 2. parça boş açılır; bu seferdeki açık takas/bırakma talepleri geçersiz olur',
    )
    const kirmizi = w.get('[data-test="yeni-kirmizi"]').text()
    expect(kirmizi).toContain('T1030')
    expect(kirmizi).toContain('dinlenme yetersiz')
    expect(atamaApi.bol).toHaveBeenCalledTimes(1)
    expect(w.emitted('tamam')).toBeUndefined()
  })

  it('Geri: forma döner, girilen değerler korunur', async () => {
    vi.mocked(atamaApi.bol).mockResolvedValue(sonuc())
    const w = await kur()
    await doldur(w)
    await gonder(w)
    await dugme(w, 'Geri').trigger('click')
    expect(w.find('table').exists()).toBe(false)
    expect((w.get('#bol-varis-0').element as HTMLInputElement).value).toBe('13:30')
    expect((w.get('#bol-gar-0').element as HTMLSelectElement).value).toBe('2')
  })

  it('Böl: aynı gövdeyle onizle:false gider; başarıda tamam(mesaj), yeniKirmizi mesajda', async () => {
    vi.mocked(atamaApi.bol)
      .mockResolvedValueOnce(sonuc())
      .mockResolvedValueOnce(
        sonuc({
          yeniKirmizi: [{ atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme'] }],
        }),
      )
    const w = await kur()
    await doldur(w)
    await gonder(w) // önizle
    await gonder(w) // böl
    expect(atamaApi.bol).toHaveBeenLastCalledWith(11, {
      versiyon: 4,
      noktalar: [{ garId: 2, varisSaat: '13:30', kalkisSaat: '13:45', km: 603 }],
      onizle: false,
    })
    const mesaj = (w.emitted('tamam') as string[][])[0]![0]!
    expect(mesaj).toContain('Sefer 2 parçaya bölündü.')
    expect(mesaj).toContain('T1030')
  })

  it.each([
    [409, 'SEFER_ZATEN_PARCALI', 'Sefer zaten parçalı.'],
    [409, 'VERSIYON_CAKISMASI', 'Sefer başka biri tarafından değiştirildi.'],
    [422, 'SEFER_BASLAMIS', 'Başlamış sefer bölünemez.'],
  ])(
    'Böl: %i %s diyalogda gösterilir, tamam yayınlanmaz, vazgeçince yenile=true',
    async (durum, kod, detay) => {
      vi.mocked(atamaApi.bol)
        .mockResolvedValueOnce(sonuc())
        .mockRejectedValueOnce(new ApiHata(durum, kod, detay))
      const w = await kur()
      await doldur(w)
      await gonder(w)
      await gonder(w)
      expect(w.get('[role="alert"]').text()).toBe(detay)
      expect(w.emitted('tamam')).toBeUndefined()
      await dugme(w, 'Vazgeç').trigger('click')
      expect(w.emitted('kapat')).toEqual([[true]])
    },
  )

  it('hatasız vazgeçince kapat(false); sefer okunamazsa mesaj gösterilir ve Önizle kapalı kalır', async () => {
    const w = await kur()
    await dugme(w, 'Vazgeç').trigger('click')
    expect(w.emitted('kapat')).toEqual([[false]])

    vi.resetAllMocks()
    vi.mocked(seferApi.getir).mockRejectedValue(new ApiHata(404, 'BULUNAMADI', 'Sefer yok.'))
    vi.mocked(tanimApi.liste).mockResolvedValue([])
    const hatali = mount(SeferBolDialog, { props: { seferId: 11, seferEtiketi: 'x' } })
    await flushPromises()
    expect(hatali.get('[role="alert"]').text()).toBe('Sefer yok.')
    expect(dugme(hatali, 'Önizle').attributes('disabled')).toBeDefined()
  })
})
