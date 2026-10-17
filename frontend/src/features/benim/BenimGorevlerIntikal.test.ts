import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./benimApi', () => ({
  benimApi: {
    gorevler: vi.fn(),
    gordum: vi.fn(),
    intikalGordum: vi.fn(),
    olayiKapat: vi.fn(),
    takasHedefler: vi.fn(),
    takasAc: vi.fn(),
  },
}))

import BenimGorevler from './BenimGorevler.vue'
import { rozetMetni } from './rozet'
import {
  benimApi,
  type BenimGorev,
  type BenimGorevler as Yanit,
  type BenimIntikal,
} from './benimApi'

const gorev = (p: Partial<BenimGorev> = {}): BenimGorev => ({
  atamaId: 1,
  seferId: 1,
  tarih: '2026-10-14',
  sira: 1,
  trenNo: 'T1234',
  rol: 'ASIL',
  durum: 'PLANLI',
  kalkisGar: 'Eskişehir',
  varisGar: 'Ankara',
  planlananKalkis: '2026-10-14T07:00:00Z', // 10:00
  planlananVaris: '2026-10-14T08:30:00Z',
  guncelKalkis: '2026-10-14T07:00:00Z',
  guncelVaris: '2026-10-14T08:30:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  varisErtesiGun: false,
  aracTipi: 'YHT',
  hat: 'AE',
  km: 245,
  esMakinist: null,
  gordu: 'GORULDU',
  gorduZamani: '2026-10-12T06:14:00Z',
  ozet: 'ozet',
  eskiOzet: null,
  ...p,
})
const intikal = (p: Partial<BenimIntikal> = {}): BenimIntikal => ({
  id: 1,
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
  kalkis: '2026-10-14T07:00:00+03:00', // 07:00 -> sefer 10:00'dan önce
  varis: '2026-10-14T08:30:00+03:00',
  aracTuru: 'SERVIS',
  durum: 'PLANLI',
  gordu: 'YENI',
  gorduZamani: null,
  ozet: 'İntikal · Ankara 14.10 07:00 → Eskişehir 14.10 08:30 · Servis',
  eskiOzet: null,
  ...p,
})

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur(gorevler: BenimGorev[], intikaller?: BenimIntikal[]) {
  const yanit: Yanit = { bugun: '2026-10-14', ufukGun: 7, gorevler, kaldirilanlar: [], intikaller }
  vi.mocked(benimApi.gorevler).mockResolvedValue(yanit)
  const w = mount(BenimGorevler, { attachTo: document.body })
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimGorevler: serbest intikal (MK-108)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('intikal kartı kalkış sırasıyla sefer kartlarına karışık gelir; "İntikal" etiketi, Takas iste / Bırak yok', async () => {
    const w = await kur([gorev()], [intikal()])
    const kartlar = w.findAll('article')

    expect(kartlar).toHaveLength(2)
    expect(kartlar[0]!.attributes('data-test')).toBe('intikal-karti') // 07:00 önce, sefer 10:00
    expect(kartlar[0]!.text()).toContain('Ankara → Eskişehir')
    expect(kartlar[0]!.text()).toContain('Servis')
    expect(kartlar[0]!.text()).toContain('sürüş göreviniz değildir')
    expect(kartlar[0]!.text()).toContain('YENİ')
    expect(kartlar[0]!.text()).not.toContain('Takas iste')
    expect(kartlar[0]!.text()).not.toContain('Bırak')
    expect(kartlar[1]!.text()).toContain('T1234')
  })

  it('yalnız intikali olan gün de görünür ("göreviniz yok" mesajı çıkmaz)', async () => {
    const w = await kur([], [intikal()])

    expect(w.findAll('article')).toHaveLength(1)
    expect(w.text()).not.toContain('göreviniz yok')
  })

  it('alt özet intikali de görev sayar; iptal edilen sayılmaz (UX-2)', async () => {
    await kur([gorev()], [intikal(), intikal({ id: 8, durum: 'IPTAL' })])

    expect(rozetMetni.value).toContain('2 görev listelendi')
  })

  it('eski yanıtta (intikaller alanı yok) ekran eskisi gibi çalışır', async () => {
    const w = await kur([gorev()], undefined)

    expect(w.findAll('article')).toHaveLength(1)
  })

  it('"Gördüm" ekrandaki özetle gider; sonra liste yenilenir', async () => {
    vi.mocked(benimApi.intikalGordum).mockResolvedValue(intikal({ gordu: 'GORULDU' }))
    const w = await kur([], [intikal()])

    await w.get('[data-test="intikal-karti"] button').trigger('click')
    await flushPromises()

    expect(benimApi.intikalGordum).toHaveBeenCalledWith(
      1,
      'İntikal · Ankara 14.10 07:00 → Eskişehir 14.10 08:30 · Servis',
    )
    expect(benimApi.gorevler).toHaveBeenCalledTimes(2)
  })

  it('iptal edilen intikal "İPTAL" etiketiyle; değişen intikal eski / yeni özetle yeniden onay ister', async () => {
    const w = await kur(
      [],
      [
        intikal({
          id: 1,
          durum: 'IPTAL',
          gordu: 'DEGISTI',
          eskiOzet: 'eski',
          ozet: 'yeni · İPTAL',
        }),
        intikal({
          id: 2,
          kalkis: '2026-10-14T09:00:00+03:00',
          gordu: 'DEGISTI',
          eskiOzet: 'E',
          ozet: 'Y',
        }),
      ],
    )
    const kartlar = w.findAll('[data-test="intikal-karti"]')

    expect(kartlar[0]!.text()).toContain('İPTAL')
    expect(kartlar[0]!.text()).toContain('DEĞİŞTİ')
    expect(kartlar[1]!.text()).toContain('Eski: E')
    expect(kartlar[1]!.text()).toContain('Yeni: Y')
  })

  it('bayat özet (409 OZET_DEGISTI) güncel intikali yerinde değiştirir', async () => {
    const guncel = intikal({ ozet: 'GÜNCEL ÖZET', gordu: 'DEGISTI', eskiOzet: 'eski' })
    vi.mocked(benimApi.intikalGordum).mockRejectedValue(
      new ApiHata(409, 'OZET_DEGISTI', 'İntikal siz bakarken değişti.', { gorev: guncel }),
    )
    const w = await kur([], [intikal()])
    vi.mocked(benimApi.gorevler).mockResolvedValue({
      bugun: '2026-10-14',
      ufukGun: 7,
      gorevler: [],
      kaldirilanlar: [],
      intikaller: [guncel],
    })

    await w.get('[data-test="intikal-karti"] button').trigger('click')
    await flushPromises()

    expect(w.get('[data-test="intikal-karti"]').text()).toContain('Yeni: GÜNCEL ÖZET')
    expect(w.text()).toContain('İntikal siz bakarken değişti.')
  })
})
