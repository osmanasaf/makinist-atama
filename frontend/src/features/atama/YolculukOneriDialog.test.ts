import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { yolculukOner: vi.fn(), yolculukla: vi.fn() },
}))

import AdayPaneli from './AdayPaneli.vue'
import YolculukOneriDialog from './YolculukOneriDialog.vue'
import { atamaApi, type Aday, type YolculukOnerisi } from './atamaApi'

const aday: Aday = {
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [
    {
      kod: 'K-20',
      siddet: 'UYARI',
      olculen: 0,
      esik: 0,
      birim: '',
      metin: 'Konum uyuşmazlığı (ilk görev, bağlı gar): Ankara ≠ Eskişehir',
    },
  ],
  buAySaat: 100,
  buAyKm: 5000,
  buSeferdeRol: null,
}
const oneri = (ek: Partial<YolculukOnerisi> = {}): YolculukOnerisi => ({
  seferId: 31,
  trenNo: 'E1',
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
  kalkis: '2026-10-14T05:00:00Z', // İstanbul 08:00
  varis: '2026-10-14T06:30:00Z',
  aktarmaDk: 60,
  uyarilar: [],
  gerekce: 'Ankara → Eskişehir yolcu olarak; 1 s 0 dk aktarma',
  ...ek,
})
const sonuc = {} as Awaited<ReturnType<typeof atamaApi.yolculukla>>

function kur(ozellik: Record<string, unknown> = {}) {
  return mount(YolculukOneriDialog, {
    props: { seferId: 11, seferEtiketi: 'S2 Eskişehir – Ankara 10:30', aday, ...ozellik },
  })
}

describe('YolculukOneriDialog (F2b)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('öneriler (aktarma süresiyle) listelenir; seçmeden onay kapalı; seçince "Yolculukla ata" gövdeyi gönderir', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({
      oneriler: [oneri(), oneri({ seferId: 32, trenNo: 'E2', aktarmaDk: 120 })],
    })
    vi.mocked(atamaApi.yolculukla).mockResolvedValue(sonuc)
    const w = kur()
    await flushPromises()

    expect(atamaApi.yolculukOner).toHaveBeenCalledWith(11, 7)
    expect(w.findAll('input[type="radio"]')).toHaveLength(2)
    expect(w.text()).toContain('1 s 0 dk aktarma')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    await w.findAll('input[type="radio"]')[0]!.setValue(true)
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(atamaApi.yolculukla).toHaveBeenCalledWith(11, {
      makinistId: 7,
      yolculukSeferId: 31,
      gerekce: undefined,
    })
    expect(w.emitted('tamam')).toEqual([['Yolculuk ve atama yapıldı.']])
  })

  it('öneride UYARI varsa gerekçe zorunlu; sefer doluysa değiştirme nedeni zorunlu', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({
      oneriler: [
        oneri({
          uyarilar: [
            {
              kod: 'K-17',
              siddet: 'UYARI',
              olculen: 1,
              esik: 1,
              birim: 's',
              metin: 'Gece uyarısı',
            },
          ],
        }),
      ],
    })
    vi.mocked(atamaApi.yolculukla).mockResolvedValue(sonuc)
    const w = kur({
      mevcutAsil: { atamaId: 5, rol: 'ASIL', makinistId: 9, sicil: 'D009', ad: 'Eski Kişi' },
    })
    await flushPromises()
    await w.get('input[type="radio"]').setValue(true)

    expect(w.text()).toContain('Gece uyarısı')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await w.get('#yolculuk-gerekce').setValue('amir onayı')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined() // neden hâlâ yok
    await w.get('#yolculuk-neden').setValue('HASTALIK')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(atamaApi.yolculukla).toHaveBeenCalledWith(
      11,
      expect.objectContaining({ gerekce: 'amir onayı', neden: 'HASTALIK' }),
    )
  })

  it('öneri yoksa açık bir mesaj; sunucu ENGEL (422) ihlallerini diyalog açıkken gösterir', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValueOnce({ oneriler: [] })
    const bos = kur()
    await flushPromises()
    expect(bos.text()).toContain('Uygun yolculuk bulunamadı')

    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({ oneriler: [oneri()] })
    vi.mocked(atamaApi.yolculukla).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Kural ihlali: D007 bu sefere atanamaz.', {
        ihlaller: [
          {
            kod: 'K-11',
            siddet: 'ENGEL',
            olculen: 8,
            esik: 11,
            birim: 's',
            metin: 'Dinlenme 8 s < 11 s',
          },
        ],
      }),
    )
    const w = kur()
    await flushPromises()
    await w.get('input[type="radio"]').setValue(true)
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(w.text()).toContain('Kural ihlali: D007 bu sefere atanamaz.')
    expect(w.text()).toContain('Dinlenme 8 s < 11 s')
    expect(w.emitted('tamam')).toBeUndefined()
  })

  it('AdayPaneli: K-20 uyarılı adayda "Yolculuk öner" düğmesi çıkar ve satır seçimini tetiklemez; yolcu rolünde yok', async () => {
    const p = mount(AdayPaneli, { props: { adaylar: [aday], rol: 'ASIL' } })
    const dugme = p.findAll('button').find((b) => b.text() === 'Yolculuk öner')!
    await dugme.trigger('click')

    expect(p.emitted('yolculukOner')).toEqual([[aday]])
    expect(p.emitted('sec')).toBeUndefined()
    await p.setProps({ rol: 'YOLCU' })
    expect(p.findAll('button').some((b) => b.text() === 'Yolculuk öner')).toBe(false)
  })
})
