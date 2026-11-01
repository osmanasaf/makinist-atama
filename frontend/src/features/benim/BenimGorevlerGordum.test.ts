import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: { gorevler: vi.fn(), gordum: vi.fn(), olayiKapat: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import BenimGorevler from './BenimGorevler.vue'
import { benimApi, type BenimGorev, type BenimGorevler as Yanit, type BenimKalem } from './benimApi'
import { rozetMetni } from './rozet'
import { YOKLAMA_MS } from './useYoklama'

function gorev(p: Partial<BenimGorev>): BenimGorev {
  return {
    atamaId: 1,
    seferId: 1,
    tarih: '2026-10-12',
    sira: 1,
    trenNo: 'T1',
    rol: 'ASIL',
    durum: 'PLANLI',
    kalkisGar: 'Ankara',
    varisGar: 'Sivas',
    planlananKalkis: '2026-10-12T05:00:00Z',
    planlananVaris: '2026-10-12T07:00:00Z',
    guncelKalkis: '2026-10-12T05:00:00Z',
    guncelVaris: '2026-10-12T07:00:00Z',
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Ankara-Sivas',
    km: 250,
    esMakinist: null,
    gordu: 'YENI',
    gorduZamani: null,
    ozet: 'Asıl · Ankara 12.10 08:00 → Sivas 12.10 10:00',
    eskiOzet: null,
    ...p,
  }
}

const kalem = (p: Partial<BenimKalem> = {}): BenimKalem => ({
  olayId: 5,
  tur: 'DEGISTI_BASKASINA',
  seferId: 9,
  trenNo: 'T9',
  tarih: '2026-10-13',
  ozet: 'Asıl · Ankara 13.10 08:00 → Sivas 13.10 10:00',
  nedenEtiketi: 'HASTALIK',
  zaman: '2026-10-12T05:00:00Z',
  ...p,
})

const yanit = (gorevler: BenimGorev[], kaldirilanlar: BenimKalem[] = []): Yanit => ({
  bugun: '2026-10-12',
  ufukGun: 7,
  gorevler,
  kaldirilanlar,
})

// Yoklama belgeye dinleyici ekler: her test sonunda kaldırılmazsa sonraki testler önceki bileşenlerin yoklamasını da sayar.
const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur() {
  const w = mount(BenimGorevler)
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimGorevler: gördüm', () => {
  beforeEach(() => vi.resetAllMocks())

  it('YENİ kartta Gördüm düğmesi; basınca API çağrılır ve kart GÖRÜLDÜ olur', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValueOnce(yanit([gorev({})]))
    const w = await kur()
    expect(w.get('article').text()).toContain('YENİ')
    expect(w.get('article button').text()).toBe('Gördüm')

    vi.mocked(benimApi.gordum).mockResolvedValue(gorev({}))
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([gorev({ gordu: 'GORULDU', gorduZamani: '2026-10-12T06:14:00Z' })]),
    )
    await w.get('article button').trigger('click')
    await flushPromises()

    expect(benimApi.gordum).toHaveBeenCalledWith(1, 'Asıl · Ankara 12.10 08:00 → Sivas 12.10 10:00') // ekranda görülen özetle (bayat ekran 409 verir)
    expect(w.get('article').text()).toContain('Gördünüz')
    expect(w.get('article').text()).toContain('12.10.2026 09:14')
    expect(w.find('article button').exists()).toBe(false)
  })

  it('DEĞİŞTİ kartı eski → yeni özeti gösterir; yeniden onay istenir', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        gorev({
          gordu: 'DEGISTI',
          eskiOzet: 'Asıl · Ankara 12.10 08:10 → Sivas 12.10 10:00',
          ozet: 'Asıl · Ankara 12.10 08:35 → Sivas 12.10 10:25',
        }),
      ]),
    )
    const w = await kur()
    const metin = w.get('article').text()
    expect(metin).toContain('DEĞİŞTİ')
    expect(metin).toContain('Eski: Asıl · Ankara 12.10 08:10')
    expect(metin).toContain('Yeni: Asıl · Ankara 12.10 08:35')
    expect(w.get('article button').text()).toBe('Gördüm')
  })

  it('durum rozetleri ton + simge + metin taşır; kart durum sınıfı alır (emoji yok)', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([
        gorev({ atamaId: 1 }),
        gorev({ atamaId: 2, gordu: 'DEGISTI', eskiOzet: 'eski', ozet: 'yeni' }),
        gorev({ atamaId: 3, gordu: 'GORULDU', gorduZamani: '2026-10-12T06:14:00Z' }),
      ]),
    )
    const w = await kur()
    const kartlar = w.findAll('article')
    // H8: onay bekleyenler üstte ve DEĞİŞTİ önce; Gördünüz kartı gün grubunda kalır
    expect(kartlar.map((k) => k.classes().filter((c) => ['yeni', 'degisti'].includes(c)))).toEqual([
      ['degisti'],
      ['yeni'],
      [],
    ])
    const yeni = kartlar[1]!.get('.rozet-bilgi')
    expect(yeni.text()).toBe('YENİ')
    expect(yeni.get('svg').attributes('data-simge')).toBe('bilgi')
    const degisti = kartlar[0]!.get('.rozet-uyari')
    expect(degisti.text()).toContain('DEĞİŞTİ')
    expect(degisti.get('svg').attributes('data-simge')).toBe('uyari')
    expect(kartlar[2]!.get('.rozet-basari svg').attributes('data-simge')).toBe('basari')
    expect(w.text()).not.toMatch(/[🀀-🿿✅✓⛔⚠]/u)
  })

  it('çift tıklama tek istek gönderir (düğme işlem sürerken kapalı)', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([gorev({})]))
    let bitir!: () => void
    vi.mocked(benimApi.gordum).mockReturnValue(new Promise((c) => (bitir = () => c(gorev({})))))
    const w = await kur()

    await w.get('article button').trigger('click')
    await w.get('article button').trigger('click')
    expect(benimApi.gordum).toHaveBeenCalledTimes(1)
    expect(w.get('article button').attributes('disabled')).toBeDefined()
    bitir()
    await flushPromises()
  })

  it('başka makinistin / değişmiş görev (404): hata gösterir ve listeyi yeniler', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([gorev({})]))
    vi.mocked(benimApi.gordum).mockRejectedValue(
      new ApiHata(404, 'BULUNAMADI', 'Görev bulunamadı.'),
    )
    const w = await kur()
    await w.get('article button').trigger('click')
    await flushPromises()

    expect(w.get('[role=alert]').text()).toContain('Görev bulunamadı.')
    expect(benimApi.gorevler).toHaveBeenCalledTimes(2)
  })

  it('elinden alınan görev kalemleri: Başkasına verildi / Kaldırıldı, neden etiketi; Gördüm kapatır', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValueOnce(
      yanit(
        [],
        [kalem(), kalem({ olayId: 6, tur: 'KALDIRILDI', nedenEtiketi: 'IZIN', trenNo: 'T8' })],
      ),
    )
    const w = await kur()
    const kartlar = w.findAll('article.kalem')
    expect(kartlar).toHaveLength(2)
    expect(kartlar[0]!.text()).toContain('Başkasına verildi')
    expect(kartlar[0]!.text()).toContain('Neden: Hastalık')
    expect(kartlar[1]!.text()).toContain('Kaldırıldı')

    vi.mocked(benimApi.olayiKapat).mockResolvedValue(undefined)
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([], [kalem({ olayId: 6, tur: 'KALDIRILDI' })]),
    )
    await kartlar[0]!.get('button').trigger('click')
    await flushPromises()
    expect(benimApi.olayiKapat).toHaveBeenCalledWith(5)
    expect(w.findAll('article.kalem')).toHaveLength(1)
  })

  it('rozet bölgesi: Yeni / Değişti / Kaldırıldı sayıları', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit(
        [
          gorev({ atamaId: 1 }),
          gorev({ atamaId: 2, gordu: 'DEGISTI' }),
          gorev({ atamaId: 3, gordu: 'GORULDU' }),
        ],
        [kalem()],
      ),
    )
    await kur()
    expect(rozetMetni.value).toBe('3 görev listelendi. Yeni: 1, Değişti: 1, Kaldırıldı: 1')
  })
})

describe('BenimGorevler: yoklama', () => {
  let gorunurluk: 'visible' | 'hidden' = 'visible'

  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
    gorunurluk = 'visible'
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => gorunurluk,
    })
    vi.mocked(benimApi.gorevler).mockResolvedValue(yanit([gorev({})]))
  })
  afterEach(() => {
    vi.useRealTimers()
    delete (document as unknown as Record<string, unknown>).visibilityState
  })

  const degistir = (d: 'visible' | 'hidden') => {
    gorunurluk = d
    document.dispatchEvent(new Event('visibilitychange'))
  }

  it("60 sn'de bir sessizce yoklar (yükleniyor ekranına dönmez); sekme gizliyken durur; görünür olunca bir kez yeniler", async () => {
    const w = mount(BenimGorevler)
    kurulan.push(w)
    await vi.advanceTimersByTimeAsync(0)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(YOKLAMA_MS)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(2)
    expect(w.text()).not.toContain('Yükleniyor')

    degistir('hidden')
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS * 3)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(2) // gizliyken yoklama yok

    degistir('visible')
    await vi.advanceTimersByTimeAsync(0)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(3) // hemen bir kez
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(4) // yoklama sürüyor

    w.unmount()
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS * 2)
    expect(benimApi.gorevler).toHaveBeenCalledTimes(4) // kaldırılınca durur
  })

  it('yoklama hatası mevcut listeyi bozmaz', async () => {
    const w = mount(BenimGorevler)
    kurulan.push(w)
    await vi.advanceTimersByTimeAsync(0)
    vi.mocked(benimApi.gorevler).mockRejectedValue(
      new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'),
    )
    await vi.advanceTimersByTimeAsync(YOKLAMA_MS)
    expect(w.findAll('article')).toHaveLength(1)
    expect(w.find('[role=alert]').exists()).toBe(false)
  })

  it('QA BUG-8: Gördüm sonrası odak kartta kalır (düğme kalksa da BODY yerine düşmez) ve "Gördünüz" duyurulur', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValueOnce(yanit([gorev({})]))
    const w = mount(BenimGorevler, { attachTo: document.body })
    kurulan.push(w)
    await flushPromises()
    vi.mocked(benimApi.gordum).mockResolvedValue(gorev({}))
    vi.mocked(benimApi.gorevler).mockResolvedValue(
      yanit([gorev({ gordu: 'GORULDU', gorduZamani: '2026-10-12T06:14:00Z' })]),
    )
    const dugme = w.get('article button').element as HTMLElement
    dugme.focus()
    await w.get('article button').trigger('click')
    await flushPromises()
    expect(w.find('article button').exists()).toBe(false) // düğme kalktı
    // H8 (AK-7.3): kart "Onayınızı bekleyenler"den gün grubuna geçti; onay kalmadığından odak sayfa başlığına gider (BODY'ye düşmez)
    expect(document.activeElement).not.toBe(document.body)
    expect(document.activeElement).toBe(w.get('h1').element)
    expect(rozetMetni.value).toMatch(/^Gördünüz: T1. 1 görev listelendi/)
  })
})
