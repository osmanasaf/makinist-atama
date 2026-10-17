import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { otomatik: vi.fn() },
}))

import OtomatikAtamaDialog from './OtomatikAtamaDialog.vue'
import { atamaApi, type OtomatikAtamaSonucu } from './atamaApi'

const sefer = (id: number) => ({
  seferId: id,
  trenNo: `T${id}`,
  tarih: '2026-10-14',
  kalkis: '2026-10-14T10:30:00+03:00',
  varis: '2026-10-14T15:00:00+03:00',
  kalkisGar: 'Eskişehir',
  varisGar: 'Ankara',
})
const sonuc: OtomatikAtamaSonucu = {
  bas: '2026-10-14',
  bit: '2026-10-14',
  uygulandi: false,
  atananSayisi: 1,
  bosSayisi: 0,
  gecmisSayisi: 0,
  atananlar: [
    {
      ...sefer(2),
      makinistId: 7,
      sicil: 'D007',
      ad: 'Aday Yedi',
      yolculuk: {
        seferId: 1,
        trenNo: 'E1',
        kalkis: '2026-10-14T08:00:00+03:00',
        varis: '2026-10-14T09:30:00+03:00',
        kalkisGar: 'Ankara',
        varisGar: 'Eskişehir',
      },
    },
  ],
  bosKalanlar: [],
}

function kur() {
  return mount(OtomatikAtamaDialog, { props: { varsayilanBas: '2026-10-14' } })
}
const dugme = (w: ReturnType<typeof kur>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)!

describe('OtomatikAtamaDialog: yolculuk (F2b)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('kutu varsayılan kapalı; kapalıyken istek gövdesinde yolculuk alanı YOK (bugünkü istek)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue({ ...sonuc, atananlar: [], atananSayisi: 0 })
    const w = kur()

    expect((w.get('#oto-yolculuk').element as HTMLInputElement).checked).toBe(false)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-14',
      bit: '2026-10-20',
      uygula: false,
    })
  })

  it('kutu açıkken yolculuk:true gider; önizlemede "+ Yolcu: E1 ..." satırı ve iki satır yazılacağı söylenir', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc)
    const w = kur()
    await w.get('#oto-yolculuk').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-14',
      bit: '2026-10-20',
      uygula: false,
      yolculuk: true,
    })
    expect(w.get('[data-test="yolculuk-satiri"]').text()).toContain(
      '+ Yolcu: E1 Ankara 08:00 → Eskişehir 09:30',
    )
    expect(w.text()).toContain('1 atama yolculuklu: her biri için iki satır yazılır')
  })

  it('kutu değişince eski önizleme geçersiz olur (Uygula kapanır)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc)
    const w = kur()
    await w.get('#oto-yolculuk').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeUndefined()

    await w.get('#oto-yolculuk').setValue(false)

    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
  })
})
