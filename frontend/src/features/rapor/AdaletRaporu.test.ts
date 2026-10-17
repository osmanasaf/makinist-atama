import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { adalet: vi.fn(), adaletExcel: vi.fn() },
}))

import AdaletRaporu from './AdaletRaporu.vue'
import { raporApi } from './raporApi'
import { adaletYaniti } from './testVerisi'

const bos = { template: '<div />' }

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rapor/adalet', name: 'rapor-adalet', component: bos },
      { path: '/yazdir/adalet', name: 'yazdir-adalet', component: bos },
    ],
  })
  await router.push('/rapor/adalet')
  const w = mount(AdaletRaporu, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const getir = async (w: Sayfa) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('AdaletRaporu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(raporApi.adalet).mockResolvedValue(adaletYaniti)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('ay seçici varsayılan olarak bu ay; sayfa açılınca rapor otomatik getirilir, düğme kalır', async () => {
    const w = await kur()
    const ay = bugun().slice(0, 7)
    expect((w.get('#adalet-ay').element as HTMLInputElement).value).toBe(ay)
    expect(raporApi.adalet).toHaveBeenCalledTimes(1)
    expect(raporApi.adalet).toHaveBeenCalledWith(ay)
    expect(w.find('table').exists()).toBe(true)
    expect(w.text()).toContain('Excel indir')
    expect(w.text()).toContain('Raporu getir')
  })

  it('personel türü değişince eski türün raporu ekranda kalmaz', async () => {
    const w = await kur()
    expect(w.find('table').exists()).toBe(true)
    await w.get('#adalet-tur').setValue('KONDUKTOR')
    expect(w.find('table').exists()).toBe(false)
    expect(w.text()).not.toContain('Excel indir')
  })

  it('otomatik getirme hatası gösterilir', async () => {
    vi.mocked(raporApi.adalet).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Rapor alınamadı.'))
    const w = await kur()
    expect(w.get('[role="alert"]').text()).toBe('Rapor alınamadı.')
    expect(w.find('table').exists()).toBe(false)
  })

  it('Raporu getir: ay ile çağırır; üretim zamanı, ortalama, ölçüt ve gece penceresi görünür', async () => {
    const w = await kur()
    await w.get('#adalet-ay').setValue('2026-10')
    await getir(w)
    expect(raporApi.adalet).toHaveBeenCalledWith('2026-10')
    const ozet = w.get('[data-test="ozet"]').text().replace(/\s+/g, ' ')
    expect(ozet).toContain('Üretim zamanı: 12.10.2026 10:20')
    expect(ozet).toContain('Üreten: Mehmet Demir')
    const ort = w.get('[data-test="ortalama"]').text().replace(/\s+/g, ' ')
    expect(ort).toContain('Ortalama görev saati: 10')
    expect(ort).toContain('Ortalama km: 500,5')
    expect(w.get('[data-test="olcut"]').text()).toContain('20:00–06:00')
  })

  it('tablo: Türkçe sayılar, işaretli fark, "0 saat" etiketi metin + simge, null fark "—"', async () => {
    const w = await kur()
    await getir(w)
    const tablo = w.get('table')
    expect(tablo.attributes('aria-label')).toBe('Adalet raporu')
    const satirlar = w.findAll('tbody tr')
    expect(satirlar).toHaveLength(3)
    const a = satirlar[0]!.text().replace(/\s+/g, ' ')
    expect(a).toContain('D001')
    expect(a).toContain('Ali Veli')
    expect(a).toContain('3,5')
    expect(a).toContain('+%60')
    expect(a).not.toContain('0 saat')
    const b = satirlar[1]!.text().replace(/\s+/g, ' ')
    expect(b).toContain('⚠ 0 saat')
    expect(b).toContain('−%100')
    const c = satirlar[2]!.text().replace(/\s+/g, ' ')
    expect(c).toContain('701,25')
    expect(c).toMatch(/—$/)
  })

  it('km bilinmeyen sefer varsa dipnot gösterilir, yoksa gösterilmez', async () => {
    const w = await kur()
    await getir(w)
    expect(w.get('[data-test="km-dipnot"]').text()).toContain(
      '2 seferin km bilgisi girilmemiş; km toplamlarına dahil değil',
    )
    vi.mocked(raporApi.adalet).mockResolvedValue({ ...adaletYaniti, kmBilinmeyenSefer: 0 })
    await getir(w)
    expect(w.find('[data-test="km-dipnot"]').exists()).toBe(false)
  })

  it('Yazdır: uygulanan ay ile yeni sekmede yazdır rotasına bağlanır', async () => {
    const w = await kur()
    await w.get('#adalet-ay').setValue('2026-10')
    await getir(w)
    const a = w.get('a[target="_blank"]')
    expect(a.text()).toBe('Yazdır')
    expect(a.attributes('href')).toBe('/yazdir/adalet?ay=2026-10')
  })

  it('Excel indir: görünen raporun ayıyla indirir; dosya adı sunucudan', async () => {
    const tikla = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    })
    vi.mocked(raporApi.adaletExcel).mockResolvedValue({
      blob: new Blob(['x']),
      ad: 'adalet-2026-10.xlsx',
    })
    const w = await kur()
    await w.get('#adalet-ay').setValue('2026-10')
    await getir(w)
    await w.get('#adalet-ay').setValue('2026-01') // form değişse de görünen rapor esas
    await w
      .findAll('button')
      .find((b) => b.text() === 'Excel indir')!
      .trigger('click')
    await flushPromises()
    expect(raporApi.adaletExcel).toHaveBeenCalledWith('2026-10')
    expect((tikla.mock.contexts[0] as HTMLAnchorElement).download).toBe('adalet-2026-10.xlsx')
    tikla.mockRestore()
  })

  it('Excel indirilemezse hata gösterilir', async () => {
    vi.mocked(raporApi.adaletExcel).mockRejectedValue(
      new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'),
    )
    const w = await kur()
    await getir(w)
    await w
      .findAll('button')
      .find((b) => b.text() === 'Excel indir')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toBe('Sunucuya ulaşılamadı.')
  })

  it('sunucu hatası gösterilir ve eski rapor kalkar', async () => {
    const w = await kur()
    await getir(w)
    expect(w.findAll('tbody tr')).toHaveLength(3)
    vi.mocked(raporApi.adalet).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    await getir(w)
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(w.findAll('tbody tr')).toHaveLength(0)
  })
})
