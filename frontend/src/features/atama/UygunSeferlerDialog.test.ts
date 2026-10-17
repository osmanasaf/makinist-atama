import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { uygunSeferler: vi.fn() },
}))

import UygunSeferlerDialog from './UygunSeferlerDialog.vue'
import { atamaApi, type UygunSefer } from './atamaApi'

const sefer = (id: number, ek: Partial<UygunSefer> = {}): UygunSefer => ({
  seferId: id,
  trenNo: `T${id}`,
  kalkisGar: 'Ankara',
  varisGar: 'Konya',
  guncelKalkis: '2026-10-12T05:30:00Z', // İstanbul 08:30
  guncelVaris: '2026-10-12T08:00:00Z',
  aracTipi: 'YHT',
  hat: 'Ankara–Konya',
  uyarilar: [],
  ...ek,
})

async function kur() {
  const w = mount(UygunSeferlerDialog, {
    props: { makinistId: 1, makinistAdi: 'Ali Veli (D001)', gun: '2026-10-12' },
  })
  await flushPromises()
  return w
}

describe('UygunSeferlerDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('makinistin günlük uygun seferlerini saat, güzergâh ve uyarıyla listeler; kaldır ipucu verir', async () => {
    vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([
      sefer(5),
      sefer(6, {
        uyarilar: [
          {
            kod: 'K-20',
            siddet: 'UYARI',
            olculen: 9,
            esik: 8,
            birim: 's',
            metin: 'Günlük 9 s > 8 s',
          },
        ],
      }),
    ])
    const w = await kur()
    expect(atamaApi.uygunSeferler).toHaveBeenCalledWith(1, '2026-10-12')
    expect(w.text()).toContain('Ali Veli (D001)')
    expect(w.text()).toContain('Kaldır')
    const satirlar = w.findAll('tbody tr')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('08:30–11:00')
    expect(satirlar[0]!.text()).toContain('T5')
    expect(satirlar[0]!.text()).toContain('Ankara – Konya')
    expect(satirlar[1]!.text()).toContain('⚠ Günlük 9 s > 8 s')
  })

  it('Seç: seferi yayınlar', async () => {
    vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([sefer(5), sefer(6)])
    const w = await kur()
    await w.findAll('tbody button')[1]!.trigger('click')
    expect(w.emitted('sec')).toEqual([[sefer(6)]])
  })

  it('uygun sefer yoksa boş durum; hata gösterilir', async () => {
    vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([])
    expect((await kur()).text()).toContain('atanabileceği sefer yok')
    vi.mocked(atamaApi.uygunSeferler).mockRejectedValue(
      new ApiHata(404, 'BULUNAMADI', 'Makinist yok.'),
    )
    expect((await kur()).get('[role="alert"]').text()).toBe('Makinist yok.')
  })
})

describe('UygunSeferlerDialog: boş durum ve nöbet notu', () => {
  beforeEach(() => vi.resetAllMocks())

  it('sonuç boşken nedenleri açıklayan metin gösterilir', async () => {
    vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([])
    const w = await kur()
    expect(w.get('.bos-durum').text()).toBe(
      'Bu gün için bu makinistin atanabileceği sefer yok: seferlerin tümü dolu ya da kurallar (dinlenme, izin, yetki vb.) uygun görmüyor.',
    )
  })

  it('uyarı nöbet içeriyorsa tek not görünür', async () => {
    const u = (metin: string) => ({
      kod: 'K-30',
      siddet: 'UYARI' as const,
      olculen: 1,
      esik: 1,
      birim: 's',
      metin,
    })
    vi.mocked(atamaApi.uygunSeferler).mockResolvedValue([
      sefer(5, { uyarilar: [u('Gece nöbeti süresi 10 s > 7 s')] }),
      sefer(6, { uyarilar: [u('Nöbet toplamı 9 s')] }),
    ])
    expect((await kur()).findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })
})
