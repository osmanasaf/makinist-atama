import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { birlestir: vi.fn() },
}))
vi.mock('@/features/sefer/seferApi', () => ({ seferApi: { getir: vi.fn() } }))

import SeferBirlestirDialog from './SeferBirlestirDialog.vue'
import { atamaApi, type BirlestirSonucu } from './atamaApi'
import { seferApi, type Sefer } from '@/features/sefer/seferApi'

const sefer = (ek: Partial<Sefer> = {}): Sefer => ({
  id: 11,
  trenNo: 'DE-1503',
  tarih: '2026-10-20',
  kalkisGarId: 1,
  kalkisGar: 'Ankara',
  varisGarId: 3,
  varisGar: 'Kars',
  planlananKalkis: '2026-10-20T05:00:00Z',
  planlananVaris: '2026-10-20T20:00:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  guncelKalkis: '2026-10-20T05:00:00Z', // İstanbul 08:00
  guncelVaris: '2026-10-20T20:00:00Z', // 23:00
  aracTipiId: 9,
  aracTipi: 'DE',
  hatId: 7,
  hat: 'Doğu',
  km: 1000,
  durum: 'PLANLI',
  versiyon: 5,
  ...ek,
})

const sonuc = (ek: Partial<BirlestirSonucu> = {}): BirlestirSonucu => ({
  sefer: sefer(),
  silinenSeferIdler: [12],
  yeniKirmizi: [],
  ...ek,
})

async function kur(yol = sefer({ parcaNo: 2, parcaSayisi: 3, versiyon: 4 })) {
  vi.mocked(seferApi.getir).mockResolvedValue(yol)
  const w = mount(SeferBirlestirDialog, {
    props: { seferId: 11, seferEtiketi: 'DE-1503 Ankara – Sivas 08:00' },
  })
  await flushPromises()
  return w
}
type W = Awaited<ReturnType<typeof kur>>
const dugme = (w: W, metin: string) => w.findAll('button').find((b) => b.text() === metin)!
const gonder = async (w: W) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('SeferBirlestirDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('açılışta yolun sürümüyle onizle:true çağırır; birleşik satır, silinecek parça sayısı ve uyarı görünür', async () => {
    vi.mocked(atamaApi.birlestir).mockResolvedValue(sonuc({ silinenSeferIdler: [12, 13] }))
    const w = await kur()
    expect(seferApi.getir).toHaveBeenCalledWith(11)
    expect(atamaApi.birlestir).toHaveBeenCalledTimes(1)
    expect(atamaApi.birlestir).toHaveBeenCalledWith(11, { versiyon: 4, onizle: true })
    const satir = w.get('table tbody tr').text()
    expect(satir).toContain('Ankara→Kars')
    expect(satir).toContain('08:00–23:00')
    expect(satir).toContain('15s0dk')
    expect(satir).toContain('1000 km')
    expect(w.text()).toContain('2 parça satırı silinecek')
    expect(w.text()).toContain('Parça 2..N satırları ve atamaları silinir; geri alınamaz.')
    expect(w.text()).toContain('yalnız bölme hatasını geri almak için kullanın')
    expect(w.find('[data-test="yeni-kirmizi"]').exists()).toBe(false)
    expect(dugme(w, 'Birleştir').attributes('disabled')).toBeUndefined()
  })

  it('yeniKirmizi doluysa atama ve nedenler listelenir', async () => {
    vi.mocked(atamaApi.birlestir).mockResolvedValue(
      sonuc({
        yeniKirmizi: [{ atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme', 'süre'] }],
      }),
    )
    const w = await kur()
    const kutu = w.get('[data-test="yeni-kirmizi"]').text()
    expect(kutu).toContain('T1030: dinlenme; süre')
  })

  it.each([
    [409, 'BIRLESTIRME_ATAMA_FARKLI', '2. parça: ASIL farklı (1234).'],
    [409, 'ZINCIR_EKSIK', 'Zincir eksik.'],
    [422, 'SEFER_BASLAMIS', 'Başlamış sefer birleştirilemez.'],
  ])(
    'önizlemede %i %s: sunucu mesajı gösterilir, Birleştir kapalı, uygulama çağrılmaz',
    async (d, kod, detay) => {
      vi.mocked(atamaApi.birlestir).mockRejectedValue(new ApiHata(d, kod, detay))
      const w = await kur()
      expect(w.get('[role="alert"]').text()).toContain(detay)
      expect(dugme(w, 'Birleştir').attributes('disabled')).toBeDefined()
      expect(w.find('table').exists()).toBe(false)
      await gonder(w)
      expect(atamaApi.birlestir).toHaveBeenCalledTimes(1)
    },
  )

  it('onayla -> onizle:false ile uygular, tamam(mesaj) yayar (yeniKirmizi mesajda)', async () => {
    vi.mocked(atamaApi.birlestir)
      .mockResolvedValueOnce(sonuc())
      .mockResolvedValueOnce(
        sonuc({
          yeniKirmizi: [{ atamaId: 5, seferId: 30, trenNo: 'T1030', nedenler: ['dinlenme'] }],
        }),
      )
    const w = await kur()
    await gonder(w)
    expect(atamaApi.birlestir).toHaveBeenLastCalledWith(11, { versiyon: 4, onizle: false })
    const mesaj = w.emitted('tamam')![0]![0] as string
    expect(mesaj).toContain('birleştirildi')
    expect(mesaj).toContain('T1030')
  })

  it('uygulamada 409 VERSIYON_CAKISMASI: mesaj gösterilir, tamam yayılmaz, kapatınca masa yenilenir', async () => {
    vi.mocked(atamaApi.birlestir)
      .mockResolvedValueOnce(sonuc())
      .mockRejectedValueOnce(
        new ApiHata(409, 'VERSIYON_CAKISMASI', 'Sefer başka biri tarafından değiştirildi.'),
      )
    const w = await kur()
    await gonder(w)
    expect(w.get('[role="alert"]').text()).toContain('başka biri')
    expect(w.emitted('tamam')).toBeUndefined()
    await dugme(w, 'Vazgeç').trigger('click')
    expect(w.emitted('kapat')![0]).toEqual([true])
  })

  it('sefer okunamazsa hata gösterilir ve API çağrılmaz', async () => {
    vi.mocked(seferApi.getir).mockRejectedValue(new ApiHata(404, 'BULUNAMADI', 'Sefer yok.'))
    const w = mount(SeferBirlestirDialog, { props: { seferId: 11, seferEtiketi: 'x' } })
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('Sefer yok.')
    expect(atamaApi.birlestir).not.toHaveBeenCalled()
    expect(dugme(w, 'Birleştir').attributes('disabled')).toBeDefined()
  })

  it('önizleme hatası yoksa Vazgeç yenilemesiz kapatır', async () => {
    vi.mocked(atamaApi.birlestir).mockResolvedValue(sonuc())
    const w = await kur()
    await dugme(w, 'Vazgeç').trigger('click')
    expect(w.emitted('kapat')![0]).toEqual([false])
  })
})
