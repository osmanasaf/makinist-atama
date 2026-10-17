import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./seferApi', () => ({ seferApi: { yukle: vi.fn(), sablon: vi.fn() } }))

import SeferYukleme from './SeferYukleme.vue'
import { seferApi, type YuklemeSatiri, type YuklemeSonucu } from './seferApi'

const satir = (p: Partial<YuklemeSatiri>): YuklemeSatiri => ({
  satirNo: 2,
  trenNo: 'DE-1503',
  tarih: '2026-10-12',
  kalkisGar: 'Ankara',
  varisGar: 'Sivas',
  kalkis: '08:00',
  varis: '13:30',
  aracTipi: 'YHT',
  hat: 'Ankara–Kars',
  durum: 'YENI',
  ...p,
})

async function kur(sonuc: YuklemeSonucu) {
  vi.mocked(seferApi.yukle).mockResolvedValue(sonuc)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'seferler', component: { template: '<div />' } }],
  })
  await router.push('/')
  const w = mount(SeferYukleme, { global: { plugins: [router] } })
  const girdi = w.get('#dosya')
  Object.defineProperty(girdi.element, 'files', {
    value: [new File(['x'], 'seferler.xlsx')],
    configurable: true,
  })
  await girdi.trigger('change')
  await flushPromises()
  return w
}

describe('SeferYukleme: parçalı tren (F2c)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('iki parça satırı etiketli; özette "Parçalı tren: 1 (2 parça)"; aynı satır no anahtar çakışması yok', async () => {
    const w = await kur({
      onizleme: true,
      toplamSatir: 2,
      eklenen: 2,
      zatenVar: 0,
      hataSayisi: 0,
      satirlar: [
        satir({ parcaNo: 1, parcaSayisi: 2 }),
        satir({ parcaNo: 2, parcaSayisi: 2, kalkisGar: 'Sivas', varisGar: 'Kars' }),
      ],
      hatalar: [],
    })

    const tren = w
      .findAll('table[aria-label="Geçerli satırlar"] tbody tr')
      .map((tr) => tr.findAll('td')[1]!.text())
    expect(tren).toEqual(['DE-1503 · 1/2', 'DE-1503 · 2/2'])
    expect(w.get('[role="status"]').text()).toContain('Parçalı tren: 1 (2 parça)')
  })

  it('parçasız yüklemede özet ve tren no eskisi gibi', async () => {
    const w = await kur({
      onizleme: true,
      toplamSatir: 1,
      eklenen: 1,
      zatenVar: 0,
      hataSayisi: 0,
      satirlar: [satir({ trenNo: 'T1' })],
      hatalar: [],
    })

    expect(w.get('[role="status"]').text()).not.toContain('Parçalı')
    expect(w.get('table[aria-label="Geçerli satırlar"] tbody tr').findAll('td')[1]!.text()).toBe(
      'T1',
    )
  })
  it('hata tablosu sayfa adı, satır no ve tren no gösterir (UX-1)', async () => {
    const w = await kur({
      onizleme: true,
      toplamSatir: 2,
      eklenen: 0,
      zatenVar: 0,
      hataSayisi: 2,
      satirlar: [],
      hatalar: [
        { satirNo: 7, neden: 'Gar bilinmiyor', sayfa: 'Seferler', trenNo: 'T-9' },
        { satirNo: 7, neden: 'KM tutarsız', sayfa: 'Değişim Noktaları', trenNo: 'DE-1503' },
      ],
    })

    const baslik = w.findAll('table[aria-label="Hatalı satırlar"] thead th').map((th) => th.text())
    expect(baslik).toEqual(['Sayfa', 'Satır no', 'Tren no', 'Neden'])
    const satirlar = w
      .findAll('table[aria-label="Hatalı satırlar"] tbody tr')
      .map((tr) => tr.findAll('td').map((td) => td.text()))
    expect(satirlar).toEqual([
      ['Seferler', '7', 'T-9', 'Gar bilinmiyor'],
      ['Değişim Noktaları', '7', 'DE-1503', 'KM tutarsız'],
    ])
  })

  it('engelleyici hata: uyarı görünür ve "Onayla ve yükle" kilitli (yeni satır olsa bile)', async () => {
    const w = await kur({
      onizleme: true,
      toplamSatir: 2,
      eklenen: 1,
      zatenVar: 0,
      hataSayisi: 1,
      satirlar: [satir({ trenNo: 'DE-1503' })],
      hatalar: [
        {
          satirNo: 2,
          neden: "'DE-1530' tren no'su Seferler sayfasında yok",
          sayfa: 'Değişim Noktaları',
          trenNo: 'DE-1530',
        },
      ],
      engelli: true,
    })

    expect(w.find('[data-test=engelli]').exists()).toBe(true)
    // QA UX-3: engelli dosyada "Eklenecek" gösterilmez; başlık harf farkını da söyler
    expect(w.text()).not.toContain('Eklenecek')
    expect(w.get('[data-test=engelli]').text()).toContain('harf farkıyla')
    const dugme = w.findAll('button').find((b) => b.text().includes('Onayla ve yükle'))!
    expect(dugme.attributes('disabled')).toBeDefined()
  })

  it('engelli değilse onay açık ve uyarı yok', async () => {
    const w = await kur({
      onizleme: true,
      toplamSatir: 1,
      eklenen: 1,
      zatenVar: 0,
      hataSayisi: 0,
      satirlar: [satir({})],
      hatalar: [],
    })

    expect(w.find('[data-test=engelli]').exists()).toBe(false)
    expect(w.text()).toContain('Eklenecek: 1')
    const dugme = w.findAll('button').find((b) => b.text().includes('Onayla ve yükle'))!
    expect(dugme.attributes('disabled')).toBeUndefined()
  })
})
