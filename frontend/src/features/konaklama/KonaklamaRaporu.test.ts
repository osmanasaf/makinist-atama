import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./konaklamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./konaklamaApi')>()),
  konaklamaApi: { rapor: vi.fn(), raporExcel: vi.fn() },
}))

import KonaklamaRaporu from './KonaklamaRaporu.vue'
import { konaklamaApi, type KonaklamaRaporu as Rapor } from './konaklamaApi'

const rapor: Rapor = {
  ay: '2026-10',
  uretimZamani: '2026-10-31T10:00:00+03:00',
  ureten: 'Ata Maci',
  toplamGece: 4,
  toplamEksikOneri: 2,
  makinistler: [
    {
      makinistId: 20,
      sicil: 'D020',
      adSoyad: 'Demo Makinist 20',
      konaklamaSayisi: 3,
      gece: 4,
      eksikOneri: 1,
      garlar: [
        { garId: 4, gar: 'Konya', konaklamaSayisi: 1, gece: 1 },
        { garId: 3, gar: 'Sivas', konaklamaSayisi: 2, gece: 3 },
      ],
    },
    {
      makinistId: 21,
      sicil: 'D021',
      adSoyad: 'Demo Makinist 21',
      konaklamaSayisi: 0,
      gece: 0,
      eksikOneri: 1,
      garlar: [],
    },
  ],
}

describe('KonaklamaRaporu (F2b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(konaklamaApi.rapor).mockResolvedValue(rapor)
  })

  it('makinist x gar satırları, makinist toplamı ve "kaydı eksik öneri" sütunu; kaydı olmayan makinist de görünür', async () => {
    const w = mount(KonaklamaRaporu)
    await flushPromises()
    const satirlar = w.findAll('[data-test="satir"]')

    expect(w.get('[data-test="ozet"]').text()).toContain('Toplam 4 gece')
    expect(satirlar).toHaveLength(3)
    expect(satirlar[0]!.text()).toContain('Konya')
    expect(satirlar[1]!.text()).toContain('Sivas')
    expect(satirlar[0]!.text()).toContain('1') // eksik öneri yalnız makinistin ilk satırında
    expect(w.get('[data-test="toplam"]').text()).toContain('4')
    expect(satirlar[2]!.text()).toContain('D021')
    expect(konaklamaApi.rapor).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}$/))
  })

  it('Excel indirme yalnız görünen ayı ister', async () => {
    vi.mocked(konaklamaApi.raporExcel).mockResolvedValue({
      blob: new Blob(['x']),
      ad: 'konaklama-raporu-2026-10.xlsx',
    })
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    const w = mount(KonaklamaRaporu)
    await flushPromises()

    await w
      .findAll('button')
      .find((b) => b.text() === 'Excel indir')!
      .trigger('click')
    await flushPromises()

    expect(konaklamaApi.raporExcel).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}$/))
  })
})
