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
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const asil = makinist({ atamaId: 1, makinistId: 1, sicil: 'D001', ad: 'Asıl Kişi' })
const kond = makinist({
  atamaId: 2,
  rol: 'KONDUKTOR',
  makinistId: 9,
  sicil: 'K001',
  ad: 'Şef Kişi',
})
const yolcu = makinist({ atamaId: 3, rol: 'YOLCU', makinistId: 3, sicil: 'D003', ad: 'Yolcu Bir' })

function gunVerisi(): GunListesi {
  return {
    tarih: '2026-10-12',
    uretimZamani: '2026-10-12T07:00:00Z',
    kondukturEksikSayisi: 1,
    satirlar: [
      satir(1, { makinistler: [yolcu, kond, asil] }), // karışık sırada gelir
      satir(2, { makinistler: [asil], kondukturEksik: true }), // yolcu treni, kondüktör yok, ayar açık
      satir(3, { makinistler: [kond] }), // yalnız kondüktör: asıl yok → BOŞ
    ],
  }
}

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

describe('AtamaMasasi: kondüktör (F3b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
  })

  it('görevliler asıl, kondüktör, yolcu sırasıyla; kondüktör "Kond:" önekiyle', async () => {
    const w = await kur()
    const hucre = w.findAll('tr.sefer-satiri')[0]!.find('.makinist-sutun')
    const gorevliler = hucre.findAll('.gorevli').map((g) => g.find('span').text())

    expect(gorevliler).toEqual([
      'Asıl Kişi (D001)',
      'Kond: Şef Kişi (K001)',
      'Yolcu: Yolcu Bir (D003)',
    ])
  })

  it('"kondüktör eksik" bilgi rozeti yalnız işaretli seferde; üstte sayaç; kırmızı/KONTROL değil', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')

    expect(satirlar[0]!.find('[data-test="konduktor-eksik"]').exists()).toBe(false)
    expect(satirlar[1]!.find('[data-test="konduktor-eksik"]').text()).toContain('Kondüktör eksik')
    expect(satirlar[1]!.classes()).not.toContain('kirmizi')
    expect(satirlar[1]!.text()).not.toContain('KONTROL')
    expect(w.get('[data-test="konduktor-sayac"]').text()).toContain('1')
  })

  it('ayar kapalıyken (alan yok) rozet ve sayaç hiç çıkmaz', async () => {
    vi.mocked(atamaApi.gun).mockImplementation(() =>
      Promise.resolve({
        ...gunVerisi(),
        kondukturEksikSayisi: 0,
        satirlar: [satir(1, { makinistler: [asil] })],
      }),
    )
    const w = await kur()
    expect(w.find('[data-test="konduktor-eksik"]').exists()).toBe(false)
    expect(w.find('[data-test="konduktor-sayac"]').exists()).toBe(false)
  })

  it('"BOŞ" yalnız ASIL yokluğuna bağlı: yalnız kondüktörü olan sefer yine BOŞ', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')

    expect(satirlar[0]!.text()).not.toContain('BOŞ')
    expect(satirlar[2]!.text()).toContain('BOŞ')
    expect(satirlar[2]!.text()).toContain('Kond: Şef Kişi (K001)')
  })
})
