import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: { gorevler: vi.fn(), gordum: vi.fn(), intikalGordum: vi.fn(), olayiKapat: vi.fn() },
}))

import BenimGorevler from './BenimGorevler.vue'
import { benimApi, type BenimGorev, type BenimGorevler as Yanit } from './benimApi'
import { onayBekleyen } from './rozet'

// "Şimdi" 07:00 İstanbul (04:00Z); bugün 2026-10-23.
const SIMDI = Date.parse('2026-10-23T04:00:00Z')
const gorev = (p: Partial<BenimGorev> = {}): BenimGorev => ({
  atamaId: 1,
  seferId: 1,
  tarih: '2026-10-23',
  sira: 1,
  trenNo: 'T1',
  rol: 'ASIL',
  durum: 'PLANLI',
  kalkisGar: 'Ankara',
  varisGar: 'Sivas',
  planlananKalkis: '2026-10-23T05:00:00Z', // 08:00
  planlananVaris: '2026-10-23T07:00:00Z', // 10:00
  guncelKalkis: '2026-10-23T05:00:00Z',
  guncelVaris: '2026-10-23T07:00:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  varisErtesiGun: false,
  aracTipi: 'YHT',
  hat: 'AS',
  km: 250,
  esMakinist: null,
  gordu: 'GORULDU',
  gorduZamani: null,
  ozet: 'ozet',
  eskiOzet: null,
  ...p,
})
const kalem = (olayId: number) =>
  ({
    olayId,
    tur: 'KALDIRILDI',
    trenNo: 'KLD',
    tarih: '2026-10-23',
    ozet: 'Kaldırıldı',
    nedenEtiketi: null,
  }) as never
const yanit = (gorevler: BenimGorev[], ek: Partial<Yanit> = {}): Yanit => ({
  bugun: '2026-10-23',
  ufukGun: 7,
  gorevler,
  kaldirilanlar: [],
  ...ek,
})

const kurulan: VueWrapper[] = []
async function kur(y: Yanit) {
  vi.mocked(benimApi.gorevler).mockResolvedValue(y)
  const w = mount(BenimGorevler, { attachTo: document.body })
  kurulan.push(w)
  await flushPromises()
  return w
}
const sonraki = (w: VueWrapper) => w.find('section[aria-label="Sonraki görev"]')
const degisti = (w: VueWrapper) => w.find('section[aria-label="Değişti: yeniden onaylayın"]')
const yeni = (w: VueWrapper) => w.find('section[aria-label="Yeni: onayınızı bekliyor"]')
const onay = (w: VueWrapper) => w.find('section[data-onay]')
const bolumler = (w: VueWrapper) =>
  w.findAll('section[aria-label]').map((s) => s.attributes('aria-label'))
const gordumDugmeleri = (k: { findAll: VueWrapper['findAll'] }) =>
  k.findAll('button').filter((b) => b.text() === 'Gördüm')

describe('BenimGorevler: ilk ekran (H8, AK-7.x)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(SIMDI)
    onayBekleyen.value = 0
  })
  afterEach(() => {
    kurulan.splice(0).forEach((w) => w.unmount())
    vi.useRealTimers()
  })

  it('AK-7.1: "Sonraki görev" kartı: saat, tren, güzergâh, rol ve "1 sa sonra"; geçmiş ve iptal atlanır', async () => {
    const w = await kur(
      yanit([
        gorev({
          atamaId: 1,
          trenNo: 'GECMIS',
          guncelKalkis: '2026-10-23T01:00:00Z',
          guncelVaris: '2026-10-23T03:00:00Z',
        }),
        gorev({ atamaId: 2, trenNo: 'IPT', durum: 'IPTAL' }),
        gorev({ atamaId: 3, trenNo: 'T3' }),
        gorev({
          atamaId: 4,
          trenNo: 'T4',
          guncelKalkis: '2026-10-23T09:00:00Z',
          guncelVaris: '2026-10-23T11:00:00Z',
        }),
      ]),
    )
    const k = sonraki(w)
    expect(k.exists()).toBe(true)
    expect(k.text()).toContain('08:00 – 10:00')
    expect(k.text()).toContain('T3')
    expect(k.text()).not.toContain('T4')
    expect(k.text()).not.toContain('GECMIS')
    expect(k.text()).toContain('Ankara → Sivas')
    expect(k.text()).toContain('Asıl')
    expect(k.text()).toContain('1 sa sonra')
    expect(k.find('article').exists()).toBe(false) // kart sayımı (article) değişmez
  })

  it('AK-7.1: sefer sürüyorsa "Şu an görevde"; onay bekliyorsa "Onay bekliyor" rozeti', async () => {
    vi.setSystemTime(Date.parse('2026-10-23T05:30:00Z'))
    const w = await kur(yanit([gorev({ gordu: 'DEGISTI', eskiOzet: 'eski' })]))
    expect(sonraki(w).text()).toContain('Şu an görevde')
    expect(sonraki(w).text()).toContain('Onay bekliyor')
  })

  it('AK-7.1: sıradaki görev yoksa kart yok (mevcut boş mesaj kalır)', async () => {
    const w = await kur(yanit([]))
    expect(sonraki(w).exists()).toBe(false)
    expect(w.text()).toContain('Önümüzdeki 7 gün için göreviniz yok')
  })

  it('AK-7.2: DEĞİŞTİ en üstte, sonra Sonraki görev, sonra YENİ; onay bekleyenler gün grubunda TEKRAR çizilmez', async () => {
    const w = await kur(
      yanit([
        gorev({ atamaId: 1, trenNo: 'YENI1', gordu: 'YENI' }),
        gorev({
          atamaId: 2,
          trenNo: 'TAMAM',
          gordu: 'GORULDU',
          guncelKalkis: '2026-10-23T04:30:00Z', // OK-41b: GORULDU olan sonraki görev olsun, YENI1 Yeni bölümünde kalsın
          guncelVaris: '2026-10-23T08:00:00Z',
        }),
        gorev({ atamaId: 3, trenNo: 'DEG1', gordu: 'DEGISTI', eskiOzet: 'eski', ozet: 'yeni' }),
      ]),
    )
    expect(bolumler(w)).toEqual([
      'Değişti: yeniden onaylayın',
      'Sonraki görev',
      'Yeni: onayınızı bekliyor',
      'Bugün',
    ])
    const kartlar = [...degisti(w).findAll('article'), ...yeni(w).findAll('article')]
    expect(kartlar).toHaveLength(2)
    expect(kartlar[0]!.text()).toContain('DEG1')
    expect(kartlar[0]!.text()).toContain('DEĞİŞTİ')
    expect(kartlar[1]!.text()).toContain('YENI1')
    expect(kartlar.map((k) => gordumDugmeleri(k).length)).toEqual([1, 1])
    const bugun = w.get('section[aria-label="Bugün"]')
    expect(bugun.findAll('article')).toHaveLength(1)
    expect(bugun.text()).toContain('TAMAM')
    expect(bugun.text()).not.toContain('DEG1')
    expect(w.findAll('article')).toHaveLength(3) // toplam kart sayısı aynı
  })

  it('AK-7.2: serbest intikal onayı da onay bölümünde; elinden alınanlar onay bölümünün ALTINDA', async () => {
    const w = await kur(
      yanit([gorev({ gordu: 'GORULDU' })], {
        intikaller: [
          {
            id: 9,
            kalkisGar: 'Ankara',
            varisGar: 'Eskişehir',
            kalkis: '2026-10-23T09:00:00+03:00', // OK-41b: görev (08:00) önce gelsin, intikal Yeni bölümünde kalsın
            varis: '2026-10-23T10:30:00+03:00',
            aracTuru: 'SERVIS',
            durum: 'PLANLI',
            gordu: 'YENI',
            gorduZamani: null,
            ozet: 'ozet',
            eskiOzet: null,
          },
        ],
        kaldirilanlar: [kalem(5)],
      }),
    )
    expect(bolumler(w).slice(0, 3)).toEqual([
      'Sonraki görev',
      'Yeni: onayınızı bekliyor',
      'Elinizden alınan görevler',
    ])
    expect(onay(w).find('[data-test=intikal-karti]').exists()).toBe(true)
  })

  describe('OK-41b: görülmemiş sonraki görev', () => {
    const sonrakiGorev = () => gorev({ atamaId: 1, trenNo: 'SON1', gordu: 'YENI', ozet: 'oz1' })
    const sonraSefer = () =>
      gorev({
        atamaId: 2,
        trenNo: 'SON2',
        gordu: 'YENI',
        guncelKalkis: '2026-10-23T09:00:00Z',
        guncelVaris: '2026-10-23T11:00:00Z',
      })

    it('Gördüm düğmesi Sonraki görev kartında; o görev Yeni listesinde tekrar etmez', async () => {
      const w = await kur(yanit([sonrakiGorev(), sonraSefer()]))
      expect(sonraki(w).text()).toContain('SON1')
      expect(gordumDugmeleri(sonraki(w))).toHaveLength(1)
      const kartlar = yeni(w).findAll('article')
      expect(kartlar).toHaveLength(1)
      expect(kartlar[0]!.text()).toContain('SON2')
      expect(yeni(w).text()).not.toContain('SON1')
      expect(w.findAll('article')).toHaveLength(1)
    })

    it('kartın Gördüm düğmesi görevi onaylar ve odak kartta kalır (BODY değil)', async () => {
      vi.mocked(benimApi.gordum).mockResolvedValue(undefined as never)
      const a = sonrakiGorev()
      const b = sonraSefer()
      const w = await kur(yanit([a, b]))
      vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([{ ...a, gordu: 'GORULDU' }, b]))
      const dugme = gordumDugmeleri(sonraki(w))[0]!
      ;(dugme.element as HTMLElement).focus()
      await dugme.trigger('click')
      await flushPromises()
      expect(benimApi.gordum).toHaveBeenCalledWith(1, 'oz1')
      expect(document.activeElement).not.toBe(document.body)
      expect(sonraki(w).element.contains(document.activeElement)).toBe(true)
      expect(gordumDugmeleri(sonraki(w))).toHaveLength(0)
    })

    it('serbest intikal sonraki görevse aynı kural (intikalGordum)', async () => {
      vi.mocked(benimApi.intikalGordum).mockResolvedValue(undefined as never)
      const w = await kur(
        yanit([sonraSefer()], {
          intikaller: [
            {
              id: 9,
              kalkisGar: 'Ankara',
              varisGar: 'Eskişehir',
              kalkis: '2026-10-23T07:00:00+03:00',
              varis: '2026-10-23T08:30:00+03:00',
              aracTuru: 'SERVIS',
              durum: 'PLANLI',
              gordu: 'YENI',
              gorduZamani: null,
              ozet: 'ioz',
              eskiOzet: null,
            },
          ],
        }),
      )
      expect(sonraki(w).text()).toContain('Ankara → Eskişehir')
      expect(yeni(w).find('[data-test=intikal-karti]').exists()).toBe(false)
      await gordumDugmeleri(sonraki(w))[0]!.trigger('click')
      expect(benimApi.intikalGordum).toHaveBeenCalledWith(9, 'ioz')
    })

    it('DEĞİŞTİ görev sonraki olsa da onay yalnız Değişti bölümünde (kartta Gördüm yok)', async () => {
      const w = await kur(yanit([gorev({ gordu: 'DEGISTI', eskiOzet: 'eski' })]))
      expect(gordumDugmeleri(sonraki(w))).toHaveLength(0)
      expect(gordumDugmeleri(degisti(w))).toHaveLength(1)
    })
  })

  it('AK-7.4: bugün için görev yokken "Bugün" başlığı + "Bugün göreviniz yok."; diğer boş günler çizilmez', async () => {
    const w = await kur(yanit([gorev({ tarih: '2026-10-24' })]))
    expect(w.get('section[aria-label="Bugün"]').text()).toContain('Bugün göreviniz yok.')
    expect(w.findAll('h2').map((h) => h.text().split(' ')[0])).toContain('Yarın')
    expect(w.text()).not.toContain('Yarın göreviniz yok.')
  })

  it('AK-7.4: bugünün tüm görevleri onay bekliyorsa "yok" satırı çıkmaz (görev var)', async () => {
    const w = await kur(yanit([gorev({ gordu: 'YENI' })]))
    expect(w.text()).not.toContain('Bugün göreviniz yok.')
  })

  it('AK-7.4: hiç görev yokken "Bugün göreviniz yok." tekrarlanmaz (tek boş mesaj)', async () => {
    const w = await kur(yanit([]))
    expect(w.text()).not.toContain('Bugün göreviniz yok.')
  })

  it('AK-7.3: "Gördüm" sonrası kart kalkarsa odak sıradaki onay kartına, onay kalmazsa sayfa başlığına gider', async () => {
    vi.mocked(benimApi.gordum).mockResolvedValue(undefined as never)
    const a = gorev({ atamaId: 1, trenNo: 'AAA', gordu: 'DEGISTI', eskiOzet: 'e' })
    const b = gorev({ atamaId: 2, trenNo: 'BBB', gordu: 'YENI' })
    const w = await kur(yanit([a, b]))
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([{ ...a, gordu: 'GORULDU' }, b]))
    const ilk = gordumDugmeleri(onay(w))[0]!
    ;(ilk.element as HTMLElement).focus()
    await ilk.trigger('click')
    await flushPromises()
    expect(document.activeElement?.tagName).toBe('ARTICLE')
    expect(document.activeElement?.textContent).toContain('BBB')

    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        { ...a, gordu: 'GORULDU' },
        { ...b, gordu: 'GORULDU' },
      ]),
    )
    const son = gordumDugmeleri(onay(w))[0]!
    ;(son.element as HTMLElement).focus()
    await son.trigger('click')
    await flushPromises()
    expect(document.activeElement?.tagName).toBe('H1')
  })

  it('AK-7.5: onay bekleyen sayısı sekme rozeti için paylaşılır (yeni + değişti + elinden alınan)', async () => {
    await kur(
      yanit(
        [
          gorev({ atamaId: 1, gordu: 'YENI' }),
          gorev({ atamaId: 2, gordu: 'DEGISTI' }),
          gorev({ atamaId: 3 }),
        ],
        { kaldirilanlar: [kalem(5)] },
      ),
    )
    expect(onayBekleyen.value).toBe(3)
  })

  it('AK-7.5: Konaklamam / Takas kısayolları küçük bağlantı (DOM ve data-test aynı), görev listesinin ALTINDA', async () => {
    const w = await kur(yanit([gorev({})]))
    expect(w.get('[data-test=konaklama-baglanti]').classes()).toContain('benim-kisayol')
    expect(w.find('[data-test=takas-baglanti]').exists()).toBe(true)
    const html = w.html()
    expect(html.indexOf('data-test="konaklama-baglanti"')).toBeGreaterThan(
      html.indexOf('aria-label="Bugün"'),
    )
  })

  it('"Bırak" ikincil (hayalet) tonda ve "Takas iste"den sonra', async () => {
    const w = await kur(yanit([gorev({})]))
    const dugmeler = w.findAll('.benim-takas-dugmeleri button')
    expect(dugmeler.map((d) => d.text())).toEqual(['Takas iste', 'Bırak'])
    expect(dugmeler[1]!.classes()).toContain('btn-hayalet')
    expect(dugmeler[0]!.classes()).not.toContain('btn-hayalet')
  })
})
