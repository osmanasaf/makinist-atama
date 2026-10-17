import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { yedek: vi.fn() },
}))

import YedekTakvimi from './YedekTakvimi.vue'
import { raporApi, type YedekRaporu } from './raporApi'

const yanit: YedekRaporu = {
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  aktifMakinist: 10,
  oran: 0.15,
  min: 2,
  eksikGunSayisi: 1,
  toplamAcik: 3,
  gunler: [
    { tarih: '2026-10-12', planliSefer: 5, ihtiyac: 4, mevcut: 6, acik: 0, izinli: 1, gorevli: 5 },
    { tarih: '2026-10-13', planliSefer: 8, ihtiyac: 7, mevcut: 4, acik: 3, izinli: 2, gorevli: 8 },
  ],
}

const getir = async (w: ReturnType<typeof mount>) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('YedekTakvimi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(raporApi.yedek).mockResolvedValue(yanit)
  })

  it('varsayılan ay bu ay; sayfa açılınca rapor otomatik getirilir, düğme kalır', async () => {
    const w = mount(YedekTakvimi)
    await flushPromises()
    const ay = bugun().slice(0, 7)
    expect((w.get('#yedek-ay').element as HTMLInputElement).value).toBe(ay)
    expect(raporApi.yedek).toHaveBeenCalledTimes(1)
    expect(raporApi.yedek).toHaveBeenCalledWith(ay)
    expect(w.find('table').exists()).toBe(true)
    expect(w.text()).toContain('Raporu getir')
  })

  it('otomatik getirme hatası gösterilir', async () => {
    vi.mocked(raporApi.yedek).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Rapor alınamadı.'))
    const w = mount(YedekTakvimi)
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toBe('Rapor alınamadı.')
  })

  it('oran Türkçe virgüllü gösterilir', async () => {
    const w = mount(YedekTakvimi)
    await flushPromises()
    expect(w.get('[data-test="yer-tutucu"]').text()).toContain('oran 0,15')
  })

  it('özet: eksik gün, toplam açık ve yer tutucu etiketi (oran, en az)', async () => {
    const w = mount(YedekTakvimi)
    await w.get('#yedek-ay').setValue('2026-10')
    await getir(w)
    expect(raporApi.yedek).toHaveBeenCalledWith('2026-10')
    const ozet = w.get('[data-test="ozet"]').text().replace(/\s+/g, ' ')
    expect(ozet).toContain('Eksik gün sayısı: 1')
    expect(ozet).toContain('Toplam açık: 3')
    expect(w.get('[data-test="yer-tutucu"]').text().replace(/\s+/g, ' ')).toBe(
      'Yedek oranı ve asgari sayı kaynaksız yer tutucudur (oran 0,15, en az 2).',
    )
  })

  it('gün satırları: açıklı gün "⚠ N eksik" (metin + simge), açıksız "Yeterli"', async () => {
    const w = mount(YedekTakvimi)
    await getir(w)
    expect(w.get('table').attributes('aria-label')).toBe('Günlük yedek planı')
    const satirlar = w.findAll('tbody tr')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('12.10.2026')
    expect(satirlar[0]!.text()).toContain('Yeterli')
    expect(satirlar[0]!.classes()).not.toContain('eksik')
    expect(satirlar[1]!.text().replace(/\s+/g, ' ')).toContain('⚠ 3 eksik')
    expect(satirlar[1]!.classes()).toContain('eksik')
  })

  it('sunucu hatası gösterilir', async () => {
    vi.mocked(raporApi.yedek).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    const w = mount(YedekTakvimi)
    await getir(w)
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(w.find('table').exists()).toBe(false)
  })
})
