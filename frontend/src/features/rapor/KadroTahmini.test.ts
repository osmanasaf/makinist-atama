import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { kadro: vi.fn(), kadroExcel: vi.fn() },
}))
vi.mock('@/features/tanim/tanimApi', () => ({
  tanimApi: {
    liste: vi.fn(async (tip: string) =>
      tip === 'HAT'
        ? [{ id: 1, tip: 'HAT', ad: 'Ankara–Sivas' }]
        : tip === 'ARAC_TIPI'
          ? [{ id: 2, tip: 'ARAC_TIPI', ad: 'Elektrikli Dizi' }]
          : [
              { id: 3, tip: 'GAR', ad: 'Ankara' },
              { id: 4, tip: 'GAR', ad: 'Sivas' },
            ],
    ),
  },
}))

import KadroTahmini from './KadroTahmini.vue'
import { raporApi, type KadroDoluluk, type KadroRaporu } from './raporApi'

const doluluk = (ad: string, bos: number): KadroDoluluk => ({
  ad,
  etiket: ad,
  seferSayisi: 2800,
  bos,
  dolulukYuzde: (100 * (2800 - bos)) / 2800,
  sureMs: 1000,
})

const rapor = (ek: Partial<KadroRaporu> = {}): KadroRaporu => ({
  varsayim: {
    bas: '2026-10-12',
    bit: '2026-11-08',
    hafta: 4,
    gunSayisi: 28,
    devamsizlikYuzde: 13,
    kisiBasinaSaat: 47.85,
    yedekOran: 0.1,
    yedekMin: 1,
    sureSn: 25,
    aktifMakinist: 66,
    seferSayisi: 2800,
    haftalikTalepSaat: 2201.3,
  },
  talep: [
    { hat: 'Ankara–Sivas', aracTipi: 'Elektrikli Dizi', seferSayisi: 840, haftalikSaat: 1151.9 },
  ],
  garlar: [{ gar: 'Ankara', haftalikTalepSaat: 943.3, mevcutMakinist: 37, eklenen: 4 }],
  analitik: {
    kisiBasinaSaat: 47.85,
    gerekliAltSinir: 47,
    tekBroveliGerekli: 49,
    mevcutAsil: 66,
    eksikSaat: 0,
    eksikAltSinir: 0,
    siniflar: [
      {
        hat: 'Ankara–Sivas',
        aracTipi: 'Elektrikli Dizi',
        haftalikSaat: 1151.9,
        gerekliKisi: 25,
        mevcutYetkili: 45,
        eksikSaat: 0,
        eksikKisi: 0,
      },
    ],
  },
  kumeler: [
    {
      aracTipleri: ['Elektrikli Dizi'],
      hatlar: ['Ankara–Sivas'],
      mevcut: 27,
      kullanilanSaat: 1000,
      kapasiteSaat: 1483,
      kullanimYuzde: 67.4,
      durum: 'NORMAL',
    },
  ],
  kumeKaynagi: 'PLAN',
  yedek: { ihtiyac: 10, enYogunGunPlanliSefer: 100 },
  simulasyon: {
    mevcutAsil: 66,
    mevcut: doluluk('mevcut', 122),
    konumsuz: doluluk('konumsuz', 35),
    iyilestirmeli: null,
    yolculuk: null,
    egri: [
      { ek: 0, bos: 122, dolulukYuzde: 95.64, marjinal: null },
      { ek: 7, bos: 44, dolulukYuzde: 98.43, marjinal: 11.14 },
      { ek: 12, bos: 28, dolulukYuzde: 99, marjinal: 3.2 },
      { ek: 64, bos: 3, dolulukYuzde: 99.89, marjinal: 0.48 },
    ],
    hedefler: [
      { yuzde: 98, ulasildi: true, ek: 7, bos: 44, dolulukYuzde: 98.43 },
      { yuzde: 99, ulasildi: true, ek: 12, bos: 28, dolulukYuzde: 99 },
      { yuzde: 100, ulasildi: false, ek: null, bos: 3, dolulukYuzde: 99.89 },
    ],
    egriDurma: 'EK_SINIRI',
    egriKosuSayisi: 14,
    dagilimEk: 12,
    dagilimEtiketi: '%99 hedefi için +12 genel makinist',
    dagilim: [
      {
        gar: 'Ankara',
        aracTipi: 'Dizel Lokomotif',
        hat: 'Eskişehir–Konya',
        kisi: 2,
        seferSayisi: 76,
        haftalikSaat: 62,
      },
    ],
    kalanBoslar: [
      {
        seferId: 7,
        trenNo: 'T7',
        tarih: '2026-10-14',
        kalkisGar: 'Ankara',
        varisGar: 'Sivas',
        neden: '0/66 uygun: konum 5',
      },
    ],
    kalanBosToplam: 1,
    kalanNedenler: [{ neden: 'konum', adet: 1 }],
    atlananlar: [],
    tamamlandi: true,
    sureMs: 19000,
  },
  senaryo: null,
  ...ek,
})

function kur() {
  return mount(KadroTahmini)
}
const duzenle = async () => {
  await flushPromises()
}

describe('KadroTahmini (F3a, OK-22 doluluk eğrisi)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('Hesapla varsayılanlarla istek gönderir; mevcut doluluk, hedefler (+n / ulaşılamadı) ve pencere notu görünür', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(rapor())
    const w = kur()
    await duzenle()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()

    const g = vi.mocked(raporApi.kadro).mock.calls[0]![0]
    expect(g).toMatchObject({
      hafta: 4,
      devamsizlikYuzde: 13,
      yedekDahil: true,
      yalnizAnalitik: false,
      sureSn: 25,
      yolculuk: false,
      seferler: [],
    })
    expect(w.get('[data-test=mevcut-yuzde]').text()).toBe('%95,64')
    expect(w.get('[data-test=mevcut-doluluk]').text()).toContain('122 boş sefer / 2800')
    expect(w.get('[data-test=hedef-98]').text()).toContain('+7')
    expect(w.get('[data-test=hedef-99]').text()).toContain('+12')
    const h100 = w.get('[data-test=hedef-100]').text()
    expect(h100).toContain('Ulaşılamadı')
    expect(h100).toContain('+64 denendi')
    // tek "toplam kadro"/"+29" biçiminde başlık sayı yok
    expect(w.text()).not.toContain('toplam kadro')
    expect(w.get('[data-test=pencere-notu]').text()).toContain('4 haftalık pencereye')
    // Q-2: başlık devamsızlık varsayımını söyler ve masadaki gerçek plandan neden farklı olduğunu açıklar
    expect(w.get('[data-test=mevcut-doluluk]').text()).toContain('%13 devamsızlık varsayımıyla')
    expect(w.get('[data-test=baslik-varsayim]').text()).toContain('temiz sayfadan')
    expect(w.get('[data-test=baslik-varsayim]').text()).toContain('masadaki gerçek plan')
    expect(w.get('[data-test=alt-degerler]').text()).toContain('%98,75')
    expect(w.get('[data-test=egri]').text()).toContain('Doluluk eğrisi')
    expect(w.get('[data-test=egri]').text()).toContain('11,14')
    expect(w.get('[data-test=dagilim]').text()).toContain('Dizel Lokomotif')
    expect(w.get('[data-test=dagilim]').text()).toContain('%99 hedefi için +12')
    expect(w.get('[data-test=yedek]').text()).toContain('10')
    expect(w.get('[data-test=yedek]').text()).not.toContain('havuz')
    expect(w.find('[data-test=sure-uyarisi]').exists()).toBe(false)
    expect(w.text()).toContain('Kalan boş seferler (1)')
    expect(w.get('[data-test=kalan-nedenler]').text()).toContain('konum')
    expect(w.text()).toContain('0/66 uygun: konum 5')
  })

  it('mevcut kadro hedefi zaten sağlıyorsa "ek gerekmiyor" yazar', async () => {
    const r = rapor()
    r.simulasyon!.hedefler[0] = { yuzde: 98, ulasildi: true, ek: 0, bos: 40, dolulukYuzde: 98.6 }
    vi.mocked(raporApi.kadro).mockResolvedValue(r)
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    expect(w.get('[data-test=hedef-98]').text()).toContain('ek gerekmiyor')
  })

  it('ek hesap atlanınca uyarı yön göstermez: eğri etkilenmez, atlanan hesaplar yazılır', async () => {
    const r = rapor()
    r.simulasyon!.tamamlandi = false
    r.simulasyon!.atlananlar = ['İyileştirmeli plan (süre yetmedi)']
    vi.mocked(raporApi.kadro).mockResolvedValue(r)
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()

    const u = w.get('[data-test=sure-uyarisi]').text()
    expect(u).toContain('EK hesaplar')
    expect(u).toContain('İyileştirmeli plan')
    expect(u).toContain('eğrisi süre sınırından etkilenmez')
    expect(u).not.toContain('gerçek ihtiyaç daha düşük')
    // varsayılan deneyimde sakin not: bildirim bandı değil (H, orkestratör (8))
    expect(w.get('[data-test=sure-uyarisi]').classes()).not.toContain('bildirim')
    expect(w.get('[data-test=sure-uyarisi] svg').attributes('data-simge')).toBe('bilgi')
  })

  it('yalnız analitik: simülasyon bölümü ve başlık kutusu yok', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(rapor({ simulasyon: null, kumeKaynagi: 'AKIS' }))
    const w = kur()
    await w.get('#kt-analitik').setValue(true)
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()

    expect(vi.mocked(raporApi.kadro).mock.calls[0]![0].yalnizAnalitik).toBe(true)
    expect(w.find('[data-test=baslik]').exists()).toBe(false)
    expect(w.get('[data-test=analitik]').text()).toContain('47')
    expect(w.text()).not.toContain('Kalan boş seferler')
    expect(w.text()).toContain('analitik akış tahsisinden')
  })

  it('sanal sefer satırı gövdeye girer; eksik alan hata verir ve istek gitmez', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(rapor())
    const w = kur()
    await duzenle()
    await w.get('[data-test=satir-ekle]').trigger('click')
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Sanal sefer satırı 1')
    expect(raporApi.kadro).not.toHaveBeenCalled()

    const sec = w.findAll('[data-test=sanal-satir] select')
    await sec[0]!.setValue(1)
    await sec[1]!.setValue(2)
    await sec[2]!.setValue(3)
    await sec[3]!.setValue(4)
    await w.get('input[aria-label="Kalkış saatleri 1"]').setValue('6:00, 14:00')
    await w.get('input[aria-label="Süre dakika 1"]').setValue(330)
    await w.get('input[aria-label="Pzt 1"]').setValue(true)
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()

    expect(vi.mocked(raporApi.kadro).mock.calls[0]![0].seferler).toEqual([
      {
        hatId: 1,
        aracTipiId: 2,
        kalkisGarId: 3,
        varisGarId: 4,
        saatler: ['06:00', '14:00'],
        sureDk: 330,
        gunler: ['MONDAY'],
      },
    ])
  })

  it('geçersiz saat metni hata verir', async () => {
    const w = kur()
    await duzenle()
    await w.get('[data-test=satir-ekle]').trigger('click')
    const sec = w.findAll('[data-test=sanal-satir] select')
    await sec[0]!.setValue(1)
    await sec[1]!.setValue(2)
    await sec[2]!.setValue(3)
    await sec[3]!.setValue(4)
    await w.get('input[aria-label="Kalkış saatleri 1"]').setValue('25:99')
    await w.get('input[aria-label="Süre dakika 1"]').setValue(60)
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('SS:DD')
    expect(raporApi.kadro).not.toHaveBeenCalled()
  })

  it('senaryo kutusu aynı kadrodaki toplam boş farkını, eğri farkını ve gerekçeyi gösterir', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(
      rapor({
        senaryo: {
          ekSeferSayisi: 112,
          ekHaftalikSaat: 154,
          taban: doluluk('mevcut', 122),
          senaryo: doluluk('senaryo', 143),
          bosFarki: 21,
          gurultuEsigi: 12,
          mevcutYeter: false,
          gerekce:
            'Aynı 66 kişilik kadroyla toplam boş sefer 122 → 143 (+21). Mevcut kadro yetmez.',
          egriFarki: [
            { yuzde: 98, tabanEk: 7, senaryoEk: 10, fark: 3 },
            { yuzde: 99, tabanEk: 12, senaryoEk: 16, fark: 4 },
            { yuzde: 100, tabanEk: null, senaryoEk: null, fark: null },
          ],
          ekTalepKisiEsdegeri: 3.2,
          atilKapasiteKisiEsdegeri: 14.6,
          tabanEksikSaat: 0,
          senaryoEksikSaat: 0,
          tamamlandi: true,
          uyari: null,
        },
      }),
    )
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()

    expect(w.get('[data-test=senaryo-fark]').text()).toBe('21')
    expect(w.get('[data-test=senaryo-gerekce]').text()).toContain('yetmez')
    expect(w.get('[data-test=senaryo]').text()).toContain('122 → 143')
    const egri = w.get('[data-test=senaryo-egri]').text()
    expect(egri).toContain('+3')
    expect(egri).toContain('ulaşılamadı')
    expect(w.get('[data-test=senaryo]').text()).not.toContain('analitik fark')
  })

  it('senaryoda eğri farkı hesaplanamadıysa uyarı görünür, eğri farkı tablosu yok', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(
      rapor({
        senaryo: {
          ekSeferSayisi: 56,
          ekHaftalikSaat: 154,
          taban: doluluk('mevcut', 122),
          senaryo: doluluk('senaryo', 125),
          bosFarki: 3,
          gurultuEsigi: 12,
          mevcutYeter: true,
          gerekce: 'Artış gürültü eşiğinin altında.',
          egriFarki: null,
          ekTalepKisiEsdegeri: 3.2,
          atilKapasiteKisiEsdegeri: 14.6,
          tabanEksikSaat: 0,
          senaryoEksikSaat: 0,
          tamamlandi: false,
          uyari: 'Süre sınırı: eğri farkı hesaplanmadı; toplam boş farkı geçerlidir.',
        },
      }),
    )
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    expect(w.find('[data-test=senaryo-egri]').exists()).toBe(false)
    expect(w.get('[data-test=senaryo]').text()).toContain('eğri farkı hesaplanmadı')
    expect(w.find('[data-test=sure-uyarisi]').exists()).toBe(true)
  })

  it('hata mesajı gösterilir; çift tıklama ikinci istek göndermez; İptal sonucu yok sayar', async () => {
    let bitir: (r: KadroRaporu) => void = () => {}
    vi.mocked(raporApi.kadro).mockReturnValue(new Promise((coz) => (bitir = coz)))
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await w.get('[data-test=hesapla]').trigger('click')
    expect(raporApi.kadro).toHaveBeenCalledTimes(1)
    expect(w.get('[data-test=hesaplaniyor]').text()).toContain('Hesaplanıyor')

    await w.get('[data-test=iptal]').trigger('click')
    bitir(rapor())
    await flushPromises()
    expect(w.find('[data-test=baslik]').exists()).toBe(false)
    expect(w.find('[data-test=hesaplaniyor]').exists()).toBe(false)
  })

  it('Excel düğmesi raporu sunucuya geri gönderir', async () => {
    const r = rapor()
    vi.mocked(raporApi.kadro).mockResolvedValue(r)
    vi.mocked(raporApi.kadroExcel).mockResolvedValue({ blob: new Blob(['x']), ad: 'kadro.xlsx' })
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const w = kur()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    await w.get('[data-test=excel]').trigger('click')
    await flushPromises()

    expect(raporApi.kadroExcel).toHaveBeenCalledWith(r)
  })

  it('QA BUG-10: başlık düzeyi atlamaz (h1 > h2; özet kartları h3 değil)', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(rapor())
    const w = kur()
    await duzenle()
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    const duzeyler = w.findAll('h1, h2, h3, h4').map((h) => Number(h.element.tagName[1]))
    duzeyler.reduce((onceki, d) => {
      expect(d - onceki).toBeLessThanOrEqual(1) // h1 → h3 gibi atlama yok
      return d
    }, 0)
    expect(duzeyler.includes(3)).toBe(false)
  })
})
