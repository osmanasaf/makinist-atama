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
    adayYakinlik: vi.fn(),
    gunYakinlik: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type Aday, type GunListesi, type YakinlikYaniti } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const asil = makinist()
const yrd = makinist({
  atamaId: 11,
  rol: 'YARDIMCI',
  makinistId: 2,
  sicil: 'D002',
  ad: 'Veli Yardım',
})

const gunVerisi = (): GunListesi => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [
    satir(1, { makinistler: [asil, yrd] }),
    satir(2, { bosNeden: '0/3 uygun: dinlenme 3' }),
    satir(3, { durum: 'IPTAL' }),
  ],
})
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

const yorgunluk = { pencereGun: 7, toplamDk: 640, geceNobetSayisi: 0, enKisaDinlenmeDk: null }
const isaret = {
  kod: 'K-13',
  olculen: 640,
  sinir: 660,
  kalan: 20,
  birim: 'dk',
  metin: 'Günlük süre (12 Eki 2026): 10 s 40 dk / 11 s',
}
const esikler = { pencereGun: 7, toplamSaat: 40, geceAdet: 3, dinlenmeSaat: 12 }
const gunYaniti = (aktif = true): YakinlikYaniti => ({
  marjAktif: aktif,
  esikler,
  satirlar: [
    { makinistId: 1, atamaId: 10, yakinlik: [isaret], yorgunluk },
    { makinistId: 2, atamaId: 11, yakinlik: [], yorgunluk },
  ],
})
const adayYaniti = (): YakinlikYaniti => ({
  marjAktif: true,
  esikler,
  satirlar: [{ makinistId: 7, atamaId: null, yakinlik: [isaret], yorgunluk }],
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
const satirlar = (w: Awaited<ReturnType<typeof kur>>) => w.findAll('tr.sefer-satiri')
const gorevli = (w: Awaited<ReturnType<typeof kur>>, sefer: number, i: number) =>
  satirlar(w)[sefer]!.findAll('.gorevli')[i]!

describe('AtamaMasasi: sınıra yakınlık (F3, MK-176)', () => {
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
    vi.mocked(atamaApi.gunYakinlik).mockImplementation(() => Promise.resolve(gunYaniti()))
    vi.mocked(atamaApi.adayYakinlik).mockImplementation(() => Promise.resolve(adayYaniti()))
  })

  it('gün başına TEK istek; yakınlığı olan atanan makinistin satırında metinli bilgi işareti (title + erişilebilir ad), olmayanda yok', async () => {
    const w = await kur()
    expect(atamaApi.gunYakinlik).toHaveBeenCalledTimes(1)
    expect(atamaApi.gunYakinlik).toHaveBeenCalledWith('2026-10-12')
    const isaretli = gorevli(w, 0, 0).get('[data-test="yakinlik"]')
    expect(isaretli.attributes('title')).toBe(
      'Sınıra yakın: Günlük süre (12 Eki 2026): 10 s 40 dk / 11 s',
    )
    expect(isaretli.text()).toContain('Sınıra yakın') // gizli metin: renk/simge tek başına değil
    expect(isaretli.find('.gorunmez').exists()).toBe(true)
    expect(gorevli(w, 0, 1).find('[data-test="yakinlik"]').exists()).toBe(false)
    expect(satirlar(w)[1]!.find('[data-test="yakinlik"]').exists()).toBe(false)
  })

  it('OK-47b: işaret yalnız hover title değil, GÖRÜNÜR kısa rozet ("Sınıra yakın", gizli metin değil) + erişilebilir ad; satır odaklanınca ölçüm metni görünür', async () => {
    const w = await kur()
    const isaretli = gorevli(w, 0, 0).get('[data-test="yakinlik"]')
    const gorunur = isaretli.get('.rozet-bilgi .rozet-metin')
    expect(gorunur.text()).toBe('Sınıra yakın')
    expect(gorunur.element.closest('.gorunmez')).toBeNull()
    expect(isaretli.text()).toContain('Günlük süre (12 Eki 2026): 10 s 40 dk / 11 s') // erişilebilir ad
    expect(w.find('[data-test="yakinlik-ayrinti"]').exists()).toBe(false)
    await satirlar(w)[0]!.trigger('focusin') // klavyeyle satıra gelen de ölçümü okur (hover gerekmez)
    const ayrinti = satirlar(w)[0]!.get('[data-test="yakinlik-ayrinti"]')
    expect(ayrinti.text()).toContain('10 s 40 dk / 11 s')
    expect(satirlar(w)[2]!.find('[data-test="yakinlik-ayrinti"]').exists()).toBe(false)
  })

  it('satırın durumu DEĞİŞMEZ: kırmızı/uyarı sınıfı ve neden yok; yakınlıksız çizimle aynı sınıflar ve metin dışında aynı yapı', async () => {
    vi.mocked(atamaApi.gunYakinlik).mockImplementation(() => Promise.resolve(gunYaniti(false)))
    const kapali = await kur()
    vi.mocked(atamaApi.gunYakinlik).mockImplementation(() => Promise.resolve(gunYaniti(true)))
    const acik = await kur()
    expect(kapali.find('[data-test="yakinlik"]').exists()).toBe(false)
    expect(acik.find('[data-test="yakinlik"]').exists()).toBe(true)
    expect(satirlar(acik).map((r) => r.classes())).toEqual(satirlar(kapali).map((r) => r.classes()))
    expect(acik.findAll('.gorevli').map((g) => g.classes())).toEqual(
      kapali.findAll('.gorevli').map((g) => g.classes()),
    )
    expect(acik.find('.kirmizi-neden').exists()).toBe(false)
    expect(acik.get('[data-test="yakinlik"]').html()).not.toMatch(
      /kirmizi|engel|rozet-uyari|gordum-uyarili/,
    )
    expect(acik.get('[data-test="atanmamis-sayac"]').text()).toBe(
      kapali.get('[data-test="atanmamis-sayac"]').text(),
    )
  })

  it('istek hatası ekranı bozmaz: masa ve aday listesi normal çizilir, işaret yok, hata mesajı yok', async () => {
    const uyarilar = (x: Awaited<ReturnType<typeof kur>>) =>
      x.findAll('[role="alert"]').map((e) => e.text())
    const temel = uyarilar(await kur()) // yakınlık hatasızken zaten görünen uyarılar (bu testte eksik sahte API'lerden)
    vi.mocked(atamaApi.gunYakinlik).mockRejectedValue(new Error('500'))
    vi.mocked(atamaApi.adayYakinlik).mockRejectedValue(new Error('500'))
    const w = await kur()
    expect(satirlar(w)).toHaveLength(3)
    expect(w.find('[data-test="yakinlik"]').exists()).toBe(false)
    expect(uyarilar(w)).toEqual(temel)
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Aday Bir')
    expect(w.find('[data-test="yakinlik-notu"]').exists()).toBe(false)
    expect(uyarilar(w)).toEqual(temel)
  })

  it('aday ucu sefer + rolle çağrılır; aday satırında işaret ve yorgunluk; rol değişince yeniden', async () => {
    const w = await kur()
    expect(atamaApi.adayYakinlik).not.toHaveBeenCalled()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(atamaApi.adayYakinlik).toHaveBeenCalledWith(2, 'ASIL')
    const satir7 = w.get('tr.aday')
    expect(satir7.find('[data-test="yakinlik-isareti"]').text()).toContain('10 s 40 dk / 11 s')
    expect(satir7.find('[data-test="yorgunluk"]').exists()).toBe(true)
    expect(satir7.classes()).toContain('aday-basari') // "Uygun" kalır
    await w.findAll('input[type="radio"]')[1]!.setValue(true)
    await flushPromises()
    expect(atamaApi.adayYakinlik).toHaveBeenLastCalledWith(2, 'YARDIMCI')
  })

  it('atama sonrası yakınlık da yenilenir (gün başına bir istek daha)', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue({
      atamaId: 55,
      ad: 'Aday Bir',
      sicil: 'D007',
    } as Awaited<ReturnType<typeof atamaApi.ata>>)
    const w = await kur()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    await w.get('tr.aday').trigger('click')
    await flushPromises()
    expect(atamaApi.gunYakinlik).toHaveBeenCalledTimes(2)
  })

  it('O-1: yakınlık ucu yavaş/takılı olsa da aday listesi hemen çizilir ve seçilebilir; yakınlık geç gelirse işaretler sonradan belirir', async () => {
    let bitir!: (y: YakinlikYaniti) => void
    vi.mocked(atamaApi.adayYakinlik).mockReturnValue(new Promise((c) => (bitir = c)))
    const w = await kur()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    const aday = w.get('tr.aday')
    expect(aday.text()).toContain('Aday Bir') // liste yakınlığı beklemeden geldi
    expect(w.find('[aria-label="Aday personel"][aria-busy="true"]').exists()).toBe(false)
    expect(aday.find('[data-test="yakinlik-isareti"]').exists()).toBe(false)
    bitir(adayYaniti())
    await flushPromises()
    expect(w.get('tr.aday').find('[data-test="yakinlik-isareti"]').text()).toContain('10 s 40 dk')
  })

  it('O-1: sefer değişince önceki seferin yakınlığı yeni adaylara yapışmaz (yeni yakınlık gelene kadar işaret yok)', async () => {
    const w = await kur()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(w.get('tr.aday').find('[data-test="yakinlik-isareti"]').exists()).toBe(true)
    vi.mocked(atamaApi.adayYakinlik).mockReturnValue(new Promise(() => {})) // ikinci seferin yakınlığı gelmiyor
    await satirlar(w)[2]!.trigger('click')
    await flushPromises()
    expect(w.find('tr.aday').exists()).toBe(true)
    expect(w.find('tr.aday [data-test="yakinlik-isareti"]').exists()).toBe(false)
  })

  it('marj kapalı yanıtı (marjAktif=false): ne masada ne aday panelinde işaret ya da yorgunluk', async () => {
    vi.mocked(atamaApi.gunYakinlik).mockImplementation(() =>
      Promise.resolve({ marjAktif: false, esikler, satirlar: [] }),
    )
    vi.mocked(atamaApi.adayYakinlik).mockImplementation(() =>
      Promise.resolve({ marjAktif: false, esikler, satirlar: [] }),
    )
    const w = await kur()
    await satirlar(w)[1]!.trigger('click')
    await flushPromises()
    expect(w.find('[data-test="yakinlik"]').exists()).toBe(false)
    expect(w.find('[data-test="yakinlik-notu"]').exists()).toBe(false)
    expect(w.text()).not.toContain('Yorgunluk')
  })
})
