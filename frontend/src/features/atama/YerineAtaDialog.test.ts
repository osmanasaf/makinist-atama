import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { oneri: vi.fn() },
}))

import YerineAtaDialog from './YerineAtaDialog.vue'
import { atamaApi, type Oneri } from './atamaApi'

const oneri = (id: number, ek: Partial<Oneri> = {}): Oneri => ({
  makinistId: id,
  sicil: `D00${id}`,
  adSoyad: `Aday ${id}`,
  bagliGar: 'Ankara',
  buAySaat: 100.5,
  buAyKm: 5000,
  uyarilar: [],
  gerekce: `Gerekçe ${id}`,
  ...ek,
})

async function kur() {
  const w = mount(YerineAtaDialog, {
    props: { seferId: 11, seferEtiketi: 'T1001 Ankara – Eskişehir 08:30' },
  })
  await flushPromises()
  return w
}

describe('YerineAtaDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('liste etiketi role göre: varsayılan makinist, kondüktör rolünde kondüktör (F3b BUG-2)', async () => {
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [oneri(1)],
      uygunAdayYok: false,
      mesaj: null,
    })
    const w = await kur()
    expect(w.get('table').attributes('aria-label')).toBe('Önerilen makinistler')
    expect(w.get('thead th').text()).toBe('Makinist')
    const k = mount(YerineAtaDialog, {
      props: { seferId: 11, seferEtiketi: 'T1', rol: 'KONDUKTOR' },
    })
    await flushPromises()
    expect(k.get('table').attributes('aria-label')).toBe('Önerilen kondüktörler')
    expect(k.get('thead th').text()).toBe('Kondüktör')
  })

  it('öneri listesi: ad, sicil, bu ay saat/km, gerekçe ve uyarılar', async () => {
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [
        oneri(1),
        oneri(2, {
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
        oneri(3),
      ],
      uygunAdayYok: false,
      mesaj: null,
    })
    const w = await kur()
    expect(atamaApi.oneri).toHaveBeenCalledWith(11)
    const satirlar = w.findAll('tbody tr')
    expect(satirlar).toHaveLength(3)
    expect(satirlar[0]!.text()).toContain('Aday 1')
    expect(satirlar[0]!.text()).toContain('D001')
    expect(satirlar[0]!.text()).toContain('100,5')
    expect(satirlar[0]!.text()).toContain('5000')
    expect(satirlar[0]!.text()).toContain('Gerekçe 1')
    expect(satirlar[0]!.find('svg[data-simge="uyari"]').exists()).toBe(false)
    expect(satirlar[1]!.text()).toContain('Günlük 9 s > 8 s')
    expect(satirlar[1]!.find('svg[data-simge="uyari"]').exists()).toBe(true)
  })

  it('Seç: öneriyi yayınlar', async () => {
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [oneri(1), oneri(2)],
      uygunAdayYok: false,
      mesaj: null,
    })
    const w = await kur()
    await w.findAll('tbody button')[1]!.trigger('click')
    expect(w.emitted('sec')).toEqual([[oneri(2)]])
  })

  it('uygun aday yoksa sunucu mesajı gösterilir, liste yoktur', async () => {
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [],
      uygunAdayYok: true,
      mesaj: 'Uygun aday yok. 0/5 uygun: hat yetkisi 2, izin 3',
    })
    const w = await kur()
    expect(w.get('[role="status"]').text()).toContain(
      'Uygun aday yok. 0/5 uygun: hat yetkisi 2, izin 3',
    )
    expect(w.find('table').exists()).toBe(false)
  })

  it('yükleme hatası gösterilir; Kapat kapat yayınlar', async () => {
    vi.mocked(atamaApi.oneri).mockRejectedValue(
      new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'),
    )
    const w = await kur()
    expect(w.get('[role="alert"]').text()).toBe('Sunucuya ulaşılamadı.')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Kapat')!
      .trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })
})

describe('YerineAtaDialog: gerekçe tekrarı ve nöbet notu', () => {
  beforeEach(() => vi.resetAllMocks())
  const uyari = (metin: string) => ({
    kod: 'K-30',
    siddet: 'UYARI' as const,
    olculen: 1,
    esik: 1,
    birim: 's',
    metin,
  })

  it('gerekçedeki "; uyarı: …" kuyruğu atılır, uyarı yalnız madde olarak bir kez görünür', async () => {
    const metin = 'Günlük 9 s > 8 s'
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [
        oneri(1, {
          gerekce: `Bu ay 100 saat, 5000 km; uyarı: ${metin}`,
          uyarilar: [uyari(metin)],
        }),
      ],
      uygunAdayYok: false,
      mesaj: null,
    })
    const w = await kur()
    const t = w.get('tbody tr').text()
    expect(t).toContain('Bu ay 100 saat, 5000 km')
    expect(t).not.toContain('uyarı:')
    expect(t.split(metin)).toHaveLength(2)
  })

  it('uyarı metni nöbet içeriyorsa tek not görünür; yoksa görünmez', async () => {
    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [
        oneri(1, { uyarilar: [uyari('Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk')] }),
        oneri(2, { uyarilar: [uyari('Nöbet toplamı 9 s')] }),
      ],
      uygunAdayYok: false,
      mesaj: null,
    })
    expect((await kur()).findAll('[data-test="nobet-notu"]')).toHaveLength(1)

    vi.mocked(atamaApi.oneri).mockResolvedValue({
      oneriler: [oneri(1, { uyarilar: [uyari('Günlük 9 s > 8 s')] })],
      uygunAdayYok: false,
      mesaj: null,
    })
    expect((await kur()).find('[data-test="nobet-notu"]').exists()).toBe(false)
  })
})
