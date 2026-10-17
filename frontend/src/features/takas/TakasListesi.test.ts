import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./takasApi', () => ({
  takasApi: { liste: vi.fn(), detay: vi.fn(), onay: vi.fn(), red: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import { takasApi, type TakasSatiri } from './takasApi'
import TakasListesi from './TakasListesi.vue'

function satir(p: Partial<TakasSatiri> = {}): TakasSatiri {
  return {
    id: 1,
    tur: 'TAKAS',
    durum: 'KARSI_ONAY_BEKLIYOR',
    kayitliDurum: 'KARSI_ONAY_BEKLIYOR',
    kaynakSeferId: 1,
    kaynakMakinistId: 1,
    kaynakSicil: 'D001',
    kaynakAd: 'Ali Veli',
    kaynakGorev: 'Asıl · Ankara 15.10 08:00 → Ankara 15.10 09:00',
    hedefMakinistId: 2,
    hedefSicil: 'D002',
    hedefAd: 'Ayşe Kaya',
    hedefGorev: 'Yardımcı · Ankara 16.10 08:00 → Ankara 16.10 09:00',
    neden: 'IZIN',
    nedenNotu: null,
    olusturma: '2026-10-13T07:00:00Z',
    sonKabul: null,
    sonKalkis: '2026-10-15T05:00:00Z',
    ...p,
  }
}

describe('TakasListesi', () => {
  beforeEach(() => vi.resetAllMocks())

  it('yükleniyor, boş, hata', async () => {
    let bitir!: (v: never) => void
    vi.mocked(takasApi.liste).mockReturnValue(new Promise((c) => (bitir = c as never)))
    const w = mount(TakasListesi)
    expect(w.text()).toContain('Yükleniyor')
    bitir({ bekleyen: 0, talepler: [] } as never)
    await flushPromises()
    expect(w.text()).toContain('talebi yok')

    vi.mocked(takasApi.liste).mockRejectedValue(new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası.'))
    const h = mount(TakasListesi)
    await flushPromises()
    expect(h.get('[role="alert"]').text()).toContain('Sunucu hatası.')
  })

  it('onay bekleyenler üstte ve vurgulu; sayaç; durum metin + simgeyle', async () => {
    vi.mocked(takasApi.liste).mockResolvedValue({
      bekleyen: 1,
      talepler: [
        satir({ id: 1, durum: 'ONAYLANDI' }),
        satir({
          id: 2,
          durum: 'ATAMACI_ONAYI_BEKLIYOR',
          tur: 'BIRAK',
          hedefSicil: null,
          hedefGorev: null,
          nedenNotu: 'ates',
        }),
      ],
    })
    const w = mount(TakasListesi)
    await flushPromises()
    expect(w.get('[data-test="takas-bekleyen"]').text()).toContain('1')
    const satirlar = w.findAll('tbody tr')
    expect(satirlar[0]!.attributes('data-test')).toBe('talep-2')
    expect(satirlar[0]!.text()).toContain('D001 bırakıyor')
    expect(satirlar[0]!.text()).toContain('Atamacı onayı bekleniyor')
    expect(satirlar[0]!.text()).toContain('ates')
    expect(satirlar[0]!.get('button').text()).toBe('İncele')
    expect(satirlar[1]!.text()).toContain('D001 ⇄ D002')
    expect(satirlar[1]!.get('button').text()).toBe('Göster')
  })

  it('İncele onay ekranını açar; işlem bitince liste yenilenir ve bilgi gösterilir', async () => {
    vi.mocked(takasApi.liste).mockResolvedValue({
      bekleyen: 1,
      talepler: [satir({ id: 2, durum: 'ATAMACI_ONAYI_BEKLIYOR' })],
    })
    vi.mocked(takasApi.detay).mockResolvedValue({
      talep: satir({ id: 2, durum: 'ATAMACI_ONAYI_BEKLIYOR' }),
      ihlaller: [],
      oneri: null,
    })
    vi.mocked(takasApi.red).mockResolvedValue(satir({ id: 2, durum: 'REDDEDILDI' }))
    const w = mount(TakasListesi, { attachTo: document.body })
    await flushPromises()
    await w.get('tbody button').trigger('click')
    await flushPromises()
    expect(takasApi.detay).toHaveBeenCalledWith(2)
    expect(w.find('dialog').exists()).toBe(true)

    vi.mocked(takasApi.liste).mockResolvedValue({
      bekleyen: 0,
      talepler: [satir({ id: 2, durum: 'REDDEDILDI' })],
    })
    await w
      .findAll('dialog button')
      .find((b) => b.text() === 'Reddet')!
      .trigger('click')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.get('[role="status"] .bildirim').text()).toContain('reddedildi')
    expect(w.get('[data-test="takas-bekleyen"]').text()).toContain('0')
  })
})
