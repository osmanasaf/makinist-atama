import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: {
    gorevler: vi.fn(),
    gordum: vi.fn(),
    olayiKapat: vi.fn(),
    takasHedefler: vi.fn(),
    takasAc: vi.fn(),
  },
}))

import BenimGorevler from './BenimGorevler.vue'
import { benimApi, type BenimGorev } from './benimApi'

function gorev(p: Partial<BenimGorev> = {}): BenimGorev {
  const yarin = new Date(Date.now() + 48 * 3600_000).toISOString()
  return {
    atamaId: 1,
    seferId: 1,
    tarih: '2026-10-12',
    sira: 1,
    trenNo: 'DE-1503',
    rol: 'ASIL',
    durum: 'PLANLI',
    kalkisGar: 'Sivas',
    varisGar: 'Kars',
    planlananKalkis: yarin,
    planlananVaris: yarin,
    guncelKalkis: yarin,
    guncelVaris: yarin,
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Sivas–Kars',
    km: 500,
    esMakinist: null,
    gordu: 'GORULDU',
    gorduZamani: '2026-10-12T06:14:00Z',
    ozet: 'Asıl · Sivas 14.10 13:40 → Kars 14.10 20:10',
    eskiOzet: null,
    ...p,
  }
}

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur(g: BenimGorev[]) {
  vi.mocked(benimApi.gorevler).mockResolvedValue({
    bugun: '2026-10-12',
    ufukGun: 7,
    gorevler: g,
    kaldirilanlar: [],
  } as never)
  const w = mount(BenimGorevler, { attachTo: document.body })
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimGorevler: parçalı tren (F2c)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('parçalı görev kartında "DE-1503 · 2/2", parçasızda yalnız tren no', async () => {
    const w = await kur([
      gorev({ gordu: 'YENI', parcaNo: 2, parcaSayisi: 2 }),
      // OK-41b: T9 daha erken → sonraki görev o; parçalı YENİ kart Yeni bölümünde kalır
      gorev({
        atamaId: 2,
        trenNo: 'T9',
        parcaNo: null,
        parcaSayisi: null,
        guncelKalkis: new Date(Date.now() + 24 * 3600_000).toISOString(),
        guncelVaris: new Date(Date.now() + 24 * 3600_000).toISOString(),
      }),
    ])
    const kartlar = w.findAll('article')

    expect(kartlar[0]!.find('.benim-guzergah strong').text()).toBe('DE-1503 · 2/2')
    expect(kartlar[0]!.find('button[aria-label="DE-1503 · 2/2 görevini gördüm"]').exists()).toBe(
      true,
    )
    expect(kartlar[1]!.find('.benim-guzergah strong').text()).toBe('T9')
  })
})
