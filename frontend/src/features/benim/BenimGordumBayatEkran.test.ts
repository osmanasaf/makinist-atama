import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: { gorevler: vi.fn(), gordum: vi.fn(), olayiKapat: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import BenimGorevler from './BenimGorevler.vue'
import { benimApi, type BenimGorev } from './benimApi'

const eski = 'Asıl · Ankara 12.10 08:10 → Sivas 12.10 10:00'
const yeni = 'Asıl · Ankara 12.10 09:40 → Sivas 12.10 11:30'

function gorev(p: Partial<BenimGorev>): BenimGorev {
  return {
    atamaId: 1,
    seferId: 1,
    tarih: '2026-10-12',
    sira: 1,
    trenNo: 'T1',
    rol: 'ASIL',
    durum: 'PLANLI',
    kalkisGar: 'Ankara',
    varisGar: 'Sivas',
    planlananKalkis: '2026-10-12T05:10:00Z',
    planlananVaris: '2026-10-12T07:00:00Z',
    guncelKalkis: '2026-10-12T05:10:00Z',
    guncelVaris: '2026-10-12T07:00:00Z',
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    varisErtesiGun: false,
    aracTipi: 'YHT',
    hat: 'Ankara-Sivas',
    km: 250,
    esMakinist: null,
    gordu: 'YENI',
    gorduZamani: null,
    ozet: eski,
    eskiOzet: null,
    ...p,
  }
}

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

describe('BenimGorevler: bayat ekran (05-review Tur 2 W1)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('409 OZET_DEGISTI: kart sunucunun güncel hâliyle değişir, mesaj gösterilir, onay yazılmış görünmez; yeni özetle yeniden basılabilir', async () => {
    vi.mocked(benimApi.gorevler).mockResolvedValue({
      bugun: '2026-10-12',
      ufukGun: 7,
      gorevler: [gorev({})],
      kaldirilanlar: [],
    })
    const guncel = gorev({
      ozet: yeni,
      gordu: 'YENI',
      guncelKalkis: '2026-10-12T06:40:00Z',
      guncelVaris: '2026-10-12T08:30:00Z',
    })
    vi.mocked(benimApi.gordum).mockRejectedValueOnce(
      new ApiHata(
        409,
        'OZET_DEGISTI',
        'Görev siz bakarken değişti. Yeni hâline bakıp yeniden onaylayın.',
        {
          gorev: guncel,
        },
      ),
    )
    const w = mount(BenimGorevler)
    kurulan.push(w)
    await flushPromises()
    // yoklama henüz yenilemedi: ekranda eski özet var, sunucuda yeni
    vi.mocked(benimApi.gorevler).mockResolvedValue({
      bugun: '2026-10-12',
      ufukGun: 7,
      gorevler: [guncel],
      kaldirilanlar: [],
    })

    await w.get('article button').trigger('click')
    await flushPromises()

    expect(benimApi.gordum).toHaveBeenCalledWith(1, eski)
    expect(w.get('[role="alert"]').text()).toContain('Görev siz bakarken değişti')
    expect(w.get('article').text()).toContain('09:40')
    expect(w.get('article').text()).not.toContain('Gördünüz')

    vi.mocked(benimApi.gordum).mockResolvedValue(gorev({ ozet: yeni, gordu: 'GORULDU' }))
    await w.get('article button').trigger('click')
    await flushPromises()
    expect(benimApi.gordum).toHaveBeenLastCalledWith(1, yeni) // artık yeni özeti görüp basıyor
  })
})
