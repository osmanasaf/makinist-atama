import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type Aday, type GunListesi, type GunSatiri } from './atamaApi'
import { makinist } from './testVerisi'

const bos = { template: '<div />' }

function satir(seferId: number, ek: Partial<GunSatiri> = {}): GunSatiri {
  return {
    seferId,
    trenNo: `T${1000 + seferId}`,
    kalkisGar: 'Ankara',
    varisGar: 'Eskişehir',
    planlananKalkis: '2026-10-12T05:30:00Z',
    planlananVaris: '2026-10-12T07:00:00Z',
    guncelKalkis: '2026-10-12T05:30:00Z', // İstanbul 08:30
    guncelVaris: '2026-10-12T07:00:00Z',
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    aracTipi: 'YHT',
    hat: 'Ankara–Eskişehir',
    km: 230,
    durum: 'PLANLI',
    kirmizi: false,
    makinistler: [],
    bosNeden: null,
    ...ek,
  }
}
const asil = makinist()
const yrd = makinist({
  atamaId: 11,
  rol: 'YARDIMCI',
  makinistId: 2,
  sicil: 'D002',
  ad: 'Veli Yardım',
})

function gunVerisi(): GunListesi {
  return {
    tarih: '2026-10-12',
    uretimZamani: '2026-10-12T07:00:00Z',
    satirlar: [
      satir(1, { makinistler: [asil, yrd] }),
      satir(2, { bosNeden: '0/3 uygun: dinlenme 3' }), // BOŞ
      satir(3, { durum: 'IPTAL' }),
      satir(4, { makinistler: [asil], kalkisGecikmeDk: 90, guncelKalkis: '2026-10-12T07:00:00Z' }),
    ],
  }
}
const aday = (ek: Partial<Aday> = {}): Aday => ({
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Aday Bir',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 100,
  buAyKm: 5000,
  buSeferdeRol: null,
  ...ek,
})

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
const satirlar = (w: Awaited<ReturnType<typeof kur>>['w']) => w.findAll('tr.sefer-satiri')

describe('AtamaMasasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([aday()])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
  })

  it('gün listesini çizer: makinistler, BOŞ rozeti, İPTAL, gecikme metni ve atanmamış sayacı', async () => {
    const { w } = await kur()
    expect(atamaApi.gun).toHaveBeenCalledWith('2026-10-12')
    const s = satirlar(w)
    expect(s).toHaveLength(4)
    expect(s[0]!.text()).toContain('08:30–10:00')
    expect(s[0]!.text()).toContain('T1001')
    expect(s[0]!.text()).toContain('Ankara – Eskişehir')
    expect(s[0]!.text()).toContain('Ali Veli (D001)')
    expect(s[0]!.text()).toContain('Yrd: Veli Yardım (D002)')
    expect(s[0]!.text()).not.toContain('BOŞ')
    expect(s[1]!.text()).toContain('BOŞ')
    expect(s[1]!.text()).toContain('0/3 uygun: dinlenme 3') // UX-13: boş seferin nedeni satırda
    expect(s[0]!.text()).not.toContain('uygun:')
    expect(s[2]!.text()).toContain('İPTAL')
    expect(s[2]!.text()).not.toContain('BOŞ')
    expect(s[3]!.text()).toContain('+90 dk (yeni kalkış 10:00)')
    // İptal sefer sayaca girmez: yalnız 2. sefer atanmamış.
    expect(w.get('[data-test="atanmamis-sayac"]').text()).toBe('Atanmamış: 1 sefer')
  })

  it('ilk yükleme sürerken "sefer yok" ve "Atanmamış: 0" göstermez, yükleniyor yazar (SON-QA Q-5)', async () => {
    let bitir: (g: GunListesi) => void = () => {}
    vi.mocked(atamaApi.gun).mockImplementation(
      () =>
        new Promise<GunListesi>((coz) => {
          bitir = coz
        }),
    )
    const { w } = await kur()
    expect(w.text()).toContain('Yükleniyor…')
    expect(w.text()).not.toContain('Bu gün için sefer yok')
    expect(w.find('[data-test="atanmamis-sayac"]').exists()).toBe(false)
    bitir(gunVerisi())
    await flushPromises()
    expect(w.text()).not.toContain('Yükleniyor…')
    expect(w.get('[data-test="atanmamis-sayac"]').text()).toBe('Atanmamış: 1 sefer')
  })

  it('?tarih= yoksa bugün; gün seçici, önceki/sonraki gün ve Yazdır bağlantısı', async () => {
    const { w, router } = await kur('')
    expect(atamaApi.gun).toHaveBeenCalledTimes(1)
    await router.replace({ query: { tarih: '2026-10-31' } })
    await flushPromises()
    expect(atamaApi.gun).toHaveBeenLastCalledWith('2026-10-31')
    const dugmeler = w.findAll('.arac-cubugu button')
    await dugmeler[1]!.trigger('click') // Sonraki gün
    await flushPromises()
    expect(router.currentRoute.value.query.tarih).toBe('2026-11-01')
    expect(atamaApi.gun).toHaveBeenLastCalledWith('2026-11-01')
    const yazdir = w.get('a[target="_blank"]')
    expect(yazdir.attributes('href')).toContain('/yazdir/gun/2026-11-01')
    await dugmeler[0]!.trigger('click') // Önceki gün
    await flushPromises()
    expect(router.currentRoute.value.query.tarih).toBe('2026-10-31')
  })

  it('sefer seçilince aday ucu çağrılır; aria-selected işaretlenir; Enter ile de seçilir', async () => {
    const { w } = await kur()
    expect(atamaApi.adaylar).not.toHaveBeenCalled()
    expect(w.text()).toContain('soldan bir sefer seçin')
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(atamaApi.adaylar).toHaveBeenCalledWith(2, 'ASIL')
    expect(satirlar(w)[1]!.attributes('aria-selected')).toBe('true')
    expect(w.text()).toContain('Aday Bir')
    await satirlar(w)[3]!.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(atamaApi.adaylar).toHaveBeenLastCalledWith(4, 'ASIL')
    // rol değişince aynı sefer için YARDIMCI adayları istenir
    await w.findAll('input[type="radio"]')[1]!.setValue(true)
    await flushPromises()
    expect(atamaApi.adaylar).toHaveBeenLastCalledWith(4, 'YARDIMCI')
  })

  it('atama sonrası gün listesi ve adaylar yenilenir, "Atama yapıldı." bildirilir', async () => {
    // K8 (OK-34): temiz aday artık onay diyaloğu açmadan atanır; aynı `ata` gövdesi, başarı Geri al şeridiyle bildirilir
    vi.mocked(atamaApi.ata).mockResolvedValue({
      atamaId: 55,
      ad: 'Aday Bir',
      sicil: 'D007',
    } as Awaited<ReturnType<typeof atamaApi.ata>>)
    const { w } = await kur()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(atamaApi.gun).toHaveBeenCalledTimes(1)
    expect(atamaApi.adaylar).toHaveBeenCalledTimes(1)

    await w.get('tr.aday').trigger('click')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)

    expect(atamaApi.ata).toHaveBeenCalledWith(2, { makinistId: 7, rol: 'ASIL', gerekce: undefined })
    expect(atamaApi.gun).toHaveBeenCalledTimes(2)
    expect(atamaApi.adaylar).toHaveBeenCalledTimes(2)
    expect(w.get('[role="status"]').text()).toContain('Atandı: Aday Bir (D007)')
    expect(w.get('[data-test="geri-al"]').text()).toBe('Geri al')
  })

  it('dolu rolde aday seçmek "değiştir" diyaloğu açar ve neden gönderir', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue({} as Awaited<ReturnType<typeof atamaApi.ata>>)
    const { w } = await kur()
    await satirlar(w)[0]!.trigger('click') // Ali Veli ASIL, Veli Yardım YARDIMCI
    await flushPromises()
    await w.get('tr.aday').trigger('click')
    expect(w.get('dialog').text()).toContain('Makinisti değiştir')
    expect(w.get('dialog').text()).toContain('Ali Veli (D001)')
    await w.get('#atama-neden').setValue('BELGE')
    await w.get('dialog form').trigger('submit')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledWith(1, {
      makinistId: 7,
      rol: 'ASIL',
      gerekce: undefined,
      neden: 'BELGE',
      nedenNotu: undefined,
    })
  })

  it('Kaldır: neden diyaloğu, ardından DELETE ve yenileme', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined)
    const { w } = await kur()
    await w.get('button[aria-label="Veli Yardım (D002) atamasını kaldır"]').trigger('click')
    await flushPromises()
    expect(w.get('dialog').text()).toContain('Atamayı kaldır')
    await w.get('#atama-neden').setValue('IZIN')
    await w.get('dialog form').trigger('submit')
    await flushPromises()
    expect(atamaApi.kaldir).toHaveBeenCalledWith(1, {
      rol: 'YARDIMCI',
      neden: 'IZIN',
      nedenNotu: undefined,
    })
    expect(atamaApi.gun).toHaveBeenCalledTimes(2)
    expect(w.get('[role="status"]').text()).toBe('Atama kaldırıldı.')
  })

  describe('tek sütun düzeni (BUG-9)', () => {
    const kaydir = vi.fn()
    function ekran(dar: boolean) {
      // hareket azaltma tercihi (prefers-reduced-motion) kapalı: yalnız tek sütun sorgusu dar ekrana göre eşleşir
      vi.stubGlobal('matchMedia', (q: string) => ({
        matches: q.includes('reduced-motion') ? false : dar,
        media: q,
      }))
      Element.prototype.scrollIntoView = kaydir
    }
    afterEach(() => {
      vi.unstubAllGlobals()
      delete (Element.prototype as Partial<Element>).scrollIntoView
      kaydir.mockReset()
    })

    it('dar ekranda sefer seçilince aday paneli görünür kılınır; dön bağlantısı listeye kaydırır', async () => {
      ekran(true)
      const { w } = await kur()
      await satirlar(w)[1]!.trigger('click')
      await flushPromises()
      expect(kaydir).toHaveBeenCalledTimes(1)
      expect(kaydir.mock.contexts[0]).toBe(w.get('aside').element)
      expect(kaydir).toHaveBeenLastCalledWith({ block: 'start', behavior: 'smooth' })
      await w.get('button.listeye-don').trigger('click')
      expect(kaydir).toHaveBeenCalledTimes(2)
      expect(kaydir.mock.contexts[1]).toBe(w.get('.atama-izgara .tablo-kap').element)
    })

    it('geniş ekranda (iki sütun) kaydırma yapılmaz', async () => {
      ekran(false)
      const { w } = await kur()
      await satirlar(w)[1]!.trigger('click')
      await flushPromises()
      expect(kaydir).not.toHaveBeenCalled()
    })

    it('scrollIntoView olmayan ortamda (jsdom) hata vermez', async () => {
      vi.stubGlobal('matchMedia', () => ({ matches: true }))
      const { w } = await kur()
      await satirlar(w)[1]!.trigger('click')
      await flushPromises()
      await w.get('button.listeye-don').trigger('click')
      expect(w.text()).toContain('Aday Bir')
    })
  })

  it('gün yüklenemezse hata gösterilir', async () => {
    const { ApiHata } = await import('@/api/istemci')
    vi.mocked(atamaApi.gun).mockRejectedValue(new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'))
    const { w } = await kur()
    expect(w.get('[role="alert"]').text()).toBe('Sunucuya ulaşılamadı.')
  })
})
