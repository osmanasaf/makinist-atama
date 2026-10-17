import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { yolculukOner: vi.fn(), yolculukla: vi.fn(), intikalle: vi.fn() },
}))

import YolculukOneriDialog from './YolculukOneriDialog.vue'
import { atamaApi, type Aday, type IntikalOnerisi, type YolculukOnerisi } from './atamaApi'

const aday: Aday = {
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 100,
  buAyKm: 5000,
  buSeferdeRol: null,
}
const seferli: YolculukOnerisi = {
  seferId: 31,
  trenNo: 'E1',
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
  kalkis: '2026-10-14T03:00:00Z',
  varis: '2026-10-14T04:30:00Z',
  aktarmaDk: 60,
  uyarilar: [],
  gerekce: 'Ankara → Eskişehir yolcu olarak; 1 s 0 dk aktarma',
}
const intikal = (ek: Partial<IntikalOnerisi> = {}): IntikalOnerisi => ({
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
  kalkis: '2026-10-14T04:15:00Z', // İstanbul 07:15
  varis: '2026-10-14T05:45:00Z', // 08:45
  sureDk: 90,
  aktarmaDk: 15,
  uyarilar: [],
  gerekce: 'Ankara → Eskişehir serbest intikal (1 s 30 dk); 15 dk aktarma',
  ...ek,
})

function kur(ozellik: Record<string, unknown> = {}) {
  return mount(YolculukOneriDialog, {
    props: { seferId: 11, seferEtiketi: 'H Eskişehir – Ankara 09:00', aday, ...ozellik },
  })
}

describe('YolculukOneriDialog: serbest intikal satırı (İ-2, MK-110)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('seferli öneri yokken bile intikal satırı çıkar ("uygun yolculuk yok" mesajı çıkmaz)', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({ oneriler: [], intikal: intikal() })
    const w = kur()
    await flushPromises()

    expect(w.get('[data-test="intikal-oneri"]').text()).toContain('Servis ile intikal (90 dk)')
    expect(w.get('[data-test="intikal-oneri"]').text()).toContain('Ankara 07:15 → Eskişehir 08:45')
    expect(w.text()).not.toContain('Uygun yolculuk bulunamadı')
    expect(w.get('button[type="submit"]').text()).toBe('Yolculukla ata')
  })

  it('öneri yoksa (intikal null) eski davranış: "bulunamadı" mesajı', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({ oneriler: [], intikal: null })
    const w = kur()
    await flushPromises()

    expect(w.find('[data-test="intikal-oneri"]').exists()).toBe(false)
    expect(w.text()).toContain('Uygun yolculuk bulunamadı')
  })

  it('seferliler önce, intikal sonda; seçince "İntikalle ata" gövdeyi (saat YOK) gönderir', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({ oneriler: [seferli], intikal: intikal() })
    vi.mocked(atamaApi.intikalle).mockResolvedValue({ intikal: {}, atama: {} })
    const w = kur()
    await flushPromises()
    const radyolar = w.findAll('input[type="radio"]')

    expect(radyolar).toHaveLength(2)
    expect(radyolar[1]!.element.closest('label')!.getAttribute('data-test')).toBe('intikal-oneri')
    await radyolar[1]!.setValue(true)
    expect(w.get('button[type="submit"]').text()).toBe('İntikalle ata')
    await w.get('#yolculuk-arac').setValue('OTOBUS')
    await w.get('#yolculuk-intikal-not').setValue(' kapı 2 ')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(atamaApi.intikalle).toHaveBeenCalledWith(11, {
      makinistId: 7,
      aracTuru: 'OTOBUS',
      notu: 'kapı 2',
      gerekce: undefined,
    })
    expect(atamaApi.yolculukla).not.toHaveBeenCalled()
    expect(w.emitted('tamam')).toEqual([['İntikal ve atama yapıldı.']])
  })

  it('uyarılı intikal gerekçe ister; sefer doluysa değiştirme nedeni de gider', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({
      oneriler: [],
      intikal: intikal({
        uyarilar: [
          {
            kod: 'K-20',
            siddet: 'UYARI',
            olculen: 0,
            esik: 0,
            birim: '',
            metin: 'Konum uyuşmazlığı (sonraki görev)',
          },
        ],
      }),
    })
    vi.mocked(atamaApi.intikalle).mockResolvedValue({ intikal: {}, atama: {} })
    const w = kur({ mevcutAsil: { ad: 'Veli Can', sicil: 'D009' } })
    await flushPromises()

    await w.get('input[type="radio"]').setValue(true)
    expect(w.text()).toContain('Konum uyuşmazlığı (sonraki görev)')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await w.get('#yolculuk-gerekce').setValue('bilinen aktarma')
    await w.get('#yolculuk-neden').setValue('HASTALIK')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(atamaApi.intikalle).toHaveBeenCalledWith(
      11,
      expect.objectContaining({ gerekce: 'bilinen aktarma', neden: 'HASTALIK' }),
    )
  })

  it('INTIKAL_ONERISI_YOK (422) diyalogda gösterilir', async () => {
    vi.mocked(atamaApi.yolculukOner).mockResolvedValue({ oneriler: [], intikal: intikal() })
    vi.mocked(atamaApi.intikalle).mockRejectedValue(
      new ApiHata(
        422,
        'INTIKAL_ONERISI_YOK',
        'Bu makinist için süre tablosuna göre intikal önerilemiyor.',
      ),
    )
    const w = kur()
    await flushPromises()

    await w.get('input[type="radio"]').setValue(true)
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(w.get('[role="alert"]').text()).toContain('intikal önerilemiyor')
    expect(w.emitted('tamam')).toBeUndefined()
  })
})
