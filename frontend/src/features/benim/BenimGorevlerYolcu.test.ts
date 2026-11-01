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
    trenNo: 'T1234',
    rol: 'ASIL',
    durum: 'PLANLI',
    kalkisGar: 'Ankara',
    varisGar: 'Eskişehir',
    planlananKalkis: yarin,
    planlananVaris: yarin,
    guncelKalkis: yarin,
    guncelVaris: yarin,
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Ankara-Eskişehir',
    km: 245,
    esMakinist: null,
    gordu: 'GORULDU',
    gorduZamani: '2026-10-12T06:14:00Z',
    ozet: 'Yolcu · Ankara 14.10 08:00 → Eskişehir 14.10 09:30',
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
  })
  const w = mount(BenimGorevler, { attachTo: document.body })
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimGorevler: yolcu görevi (F2b)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('YOLCU kartı "Yolcu" etiketiyle ve açıklamasıyla gelir; Takas iste / Bırak düğmeleri yok', async () => {
    const w = await kur([gorev({ rol: 'YOLCU' }), gorev({ atamaId: 2, rol: 'ASIL' })])
    const kartlar = w.findAll('article')

    expect(kartlar[0]!.text()).toContain('Yolcu')
    expect(kartlar[0]!.find('[data-test="yolcu-notu"]').text()).toContain(
      'sürüş göreviniz değildir',
    )
    expect(kartlar[0]!.find('.benim-takas-dugmeleri').exists()).toBe(false)
    expect(kartlar[0]!.findAll('button').map((b) => b.text())).not.toContain('Takas iste')
    // asıl görevde değişmedi
    expect(kartlar[1]!.find('[data-test="yolcu-notu"]').exists()).toBe(false)
    expect(kartlar[1]!.findAll('button').map((b) => b.text())).toEqual(['Takas iste', 'Bırak'])
  })

  it('yolcu görevi de "Gördüm" ister (tebliğ kanıtı rol-bağımsız)', async () => {
    // OK-41b: sonraki görev kartı kendi Gördüm'ünü taşır; bu test Yeni bölümündeki kartı sınar → önde görülmüş bir görev
    const erken = new Date(Date.now() + 24 * 3600_000).toISOString()
    const w = await kur([
      gorev({ rol: 'YOLCU', gordu: 'YENI', gorduZamani: null }),
      gorev({ atamaId: 2, guncelKalkis: erken, guncelVaris: erken }),
    ])

    expect(w.find('article').text()).toContain('YENİ')
    expect(
      w
        .find('article')
        .findAll('button')
        .map((b) => b.text()),
    ).toContain('Gördüm')
  })
})
