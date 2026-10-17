import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { otomatik: vi.fn() },
}))

import OtomatikAtamaDialog from './OtomatikAtamaDialog.vue'
import { atamaApi, type OtomatikAtamaSonucu } from './atamaApi'

const ANAHTAR = { kaydirDk: 240, oncelik: { '102': 1 } }
const sonuc = (
  uygulandi: boolean,
  tabanBos: number | null,
  bos: number,
  durma: 'BOS_YOK' | 'BOS_SIFIR' | 'TUR_SINIRI' | 'SURE' | null = 'TUR_SINIRI',
  tur = 2,
): OtomatikAtamaSonucu => ({
  bas: '2026-10-12',
  bit: '2026-10-18',
  uygulandi,
  atananSayisi: 2,
  bosSayisi: bos,
  gecmisSayisi: 0,
  atananlar: [],
  bosKalanlar: [],
  iyilestirme: { tabanBosSayisi: tabanBos, tur, sureMs: 1200, anahtar: ANAHTAR, durma },
})

function kur() {
  return mount(OtomatikAtamaDialog, { props: { varsayilanBas: '2026-10-12' } })
}
const dugme = (w: ReturnType<typeof kur>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)!

describe('OtomatikAtamaDialog: iyileştir (F2d)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('kutu varsayılan kapalı; kapalıyken istek gövdesinde iyileştir alanı YOK (bugünkü istek)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue({ ...sonuc(false, null, 1), iyilestirme: null })
    const w = kur()

    expect((w.get('#oto-iyilestir').element as HTMLInputElement).checked).toBe(false)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: false,
    })
    expect(w.find('[data-test="iyilestirme-ozet"]').exists()).toBe(false)
  })

  it('açıkken önizleme iyileştir:true gönderir; özet "X sefer daha dolduruldu"', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, 3, 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: false,
      iyilestir: true,
    })
    expect(w.get('[data-test="iyilestirme-ozet"]').text()).toBe(
      '2 sefer daha dolduruldu (3 → 1 boş)',
    )
  })

  it.each([
    ['TUR_SINIRI', 1, 1, 3, '3 tur denendi, daha iyi plan bulunamadı; bugünkü plan korundu'],
    ['BOS_SIFIR', 1, 1, 2, '2 tur denendi, daha iyi plan bulunamadı; bugünkü plan korundu'],
  ] as const)(
    'kazanç yoksa ve gerçekten denendiyse (%s) kaç tur denendiğini söyler',
    async (durma, taban, bos, tur, metin) => {
      vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, taban, bos, durma, tur))
      const w = kur()
      await w.get('#oto-iyilestir').setValue(true)
      await dugme(w, 'Önizle').trigger('click')
      await flushPromises()

      expect(w.get('[data-test="iyilestirme-ozet"]').text()).toBe(metin)
    },
  )

  it('boş sefer yokken "kazanç bulamadı" DEMEZ (QA UX-1)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, 0, 0, 'BOS_YOK', 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(w.get('[data-test="iyilestirme-ozet"]').text()).toBe(
      'Boş sefer yok; iyileştirmeye gerek kalmadı',
    )
  })

  it('tur 0 süre bütçesini doldurduysa "denenemedi" der, "kazanç bulamadı" demez (W3)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, 276, 276, 'SURE', 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    const metin = w.get('[data-test="iyilestirme-ozet"]').text()
    expect(metin).toContain('iyileştirme denenemedi')
    expect(metin).toContain('iyilestir.sure_sn')
    expect(metin).not.toContain('kazanç bulamadı')
  })

  it('tur ayarı 1 ise ek deneme yapılmadığını söyler', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, 5, 5, 'TUR_SINIRI', 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()

    expect(w.get('[data-test="iyilestirme-ozet"]').text()).toContain('ek deneme yapılmadı')
  })

  it('önizleme sürerken ilerleme metni görünür ve Kapat açıktır; Kapat geç gelen yanıtı atar (QA UX-2)', async () => {
    let bitir: (s: OtomatikAtamaSonucu) => void = () => {}
    vi.mocked(atamaApi.otomatik).mockReturnValue(new Promise((coz) => (bitir = coz)))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')

    expect(w.get('[data-test="hesaplaniyor"]').text()).toContain('Hesaplanıyor')
    expect(w.get('[data-test="hesaplaniyor"]').text()).toContain('Kapat')
    expect(dugme(w, 'Kapat').attributes('disabled')).toBeUndefined()
    await dugme(w, 'Kapat').trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
    bitir(sonuc(false, 3, 1))
    await flushPromises()
    expect(w.find('[data-test="iyilestirme-ozet"]').exists()).toBe(false)
  })

  it('Uygula sürerken Kapat kapalıdır (yazma işlemi)', async () => {
    let bitir: (s: OtomatikAtamaSonucu) => void = () => {}
    vi.mocked(atamaApi.otomatik).mockResolvedValueOnce(sonuc(false, 3, 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    vi.mocked(atamaApi.otomatik).mockReturnValue(new Promise((coz) => (bitir = coz)))
    await dugme(w, 'Uygula').trigger('click')

    expect(w.get('[data-test="hesaplaniyor"]').text()).toContain('yazılıyor')
    expect(dugme(w, 'Kapat').attributes('disabled')).toBeDefined()
    bitir(sonuc(true, null, 1))
    await flushPromises()
    expect(dugme(w, 'Kapat').attributes('disabled')).toBeUndefined()
  })

  it('Uygula, önizlemenin anahtarını gövdeye koyar', async () => {
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false, 3, 1))
      .mockResolvedValueOnce(sonuc(true, null, 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()

    expect(atamaApi.otomatik).toHaveBeenLastCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: true,
      iyilestir: true,
      anahtar: ANAHTAR,
    })
    // QA UX-4: uygulamadan sonra da kazanç satırı kalır (uygula yanıtında taban yok, önizlemeninki korunur)
    expect(w.get('[data-test="iyilestirme-ozet"]').text()).toBe(
      '2 sefer daha dolduruldu (3 → 1 boş)',
    )
  })

  it('iyileştir ile yolculuk birbirini devre dışı bırakır', async () => {
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    expect((w.get('#oto-yolculuk').element as HTMLInputElement).disabled).toBe(true)
    expect(w.text()).toContain('İyileştirme şimdilik yolculuk aşamasıyla birlikte kullanılamaz')

    await w.get('#oto-iyilestir').setValue(false)
    await w.get('#oto-yolculuk').setValue(true)
    expect((w.get('#oto-iyilestir').element as HTMLInputElement).disabled).toBe(true)
  })

  it('iyileştir değişince önizleme sıfırlanır (Uygula kapanır)', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false, 3, 1))
    const w = kur()
    await w.get('#oto-iyilestir').setValue(true)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeUndefined()

    await w.get('#oto-iyilestir').setValue(false)
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
  })
})
