import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    ata: vi.fn(),
    kaldir: vi.fn(),
    kirmiziSayisi: vi.fn(),
    oneri: vi.fn(),
    uygunSeferler: vi.fn(),
    otomatik: vi.fn(),
    seferIptal: vi.fn(),
    seferGecikme: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi, type Oneri, type UygunSefer } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const ali = makinist()
const kirmiziAli = makinist({
  kirmizi: true,
  kirmiziNedenler: ['Dinlenme 9 s 30 dk < 11 s'],
})
const iptalAli = makinist({
  atamaId: 20,
  makinistId: 3,
  sicil: 'D003',
  ad: 'Boşta Kişi',
  kirmizi: true,
  kirmiziNedenler: ['İPTAL – makinist boşta'],
})
const uyarili = makinist({
  atamaId: 30,
  makinistId: 4,
  sicil: 'D004',
  ad: 'Uyarılı Kişi',
  uyarilar: ['Günlük 9 s > 8 s'],
})

function gunVerisi(): GunListesi {
  return {
    tarih: '2026-10-12',
    uretimZamani: '2026-10-12T07:00:00Z',
    satirlar: [
      satir(1, { makinistler: [ali] }), // temiz
      satir(2, { kirmizi: true, makinistler: [kirmiziAli] }),
      satir(3, { durum: 'IPTAL', kirmizi: true, makinistler: [iptalAli] }),
      satir(4, { makinistler: [uyarili] }),
      satir(5), // BOŞ
    ],
  }
}

const sefer = {} as Awaited<ReturnType<typeof atamaApi.seferIptal>>
const oneri = (ek: Partial<Oneri> = {}): Oneri => ({
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Öneri Yedi',
  bagliGar: 'Ankara',
  buAySaat: 100,
  buAyKm: 5000,
  uyarilar: [],
  gerekce: 'En az saatli',
  ...ek,
})
const uygunSefer: UygunSefer = {
  seferId: 5,
  trenNo: 'T1005',
  kalkisGar: 'Ankara',
  varisGar: 'Konya',
  guncelKalkis: '2026-10-12T09:00:00Z',
  guncelVaris: '2026-10-12T11:00:00Z',
  aracTipi: 'YHT',
  hat: 'Ankara–Konya',
  uyarilar: [],
}

async function kur(sorgu = '?tarih=2026-10-12') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push(`/atama${sorgu}`)
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return { w, router }
}
type Masa = Awaited<ReturnType<typeof kur>>['w']
const satirlar = (w: Masa) => w.findAll('tr.sefer-satiri')
const dugme = (w: Pick<Masa, 'findAll'>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)!

describe('AtamaMasasi: kırmızı, uyarı, sayaç', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 3,
      kirmiziAtama: 4,
    })
  })

  it('kırmızı satır: kırmızı sınıf, "KONTROL" metni ve görevlinin yanında neden; temiz satırda yok', async () => {
    const { w } = await kur()
    const s = satirlar(w)
    expect(s[1]!.classes()).toContain('kirmizi')
    expect(s[1]!.text()).toContain('⛔ KONTROL')
    expect(s[1]!.text()).toContain('Dinlenme 9 s 30 dk < 11 s')
    expect(s[0]!.classes()).not.toContain('kirmizi')
    expect(s[0]!.text()).not.toContain('KONTROL')
  })

  it('iptal sefer: "İPTAL" (KONTROL değil), boşta kalan görevlinin nedeni ve Kaldır ipucu görünür', async () => {
    const { w } = await kur()
    const s = satirlar(w)[2]!
    expect(s.text()).toContain('İPTAL')
    expect(s.text()).not.toContain('KONTROL')
    expect(s.text()).toContain('İPTAL – makinist boşta')
    expect(s.text()).toContain('Eski atama iptal seferde duruyor')
  })

  it('UYARI ile atanmış görevli: "⚠ Uyarı" işareti + metin, satır kırmızı değil', async () => {
    const { w } = await kur()
    const s = satirlar(w)[3]!
    expect(s.text()).toContain('⚠ Uyarı')
    expect(s.text()).toContain('Günlük 9 s > 8 s')
    expect(s.classes()).not.toContain('kirmizi')
    expect(s.text()).not.toContain('KONTROL')
    expect(satirlar(w)[0]!.text()).not.toContain('Uyarı')
  })

  it('üst çubuktaki "Kırmızı: N sefer" rozeti seçili gün + 7 gün için sorulur', async () => {
    const { w } = await kur()
    expect(atamaApi.kirmiziSayisi).toHaveBeenCalledWith('2026-10-12', '2026-10-19')
    expect(w.get('[data-test="kirmizi-sayac"]').text()).toBe('⛔ Kırmızı: 3 sefer')
    expect(w.get('[data-test="kirmizi-sayac"]').attributes('title')).toContain('4 atama')
  })

  it('gün değişince sayaç yeni aralıkla yenilenir', async () => {
    const { w, router } = await kur()
    await router.replace({ query: { tarih: '2026-10-13' } })
    await flushPromises()
    expect(atamaApi.kirmiziSayisi).toHaveBeenLastCalledWith('2026-10-13', '2026-10-20')
    expect(w.find('[data-test="kirmizi-sayac"]').exists()).toBe(true)
  })

  it('sayaç hata verirse masa yine çalışır; rozet gösterilmez', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockRejectedValue(new Error('x'))
    const { w } = await kur()
    expect(satirlar(w)).toHaveLength(5)
    expect(w.find('[data-test="kirmizi-sayac"]').exists()).toBe(false)
  })

  it('atama sonrası sayaç da yenilenir', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue({} as Awaited<ReturnType<typeof atamaApi.ata>>)
    vi.mocked(atamaApi.adaylar).mockResolvedValue([
      {
        makinistId: 7,
        sicil: 'D007',
        adSoyad: 'Aday Bir',
        bagliGar: 'Ankara',
        uygun: true,
        ihlaller: [],
        buAySaat: 1,
        buAyKm: 1,
        buSeferdeRol: null,
      },
    ])
    const { w } = await kur()
    await satirlar(w)[4]!.trigger('click')
    await flushPromises()
    await w.get('tr.aday').trigger('click')
    await w.get('dialog form').trigger('submit')
    await flushPromises()
    expect(atamaApi.kirmiziSayisi).toHaveBeenCalledTimes(2)
  })

  describe('Gecikme / İptal', () => {
    it('düğmeler iptal olmayan satırlarda var, iptal satırında yok', async () => {
      const { w } = await kur()
      const s = satirlar(w)
      expect(s[0]!.find('button[aria-label^="T1001"][aria-label$="gecikme gir"]').exists()).toBe(
        true,
      )
      expect(s[0]!.find('button[aria-label$="iptal et"]').exists()).toBe(true)
      expect(s[2]!.find('button[aria-label$="gecikme gir"]').exists()).toBe(false)
      expect(s[2]!.find('button[aria-label$="iptal et"]').exists()).toBe(false)
    })

    it('Gecikme: diyalog, doğru gövde; başarıda gün ve sayaç yenilenir, bilgi gösterilir', async () => {
      vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
      const { w } = await kur()
      await satirlar(w)[0]!.get('button[aria-label$="gecikme gir"]').trigger('click')
      expect(w.get('dialog').text()).toContain('Gecikme gir')
      await w.get('#sefer-kalkis-gecikme').setValue('45')
      await w.get('#sefer-neden').setValue('GECIKME')
      await w.get('dialog form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferGecikme).toHaveBeenCalledWith(1, {
        kalkisGecikmeDk: 45,
        varisGecikmeDk: undefined,
        neden: 'GECIKME',
        nedenNotu: undefined,
      })
      expect(atamaApi.gun).toHaveBeenCalledTimes(2)
      expect(atamaApi.kirmiziSayisi).toHaveBeenCalledTimes(2)
      expect(w.find('dialog').exists()).toBe(false)
      expect(w.get('[role="status"]').text()).toBe('Gecikme kaydedildi.')
    })

    it('İptal: geri alınamaz uyarısı, doğru gövde, yenileme', async () => {
      vi.mocked(atamaApi.seferIptal).mockResolvedValue(sefer)
      const { w } = await kur()
      await satirlar(w)[1]!.get('button[aria-label$="iptal et"]').trigger('click')
      expect(w.get('dialog').text()).toContain('Bu işlem geri alınamaz')
      await w.get('#sefer-neden').setValue('HASTALIK')
      await w.get('dialog form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferIptal).toHaveBeenCalledWith(2, {
        neden: 'HASTALIK',
        nedenNotu: undefined,
      })
      expect(atamaApi.gun).toHaveBeenCalledTimes(2)
      expect(atamaApi.kirmiziSayisi).toHaveBeenCalledTimes(2)
      expect(w.get('[role="status"]').text()).toBe('Sefer iptal edildi.')
    })
  })

  describe('Yerine ata', () => {
    it('yalnız asıl görevlisi olan satırda; kırmızı satırda öne çıkar (birincil düğme)', async () => {
      const { w } = await kur()
      const s = satirlar(w)
      expect(s[4]!.find('button[aria-label$="yerine ata"]').exists()).toBe(false) // BOŞ
      expect(s[0]!.get('button[aria-label$="yerine ata"]').classes()).not.toContain('btn-birincil')
      expect(s[1]!.get('button[aria-label$="yerine ata"]').classes()).toContain('btn-birincil')
    })

    it('öneri seçilince AtamaDialog "değiştir" kipinde açılır; neden zorunlu; uyarı gerekçe ister', async () => {
      vi.mocked(atamaApi.oneri).mockResolvedValue({
        oneriler: [
          oneri({
            uyarilar: [
              {
                kod: 'K-20',
                siddet: 'UYARI',
                olculen: 9,
                esik: 8,
                birim: 's',
                metin: 'Günlük 9 s > 8 s',
              },
            ],
          }),
        ],
        uygunAdayYok: false,
        mesaj: null,
      })
      vi.mocked(atamaApi.ata).mockResolvedValue({} as Awaited<ReturnType<typeof atamaApi.ata>>)
      const { w } = await kur()
      await satirlar(w)[1]!.get('button[aria-label$="yerine ata"]').trigger('click')
      await flushPromises()
      expect(atamaApi.oneri).toHaveBeenCalledWith(2)
      expect(w.get('dialog').text()).toContain('Öneri Yedi')
      await dugme(w.get('dialog'), 'Seç').trigger('click')
      await flushPromises()
      const d = w.get('dialog')
      expect(d.text()).toContain('Makinisti değiştir')
      expect(d.text()).toContain('Ali Veli (D001)')
      expect(d.text()).toContain('Günlük 9 s > 8 s')
      const onay = d.get('button[type="submit"]')
      expect(onay.attributes('disabled')).toBeDefined() // neden + gerekçe yok
      await d.get('#atama-neden').setValue('BELGE')
      await d.get('#atama-gerekce').setValue('Amir onayı')
      await d.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.ata).toHaveBeenCalledWith(2, {
        makinistId: 7,
        rol: 'ASIL',
        gerekce: 'Amir onayı',
        neden: 'BELGE',
        nedenNotu: undefined,
      })
      expect(w.get('[role="status"]').text()).toBe('Atama yapıldı.')
    })

    it('BUG-12: gün değişince açık Yerine ata diyaloğu kapanır', async () => {
      vi.mocked(atamaApi.oneri).mockResolvedValue({
        oneriler: [],
        uygunAdayYok: true,
        mesaj: 'Uygun aday yok.',
      })
      const { w, router } = await kur()
      await satirlar(w)[0]!.get('button[aria-label$="yerine ata"]').trigger('click')
      await flushPromises()
      expect(w.find('dialog').exists()).toBe(true)
      await router.replace({ query: { tarih: '2026-10-13' } })
      await flushPromises()
      expect(w.find('dialog').exists()).toBe(false)
    })

    it('aday yoksa sunucu mesajı gösterilir', async () => {
      vi.mocked(atamaApi.oneri).mockResolvedValue({
        oneriler: [],
        uygunAdayYok: true,
        mesaj: 'Uygun aday yok. 0/4 uygun: hat yetkisi 4',
      })
      const { w } = await kur()
      await satirlar(w)[0]!.get('button[aria-label$="yerine ata"]').trigger('click')
      await flushPromises()
      expect(w.get('dialog').text()).toContain('Uygun aday yok. 0/4 uygun: hat yetkisi 4')
    })
  })

  describe('Uygun seferler', () => {
    it('yalnız iptal seferin kırmızı görevlisinde görünür', async () => {
      const { w } = await kur()
      const s = satirlar(w)
      expect(s[2]!.find('button[aria-label$="için uygun seferler"]').exists()).toBe(true)
      expect(s[1]!.find('button[aria-label$="için uygun seferler"]').exists()).toBe(false)
    })

    it('seçilen sefer için AtamaDialog o seferin kimliğiyle ve aynı makinistle açılır', async () => {
      vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([uygunSefer])
      vi.mocked(atamaApi.ata).mockResolvedValue({} as Awaited<ReturnType<typeof atamaApi.ata>>)
      const { w } = await kur()
      await satirlar(w)[2]!.get('button[aria-label$="için uygun seferler"]').trigger('click')
      await flushPromises()
      expect(atamaApi.uygunSeferler).toHaveBeenCalledWith(3, '2026-10-12')
      await dugme(w.get('dialog'), 'Seç').trigger('click')
      await flushPromises()
      const d = w.get('dialog')
      expect(d.text()).toContain('Makinist ata')
      expect(d.text()).toContain('T1005')
      expect(d.text()).toContain('Boşta Kişi (D003)')
      await d.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.ata).toHaveBeenCalledWith(5, {
        makinistId: 3,
        rol: 'ASIL',
        gerekce: undefined,
      })
    })
  })

  describe('Otomatik ata', () => {
    it('düğme diyaloğu açar; uygulama sonrası masa yenilenir', async () => {
      const sonuc = {
        bas: '2026-10-12',
        bit: '2026-10-18',
        uygulandi: true,
        atananSayisi: 1,
        bosSayisi: 0,
        gecmisSayisi: 0,
        atananlar: [],
        bosKalanlar: [],
      }
      vi.mocked(atamaApi.otomatik)
        .mockResolvedValueOnce({ ...sonuc, uygulandi: false })
        .mockResolvedValueOnce(sonuc)
      const { w } = await kur()
      await dugme(w, 'Otomatik ata').trigger('click')
      const d = w.get('dialog')
      expect((d.get('#oto-bas').element as HTMLInputElement).value).toBe('2026-10-12')
      await dugme(d, 'Önizle').trigger('click')
      await flushPromises()
      await dugme(d, 'Uygula').trigger('click')
      await flushPromises()
      expect(atamaApi.gun).toHaveBeenCalledTimes(2)
      expect(atamaApi.kirmiziSayisi).toHaveBeenCalledTimes(2)
      expect(w.get('dialog').text()).toContain('1 atandı, 0 boş kaldı')
      await dugme(w.get('dialog'), 'Kapat').trigger('click')
      expect(w.find('dialog').exists()).toBe(false)
    })
  })
})

describe('AtamaMasasi: nöbet notu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
  })

  it('kırmızı neden nöbet içeriyorsa masada tek not görünür', async () => {
    const nobetli = (id: number) =>
      makinist({
        atamaId: id,
        makinistId: id,
        kirmizi: true,
        kirmiziNedenler: ['Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk'],
      })
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-12',
      uretimZamani: '2026-10-12T07:00:00Z',
      satirlar: [
        satir(1, { kirmizi: true, makinistler: [nobetli(1)] }),
        satir(2, { kirmizi: true, makinistler: [nobetli(2)] }),
      ],
    })
    const { w } = await kur()
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })

  it('nöbet geçmiyorsa not görünmez', async () => {
    vi.mocked(atamaApi.gun).mockResolvedValue(gunVerisi())
    const { w } = await kur()
    expect(w.find('[data-test="nobet-notu"]').exists()).toBe(false)
  })
})
