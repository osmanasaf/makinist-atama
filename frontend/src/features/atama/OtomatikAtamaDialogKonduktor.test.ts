import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { otomatik: vi.fn() },
}))

import OtomatikAtamaDialog from './OtomatikAtamaDialog.vue'
import { atamaApi, type OtomatikAtamaSonucu } from './atamaApi'

const sonuc: OtomatikAtamaSonucu = {
  bas: '2026-10-14',
  bit: '2026-10-20',
  uygulandi: false,
  atananSayisi: 0,
  bosSayisi: 0,
  gecmisSayisi: 0,
  atananlar: [],
  bosKalanlar: [],
}

const kur = () => mount(OtomatikAtamaDialog, { props: { varsayilanBas: '2026-10-14' } })
const dugme = (w: ReturnType<typeof kur>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)!

describe('OtomatikAtamaDialog: personel türü (F3b)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc)
  })

  it('varsayılan makinist: istek gövdesinde personelTuru alanı YOK (bugünkü istek birebir aynı)', async () => {
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-14',
      bit: '2026-10-20',
      uygula: false,
    })
  })

  it('Kondüktör seçilince personelTuru gönderilir; yolculuk ve iyileştir kutuları kapanır ve temizlenir', async () => {
    const w = kur()
    await w.get('#oto-yolculuk').setValue(true)
    await w.get('#oto-tur').setValue('KONDUKTOR')
    await flushPromises()

    expect((w.get('#oto-yolculuk').element as HTMLInputElement).disabled).toBe(true)
    expect((w.get('#oto-yolculuk').element as HTMLInputElement).checked).toBe(false)
    expect((w.get('#oto-iyilestir').element as HTMLInputElement).disabled).toBe(true)
    expect(w.text()).toContain('Yalnız kondüktörü olmayan yolcu trenleri doldurulur')

    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-14',
      bit: '2026-10-20',
      uygula: false,
      personelTuru: 'KONDUKTOR',
    })
  })
})
