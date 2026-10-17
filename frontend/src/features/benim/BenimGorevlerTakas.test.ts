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
import { rozetMetni } from './rozet'

function gorev(p: Partial<BenimGorev> = {}): BenimGorev {
  const yarin = new Date(Date.now() + 48 * 3600_000).toISOString()
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
    planlananKalkis: yarin,
    planlananVaris: yarin,
    guncelKalkis: yarin,
    guncelVaris: yarin,
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Ankara-Sivas',
    km: 250,
    esMakinist: null,
    gordu: 'GORULDU',
    gorduZamani: '2026-10-12T06:14:00Z',
    ozet: 'Asıl · Ankara → Sivas',
    eskiOzet: null,
    ...p,
  }
}

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur(g: BenimGorev[], bekleyenTakas?: number) {
  vi.mocked(benimApi.gorevler).mockResolvedValue({
    bugun: '2026-10-12',
    ufukGun: 7,
    gorevler: g,
    kaldirilanlar: [],
    bekleyenTakas,
  })
  const w = mount(BenimGorevler, { attachTo: document.body })
  kurulan.push(w)
  await flushPromises()
  return w
}

describe('BenimGorevler: takas / bırak', () => {
  beforeEach(() => vi.resetAllMocks())

  it('gelecekteki görevde "Takas iste" ve "Bırak" düğmeleri; iptal ve geçmiş görevde yok', async () => {
    const gecmis = new Date(Date.now() - 3600_000).toISOString()
    const w = await kur([
      gorev(),
      gorev({ atamaId: 2, durum: 'IPTAL' }),
      gorev({ atamaId: 3, guncelKalkis: gecmis }),
    ])
    const kartlar = w.findAll('article')
    expect(kartlar[0]!.findAll('button').map((b) => b.text())).toEqual(['Takas iste', 'Bırak'])
    expect(kartlar[1]!.find('.benim-takas-dugmeleri').exists()).toBe(false)
    expect(kartlar[2]!.find('.benim-takas-dugmeleri').exists()).toBe(false)
  })

  it('"Bırak" formu açar; gönderilince form kapanır, bilgi gösterilir ve liste yenilenir', async () => {
    vi.mocked(benimApi.takasAc).mockResolvedValue({ id: 9 } as never)
    const w = await kur([gorev()])
    await w.findAll('article button')[1]!.trigger('click')
    expect(w.find('dialog').exists()).toBe(true)
    await w.get('#takas-neden').setValue('IZIN')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.text()).toContain('Talebiniz gönderildi')
    expect(vi.mocked(benimApi.gorevler).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('rozet: bekleyen takas sayısı bağlantıda ve aria-live bölgesinde', async () => {
    const w = await kur([gorev()], 2)
    expect(w.get('[data-test="takas-baglanti"]').text()).toContain('Takas: 2')
    expect(w.get('[data-test="takas-baglanti"]').attributes('href')).toBe('#/benim/takas')
    expect(rozetMetni.value).toContain('Takas: 2')
  })

  it('bekleyen takas yoksa rozet etiketi çizilmez', async () => {
    const w = await kur([gorev()], 0)
    expect(w.get('[data-test="takas-baglanti"]').text()).not.toContain('Takas:')
  })
})

describe('BenimGorevler: açık talep işareti (UX-3)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('açık talepteki görevde düğmeler yerine "Takas talebi açık"; diğer görevde düğmeler durur', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue({
      bugun: '2026-10-12',
      ufukGun: 7,
      gorevler: [gorev({ atamaId: 1 }), gorev({ atamaId: 2, trenNo: 'T2' })],
      kaldirilanlar: [],
      takasAcikAtamalar: [1],
    })
    const w = mount(BenimGorevler, { attachTo: document.body })
    kurulan.push(w)
    await flushPromises()
    const kartlar = w.findAll('article')
    expect(kartlar[0]!.find('[data-test=takas-acik]').text()).toContain('Takas talebi açık')
    expect(kartlar[0]!.find('.benim-takas-dugmeleri').exists()).toBe(false)
    expect(kartlar[1]!.findAll('button').map((b) => b.text())).toContain('Takas iste')
  })
})
