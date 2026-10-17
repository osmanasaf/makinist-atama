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
  tanimApi: { liste: vi.fn(), ekle: vi.fn(), adlandir: vi.fn() },
}))

import { tanimApi } from '../tanim/tanimApi'
import KuralKurucuDialog from './KuralKurucuDialog.vue'
import { ozelKuralApi, type DeneSonucu, type OlcuDto, type OzelKuralDto } from './ozelKuralApi'

const katalog: OlcuDto[] = [
  {
    kod: 'TOPLAM_GOREV_SURESI',
    ad: 'Toplam görev süresi',
    birim: 's',
    aciklama: 'Pencere içindeki tüm görevlerin toplam süresi.',
    pencereler: [
      {
        kod: 'GUN',
        ad: 'Gün',
        aciklama: 'Takvim gününün içindeki görevler.',
        esikAlt: 0,
        esikAltDahil: false,
        esikUst: 24,
      },
      {
        kod: 'AY',
        ad: 'Ay',
        aciklama: 'Takvim ayının içindeki görevler.',
        esikAlt: 0,
        esikAltDahil: false,
        esikUst: 744,
      },
      {
        kod: 'KAYAN',
        ad: 'Kayan pencere',
        aciklama: 'Her günün öncesindeki N gün birlikte sayılır.',
        esikAlt: 0,
        esikAltDahil: false,
        esikUst: 744,
      },
    ],
    karsilastirmalar: [{ kod: 'EN_FAZLA', ad: 'en fazla', aciklama: 'Toplam bu değeri aşamaz.' }],
  },
  {
    kod: 'TEK_SEFER_SURESI',
    ad: 'Tek sefer süresi',
    birim: 's',
    aciklama: 'Tek bir seferin süresi.',
    pencereler: [
      {
        kod: 'SEFER',
        ad: 'Sefer',
        aciklama: 'Her sefer ayrı sayılır.',
        esikAlt: 0,
        esikAltDahil: false,
        esikUst: 24,
      },
    ],
    karsilastirmalar: [{ kod: 'EN_FAZLA', ad: 'en fazla', aciklama: 'Süre bu değeri aşamaz.' }],
  },
  {
    kod: 'GOREV_SAYISI',
    ad: 'Görev sayısı',
    birim: 'adet',
    aciklama: 'Pencere içindeki görev adedi.',
    pencereler: [
      {
        kod: 'GUN',
        ad: 'Gün',
        aciklama: 'Takvim gününün içindeki görevler.',
        esikAlt: 1,
        esikAltDahil: true,
        esikUst: 10,
      },
    ],
    karsilastirmalar: [{ kod: 'EN_FAZLA', ad: 'en fazla', aciklama: 'Adet bu değeri aşamaz.' }],
  },
  {
    kod: 'IZIN_GUNU',
    ad: 'İzin günü',
    birim: 'gün',
    aciklama: 'Pencere içindeki izin günü sayısı.',
    pencereler: [
      {
        kod: 'AY',
        ad: 'Ay',
        aciklama: 'Takvim ayı.',
        esikAlt: 0,
        esikAltDahil: true,
        esikUst: 31,
      },
    ],
    karsilastirmalar: [
      { kod: 'EN_AZ', ad: 'en az', aciklama: 'İzin günü bu değerin altına düşemez.' },
    ],
  },
  {
    kod: 'YENI_OLCU',
    ad: 'Yeni ölçü',
    birim: 's',
    aciklama: 'Henüz bilinmeyen ölçü.',
    pencereler: [
      { kod: 'GUN', ad: 'Gün', aciklama: 'Gün.', esikAlt: 0, esikAltDahil: false, esikUst: 24 },
    ],
    karsilastirmalar: [
      { kod: 'EN_FAZLA', ad: 'en fazla', aciklama: 'Üst sınır.' },
      { kod: 'EN_AZ', ad: 'en az', aciklama: 'Alt sınır.' },
    ],
  },
]

const mevcut: OzelKuralDto = {
  id: 4,
  kod: 'Ö-004',
  ad: 'Sivas hattı sınırı',
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
    vardiya: 'GECE',
  },
  siddet: 'UYARI',
  gecerlilikBas: '2026-10-12',
  gecerlilikBit: null,
  gerekce: 'TİS ek protokol md. 4',
  kaynak: 'TİS',
  versiyon: 3,
  ozet: 'özet',
  olusturan: 'a',
  olusturma: '2026-10-12T05:30:00Z',
  guncelleme: '2026-10-12T05:30:00Z',
}

const sonuc = (kirmizi: number, uyari = 0): DeneSonucu => ({
  bas: '2026-10-12',
  bit: '2026-12-12',
  toplamAtama: 40,
  kirmiziAtama: kirmizi,
  kirmiziMakinist: kirmizi > 0 ? 2 : 0,
  uyariAtama: uyari,
  uyariMakinist: uyari > 0 ? 1 : 0,
  ornekler:
    kirmizi > 0
      ? [
          {
            sicil: 'D007',
            tarih: '2026-11-03',
            trenNo: '1001',
            durum: 'KIRMIZI',
            metin: 'Günlük 9 s > 8 s',
          },
        ]
      : [],
})

function kur(kural?: OzelKuralDto) {
  return mount(KuralKurucuDialog, { props: { katalog, kural } })
}

/** Zorunlu alanları doldurur. */
async function doldur(w: ReturnType<typeof kur>) {
  await w.get('#kk-ad').setValue('  Gece sınırı ')
  await w.get('#kk-esik').setValue('8')
  await w.get('#kk-gerekce').setValue(' Protokol ')
}
const kaydetDugmesi = (w: ReturnType<typeof kur>) => w.get('button[type="submit"]')
const dugme = (w: ReturnType<typeof kur>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)

describe('KuralKurucuDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockImplementation(async (tip) => [
      { id: tip === 'HAT' ? 1 : 2, tip, ad: tip === 'HAT' ? 'Ankara–Sivas' : 'Ankara' },
    ])
  })

  it('ölçü seçilince pencere listesi o ölçünün pencereleriyle sınırlanır', async () => {
    const w = kur()
    await flushPromises()
    const secenekler = () => w.findAll('#kk-pencere option').map((o) => o.attributes('value'))
    expect(secenekler()).toEqual(['GUN', 'AY', 'KAYAN'])
    await w.get('#kk-pencere').setValue('AY')
    await w.get('#kk-olcu').setValue('TEK_SEFER_SURESI')
    expect(secenekler()).toEqual(['SEFER'])
    expect((w.get('#kk-pencere').element as HTMLSelectElement).value).toBe('SEFER')
  })

  it('eşik ipucu ve birim katalogdan türer', async () => {
    const w = kur()
    await flushPromises()
    expect(w.get('#kk-esik-ipucu').text()).toContain('0 değerinden büyük, en çok 24 s')
    await w.get('#kk-olcu').setValue('GOREV_SAYISI')
    expect(w.get('#kk-esik-ipucu').text()).toContain('en az 1, en çok 10 adet')
    expect(w.get('.kosul').text()).toContain('adet')
  })

  it('seçili ölçü, pencere ve karşılaştırmanın açıklaması katalogdan görünür', async () => {
    const w = kur()
    await flushPromises()
    expect(w.text()).toContain('Pencere içindeki tüm görevlerin toplam süresi.')
    expect(w.text()).toContain('Takvim gününün içindeki görevler.')
    expect(w.text()).toContain('Toplam bu değeri aşamaz.')
    await w.get('#kk-olcu').setValue('IZIN_GUNU')
    expect(w.text()).toContain('Pencere içindeki izin günü sayısı.')
    expect(w.text()).toContain('İzin günü bu değerin altına düşemez.')
    expect(w.text()).not.toContain('Toplam bu değeri aşamaz.')
  })

  it('şiddet seçeneklerinin yanında sabit açıklama vardır', async () => {
    const t = kur().text()
    expect(t).toContain('Atama yapılamaz; mevcut atamalar kırmızı görünür.')
    expect(t).toContain(
      'Atama yapılabilir ama gerekçe istenir; otomatik atama bu seferi uyarılı adaya vermez.',
    )
    expect(t).toContain('Kural kayıtlı kalır ama hiç değerlendirilmez.')
  })

  it('izin günü ölçüsünde yardım metni görünür, diğerlerinde görünmez', async () => {
    const w = kur()
    await flushPromises()
    const yardim =
      'Boş günler izin sayılır; ayın başında engellemez, eşiğin altına düşüren ilk atamada engeller.'
    expect(w.text()).not.toContain(yardim)
    await w.get('#kk-olcu').setValue('IZIN_GUNU')
    expect(w.text()).toContain(yardim)
  })

  it('ölçü değişince geçersiz karşılaştırma/pencere seçili kalmaz; birim etikete yansır', async () => {
    const w = kur()
    await flushPromises()
    const kosul = 'select[aria-label="Koşul türü"]'
    await w.get('#kk-olcu').setValue('IZIN_GUNU')
    expect(w.findAll(`${kosul} option`).map((o) => o.attributes('value'))).toEqual(['EN_AZ'])
    expect((w.get(kosul).element as HTMLSelectElement).value).toBe('EN_AZ')
    expect(w.get('label[for="kk-esik"]').text()).toBe('Koşul (gün)')
    expect(w.get('.kosul').text()).toContain('gün')
    await w.get('#kk-olcu').setValue('TEK_SEFER_SURESI')
    expect(w.get('label[for="kk-esik"]').text()).toBe('Koşul (saat)')
    // Katalogda iki karşılaştırması olan (bilinmeyen koddaki) ölçü de seçilebilir.
    await w.get('#kk-olcu').setValue('YENI_OLCU')
    expect(w.findAll(`${kosul} option`)).toHaveLength(2)
  })

  it('KAYAN pencerede Kaç gün alanı çıkar, 2–366 dışında Kaydet kapalı, gövdeye kayanGun gider', async () => {
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    expect(w.find('#kk-kayan').exists()).toBe(false)
    await w.get('#kk-pencere').setValue('KAYAN')
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    for (const kotu of ['', '1', '367', '7,5', 'x']) {
      await w.get('#kk-kayan').setValue(kotu)
      expect(kaydetDugmesi(w).attributes('disabled')).toBeDefined()
      expect(w.text()).toContain('Kaç gün 2 ile 366 arasında tam sayı olmalı')
    }
    await w.get('#kk-kayan').setValue('7')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(ozelKuralApi.ekle).mock.calls[0]![0]).toMatchObject({
      pencere: 'KAYAN',
      kayanGun: 7,
    })
  })

  it('KAYAN dışında kayanGun null gider (eski değer sızmaz)', async () => {
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    await w.get('#kk-pencere').setValue('KAYAN')
    await w.get('#kk-kayan').setValue('7')
    await w.get('#kk-pencere').setValue('GUN')
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(ozelKuralApi.ekle).mock.calls[0]![0].kayanGun).toBeNull()
  })

  it('düzenlemede kayanGun dolu gelir; sunucu kayanGun hatası alanın yanında görünür', async () => {
    vi.mocked(ozelKuralApi.guncelle).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Geçersiz', { alanlar: { kayanGun: 'Gün sayısı geçersiz.' } }),
    )
    const w = kur({ ...mevcut, pencere: 'KAYAN', kayanGun: 14 })
    await flushPromises()
    expect((w.get('#kk-kayan').element as HTMLInputElement).value).toBe('14')
    await w.get('#kk-neden').setValue('Neden')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Gün sayısı geçersiz.')
  })

  it('tren türü çoklu seçimi gövdeye gider; boşsa boş dizi', async () => {
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(ozelKuralApi.ekle).mock.calls[0]![0].kapsam!.trenTurleri).toEqual([])
    const w2 = kur()
    await flushPromises()
    await doldur(w2)
    await w2.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    const tur = w2.findAll('fieldset').find((f) => f.find('legend').text() === 'Tren türü')!
    expect(tur.text()).toContain('Yük')
    expect(tur.text()).toContain('Yolcu')
    await tur.findAll('input')[0]!.setValue(true) // Yük
    await tur.findAll('input')[1]!.setValue(true) // Yolcu
    await w2.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(ozelKuralApi.ekle).mock.calls[1]![0].kapsam!.trenTurleri).toEqual([
      'YUK',
      'YOLCU',
    ])
  })

  it('kapsama girmeyen hatlar notu yalnız en az bir hat seçiliyse çıkar', async () => {
    vi.mocked(tanimApi.liste).mockImplementation(async (tip) =>
      tip === 'HAT'
        ? [
            { id: 1, tip, ad: 'Ankara–Sivas' },
            { id: 3, tip, ad: 'Ankara–Kayseri' },
            { id: 4, tip, ad: 'Sivas–Kars' },
          ]
        : [],
    )
    const w = kur()
    await flushPromises()
    expect(w.text()).not.toContain('Kapsama girmeyen')
    await w.get('input[type="checkbox"]').setValue(true) // Ankara–Sivas
    expect(w.text()).toContain('Kapsama girmeyen hatlar: Ankara–Kayseri, Sivas–Kars')
  })

  it('canlı özet cümlesi ölçü, pencere, koşul, eşik, birim ve kapsamı yansıtır', async () => {
    const w = kur()
    await flushPromises()
    const ozet = () => w.get('[aria-label="Kural özeti"]').text()
    expect(ozet()).toContain('Toplam görev süresi (gün) en fazla ? s')
    await w.get('#kk-esik').setValue('5,5')
    expect(ozet()).toContain('Toplam görev süresi (gün) en fazla 5,5 s')
    await w.get('input[type="checkbox"]').setValue(true) // Ankara–Sivas
    await w.get('input[name="kk-vardiya"][value="GECE"]').setValue()
    expect(ozet()).toContain('Hat: Ankara–Sivas')
    expect(ozet()).toContain('Vardiya: Yalnız gece')
    await w.get('#kk-pencere').setValue('KAYAN')
    await w.get('#kk-kayan').setValue('7')
    expect(ozet()).toContain('(kayan 7 gün)')
    await w.get('#kk-olcu').setValue('GOREV_SAYISI')
    await w.get('#kk-esik').setValue('3')
    expect(ozet()).toContain('Görev sayısı (gün) en fazla 3 adet')
  })

  it('pasif Dene ve Kaydet yanında nedenini yazar; sorun giderilince ipucu kalkar', async () => {
    const w = kur()
    await flushPromises()
    expect(w.get('#kk-dene-ipucu').text()).toBe('Kural adı boş olamaz')
    await w.get('#kk-ad').setValue('Kural')
    expect(w.get('#kk-dene-ipucu').text()).toBe('Koşul değeri sayı olmalı')
    await w.get('#kk-esik').setValue('abc')
    expect(w.get('#kk-kaydet-ipucu').text()).toBe('Koşul değeri sayı olmalı')
    await w.get('#kk-esik').setValue('8')
    expect(w.get('#kk-dene-ipucu').text()).toBe('Gerekçe yazılmalı')
    await w.get('#kk-gerekce').setValue('Protokol')
    expect(w.find('#kk-dene-ipucu').exists()).toBe(false)
    expect(w.find('#kk-kaydet-ipucu').exists()).toBe(false)
  })

  it('düzenlemede neden boşken Kaydet ipucu nedeni söyler', async () => {
    const w = kur(mevcut)
    await flushPromises()
    expect(w.find('#kk-dene-ipucu').exists()).toBe(false)
    expect(w.get('#kk-kaydet-ipucu').text()).toBe('Değişiklik nedeni yazılmalı')
  })

  // QA UX: planlama jargonu (pencere, seri) yerine sade Türkçe; kapsamın nasıl birleştiği ve geçerlilik tek cümleyle açıklanır.
  it('sade dil: "Zaman aralığı" etiketi, kapsam birleşimi (veya/ve) ve geçerlilik açıklaması', async () => {
    const w = kur()
    await flushPromises()
    expect(w.get('label[for="kk-pencere"]').text()).toBe('Zaman aralığı')
    expect(w.text()).toContain('Aynı alanda birden çok seçerseniz biri yeter ("veya")')
    expect(w.text()).toContain('farklı alanlarda seçtiklerinizin hepsi birlikte aranır ("ve")')
    expect(w.get('#kk-tarih-ipucu').text()).toContain(
      'başlangıçtan önceki (ve bitişten sonraki) görevler toplamlara',
    )
    expect(w.text()).not.toContain('ileri tarihli atamalar toplamı görmez') // ay/yıl için artık doğru değil: ileri yükleme tam
  })

  it('gerekçe ya da ad boşken Kaydet kapalı, doldurulunca açık', async () => {
    const w = kur()
    await flushPromises()
    await w.get('#kk-ad').setValue('Kural')
    await w.get('#kk-esik').setValue('8')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeDefined()
    await w.get('#kk-gerekce').setValue('   ')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeDefined()
    await w.get('#kk-gerekce').setValue('Protokol')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeUndefined()
  })

  it('UYARI seçiliyken dene çağrılmadan kaydedilir; gövde ve olaylar doğrudur', async () => {
    const kayit = { ...mevcut, id: 9 }
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(kayit)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('input[type="checkbox"]').setValue(true) // Ankara–Sivas hattı
    await w.get('input[name="kk-vardiya"][value="GECE"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(ozelKuralApi.dene).not.toHaveBeenCalled()
    const govde = vi.mocked(ozelKuralApi.ekle).mock.calls[0]![0]
    expect(govde).toMatchObject({
      ad: 'Gece sınırı',
      olcu: 'TOPLAM_GOREV_SURESI',
      pencere: 'GUN',
      karsilastirma: 'EN_FAZLA',
      esik: 8,
      siddet: 'UYARI',
      gerekce: 'Protokol',
      gecerlilikBit: null,
      kapsam: { hatlar: [1], aracTipleri: [], trenTurleri: [], vardiya: 'GECE' },
    })
    expect(govde).not.toHaveProperty('versiyon')
    expect(govde).not.toHaveProperty('neden')
    expect(w.emitted('kaydedildi')).toEqual([[kayit]])
    expect(w.emitted('kapat')).toHaveLength(1)
  })

  it('KAPALI seçiliyken de dene zorunlu değil', async () => {
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="KAPALI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(ozelKuralApi.dene).not.toHaveBeenCalled()
    expect(ozelKuralApi.ekle).toHaveBeenCalledTimes(1)
  })

  it('Dene: kırmızı ve uyarı sayıları ile örnekler görünür', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(3, 2))
    const w = kur()
    await flushPromises()
    await doldur(w)
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    const durum = w.get('[role="status"]').text()
    expect(durum).toContain('Bu kural kaydedilirse 3 atama kırmızı olur (2 personel).')
    expect(durum).toContain('2 atama uyarı alır.')
    expect(durum).toContain('D007')
    expect(durum).toContain('03.11.2026')
    expect(durum).toContain('Günlük 9 s > 8 s')
    expect(ozelKuralApi.dene).toHaveBeenCalledWith(expect.objectContaining({ esik: 8 }), undefined)
  })

  it('Dene: 10 örnek doluysa "İlk 10 örnek", azsa "Örnekler" yazar', async () => {
    const on = sonuc(12)
    on.ornekler = Array.from({ length: 10 }, (_, i) => ({
      ...on.ornekler[0]!,
      sicil: `D0${10 + i}`,
    }))
    vi.mocked(ozelKuralApi.dene).mockResolvedValueOnce(on)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toContain('İlk 10 örnek')
    expect(w.findAll('ul[aria-label="Örnekler"] li')).toHaveLength(10)
    vi.mocked(ozelKuralApi.dene).mockResolvedValueOnce(sonuc(3))
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toContain('Örnekler (')
    expect(w.get('[role="status"]').text()).not.toContain('İlk 10')
  })

  it('Dene: sayı 0 ise etkilenmediği yazar; hata metinle gösterilir', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValueOnce(sonuc(0))
    const w = kur()
    await flushPromises()
    await doldur(w)
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toContain('Mevcut atamalardan hiçbiri etkilenmez.')
    vi.mocked(ozelKuralApi.dene).mockRejectedValueOnce(new ApiHata(500, 'HATA', 'Hesaplanamadı.'))
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Hesaplanamadı.')
    expect(w.get('[role="status"]').text()).toBe('')
  })

  it('form değişince eski dene sonucu silinir', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(3))
    const w = kur()
    await flushPromises()
    await doldur(w)
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toContain('3 atama')
    await w.get('#kk-esik').setValue('9')
    expect(w.get('[role="status"]').text()).toBe('')
  })

  it('ENGEL + kırmızı atama varsa önce onay sorulur; onaylanınca kaydedilir', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(3))
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(ozelKuralApi.dene).toHaveBeenCalledTimes(1)
    expect(ozelKuralApi.ekle).not.toHaveBeenCalled()
    expect(w.text()).toContain('3 atama kırmızı olacak, devam edilsin mi?')
    await dugme(w, 'Evet, kaydet')!.trigger('click')
    await flushPromises()
    expect(ozelKuralApi.ekle).toHaveBeenCalledTimes(1)
    expect(w.emitted('kaydedildi')).toHaveLength(1)
  })

  it('ENGEL + onay satırında Vazgeç kaydetmez', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(3))
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('form').trigger('submit')
    await flushPromises()
    await dugme(w, 'Vazgeç')!.trigger('click')
    expect(w.text()).not.toContain('devam edilsin mi?')
    expect(ozelKuralApi.ekle).not.toHaveBeenCalled()
  })

  it('ENGEL + kırmızı atama yoksa sessizce kaydedilir; taze dene sonucu varsa yeniden denenmez', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(0))
    vi.mocked(ozelKuralApi.ekle).mockResolvedValue(mevcut)
    const w = kur()
    await flushPromises()
    await doldur(w)
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(ozelKuralApi.dene).toHaveBeenCalledTimes(1)
    expect(w.text()).not.toContain('devam edilsin mi?')
    expect(ozelKuralApi.ekle).toHaveBeenCalledTimes(1)
  })

  it('sunucu 400 alan mesajı ilgili alanın yanında gösterilir', async () => {
    vi.mocked(ozelKuralApi.ekle).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Geçersiz', {
        alanlar: { esik: 'Eşik 24 saatten büyük olamaz.' },
      }),
    )
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('#kk-esik-hata').text()).toBe('Eşik 24 saatten büyük olamaz.')
    expect(w.get('#kk-esik').attributes('aria-invalid')).toBe('true')
    expect(w.emitted('kapat')).toBeUndefined()
  })

  it('409 ad tekrarı üstte uyarı olarak görünür ve diyalog kapanmaz', async () => {
    vi.mocked(ozelKuralApi.ekle).mockRejectedValue(
      new ApiHata(409, 'KURAL_AD_TEKRARI', 'Bu adla bir kural zaten var.'),
    )
    const w = kur()
    await flushPromises()
    await doldur(w)
    await w.get('input[name="kk-siddet"][value="UYARI"]').setValue()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('Bu adla bir kural zaten var.')
    expect(w.emitted('kapat')).toBeUndefined()
  })

  it('düzenlemede alanlar dolu gelir; neden boşken Kaydet kapalı; versiyon + neden gönderilir', async () => {
    const guncel = { ...mevcut, versiyon: 4 }
    vi.mocked(ozelKuralApi.guncelle).mockResolvedValue(guncel)
    const w = kur(mevcut)
    await flushPromises()
    expect((w.get('#kk-ad').element as HTMLInputElement).value).toBe('Sivas hattı sınırı')
    expect((w.get('#kk-esik').element as HTMLInputElement).value).toBe('8')
    expect((w.get('#kk-kaynak').element as HTMLInputElement).value).toBe('TİS')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeDefined()
    await w.get('#kk-neden').setValue('  Protokol güncellendi ')
    expect(kaydetDugmesi(w).attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(ozelKuralApi.guncelle).toHaveBeenCalledWith(
      4,
      expect.objectContaining({
        ad: 'Sivas hattı sınırı',
        siddet: 'UYARI',
        versiyon: 3,
        neden: 'Protokol güncellendi',
        kapsam: expect.objectContaining({ hatlar: [1], vardiya: 'GECE' }),
      }),
    )
    expect(w.emitted('kaydedildi')).toEqual([[guncel]])
  })

  it('düzenlemede dene düzenlenen kuralın kimliğiyle çağrılır; sürüm çakışması üstte görünür', async () => {
    vi.mocked(ozelKuralApi.dene).mockResolvedValue(sonuc(0))
    vi.mocked(ozelKuralApi.guncelle).mockRejectedValue(
      new ApiHata(409, 'VERSIYON_CAKISMASI', 'Kural başka biri tarafından değiştirildi.'),
    )
    const w = kur(mevcut)
    await flushPromises()
    await w.get('#kk-neden').setValue('Neden')
    await dugme(w, 'Dene')!.trigger('click')
    await flushPromises()
    expect(ozelKuralApi.dene).toHaveBeenCalledWith(expect.anything(), 4)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('başka biri tarafından değiştirildi')
  })
})
