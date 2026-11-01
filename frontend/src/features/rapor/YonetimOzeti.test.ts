import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'

vi.mock('./ozetApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./ozetApi')>()),
  ozetApi: { getir: vi.fn(), denetimPaketi: vi.fn() },
}))

import YonetimOzeti from './YonetimOzeti.vue'
import { ozetApi } from './ozetApi'
import { ornek } from './testVerisi'
import routes from './routes'
import yazdirRoutes from '@/features/yazdir/routes'

const bos = { template: '<div />' }
async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rapor/ozet', name: 'rapor-ozet', component: bos },
      { path: '/yazdir/ozet', name: 'yazdir-ozet', component: bos },
    ],
  })
  await router.push('/rapor/ozet')
  const w = mount(YonetimOzeti, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const getir = async (w: Sayfa) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}
const metin = (w: Sayfa, ad: string) => w.get(`[data-test="${ad}"]`).text().replace(/\s+/g, ' ')

describe('YonetimOzeti (F4, FR-4.5)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(ozetApi.getir).mockResolvedValue(ornek())
  })
  afterEach(() => vi.unstubAllGlobals())

  it('açılınca bu ayın özetini getirir; ay seçici bu ay, takvim ayı etiketi, üretim zamanı ve üreten', async () => {
    const w = await kur()
    const ay = bugun().slice(0, 7)
    expect((w.get('#ozet-ay').element as HTMLInputElement).value).toBe(ay)
    expect(ozetApi.getir).toHaveBeenCalledTimes(1)
    expect(ozetApi.getir).toHaveBeenCalledWith(ay)
    const bilgi = metin(w, 'ozet-bilgi')
    expect(bilgi).toContain('Dönem: 2026-10 (takvim ayı)')
    expect(bilgi).toContain('Üretim zamanı: 12.10.2026 10:20')
    expect(bilgi).toContain('Üreten: Mehmet Demir')
  })

  it('tüm alanlar: doluluk, kırmızı, uyarılı + gerekçeler, adalet dağılımı, fazla mesai, belge, değişiklik + nedenler, yedek açığı', async () => {
    const w = await kur()
    expect(metin(w, 'doluluk')).toContain('6 / 9 sefer dolu (%66,7)')
    expect(w.get('[data-test="doluluk-bos"]').text()).toBe('3')
    expect(w.get('[data-test="kirmizi-atama"]').text()).toBe('4')
    expect(metin(w, 'kirmizi')).toContain('3 sefer')
    expect(w.get('[data-test="uyarili-atama"]').text()).toBe('3')
    const gerekce = w.get('[data-test="uyarili"]').findAll('tbody tr')
    expect(gerekce).toHaveLength(2)
    expect(gerekce[0]!.text()).toContain('geçmiş kaydın düzeltilmesi')
    expect(gerekce[0]!.text()).toContain('2')
    expect(w.get('[data-test="adalet-n"]').text()).toBe('3')
    expect(w.get('[data-test="adalet-ortalama"]').text()).toBe('20 sa')
    expect(w.get('[data-test="adalet-std"]').text()).toBe('8,16 sa')
    expect(w.get('[data-test="adalet-min"]').text()).toBe('10 sa')
    expect(w.get('[data-test="adalet-max"]').text()).toBe('30 sa')
    expect(w.get('[data-test="fm-toplam"]').text()).toBe('12,5')
    expect(metin(w, 'fazla-mesai')).toContain('2 makinist')
    expect(metin(w, 'belge-etiket')).toContain('Bugünkü durum (12.10.2026)')
    const belge = w.get('[data-test="belge"]').findAll('tbody tr')
    expect(
      belge.map((r) =>
        r
          .findAll('td')
          .slice(0, 2)
          .map((h) => h.text()),
      ),
    ).toEqual([
      ['Geçerli', '5'],
      ['Yaklaşan', '2'],
      ['Süresi dolmuş', '1'],
      ['Girilmemiş', '0'],
    ])
    expect(w.get('[data-test="degisiklik-sayi"]').text()).toBe('40')
    const neden = w.get('[data-test="degisiklik"]').findAll('tbody tr')
    expect(neden[0]!.text()).toContain('Hastalık') // sunucudaki kod okunur adla
    expect(w.get('[data-test="yedek-gun"]').text()).toBe('4')
  })

  it("standart sapma formülü ekranda yazar: popülasyon, n'e bölünür", async () => {
    const w = await kur()
    expect(metin(w, 'std-notu')).toContain("popülasyon sapmasıdır (n'e bölünür)")
  })

  it('dağılım çubukları süstür: aria-hidden, her çubuğun satırında sayı yazılı (renk dışı kodlama); yalnız tek seri', async () => {
    const w = await kur()
    const cubuklar = w.findAll('.cubuk')
    expect(cubuklar.length).toBeGreaterThanOrEqual(5)
    for (const c of cubuklar) expect(c.attributes('aria-hidden')).toBe('true')
    for (const satir of w.findAll('tbody tr')) expect(satir.text()).toMatch(/\d/)
    // en büyük değer çubuğu tam genişlik, orantılı diğerleri
    const gerekce = w.get('[data-test="uyarili"]').findAll('.cubuk-dolgu')
    expect(gerekce[0]!.attributes('style')).toContain('width: 100%')
    expect(gerekce[1]!.attributes('style')).toContain('width: 50%')
  })

  it('boş dönem: "Bu ay için veri yok."; adalette aktif makinist yoksa dağılım alanı yazılmaz', async () => {
    vi.mocked(ozetApi.getir).mockResolvedValue(
      ornek({
        doluluk: { toplamSefer: 0, dolu: 0, bos: 0, yuzde: null },
        kirmizi: { atama: 0, sefer: 0, intikal: 0 },
        uyarili: { atama: 0, gerekceler: [] },
        adalet: { n: 0, ortalamaSaat: null, standartSapmaSaat: null, minSaat: null, maxSaat: null },
        fazlaMesai: { toplamSaat: 0, makinistSayisi: 0 },
        degisiklik: { kayitSayisi: 0, nedenler: [] },
      }),
    )
    const w = await kur()
    expect(w.text()).toContain('Bu ay için veri yok.')
    expect(w.find('[data-test="doluluk-yuzde"]').exists()).toBe(false)
    expect(w.find('[data-test="adalet-std"]').exists()).toBe(false)
    expect(w.get('[data-test="adalet"]').text()).toContain('Aktif makinist yok.')
    expect(w.text()).not.toContain('NaN')
  })

  it('veri varken "veri yok" mesajı çıkmaz', async () => {
    const w = await kur()
    expect(w.text()).not.toContain('Bu ay için veri yok.')
  })

  it('hata: gösterilir, eski özet kalkar; ay değişimi yeni ayı ister', async () => {
    const w = await kur()
    expect(w.find('[data-test="doluluk"]').exists()).toBe(true)
    vi.mocked(ozetApi.getir).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    await w.get('#ozet-ay').setValue('2020-01')
    await getir(w)
    expect(ozetApi.getir).toHaveBeenLastCalledWith('2020-01')
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(w.find('[data-test="doluluk"]').exists()).toBe(false)
  })

  it('demo etiketi: yalnız demo veri varken "DEMO – gerçek değil"', async () => {
    expect((await kur()).find('[data-test="demo-etiketi"]').exists()).toBe(false)
    vi.mocked(ozetApi.getir).mockResolvedValue(ornek({ demo: true }))
    const w = await kur()
    expect(w.get('[data-test="demo-etiketi"]').text()).toContain('DEMO – gerçek değil')
  })

  it('Yazdırılabilir sayfa: görünen ayla yeni sekmede; Excel düğmesi özet gelince çıkar', async () => {
    const w = await kur()
    await w.get('#ozet-ay').setValue('2026-01') // form değişse de görünen özet esas
    const a = w.get('a[target="_blank"]')
    expect(a.text()).toBe('Yazdırılabilir sayfa')
    expect(a.attributes('href')).toBe(`/yazdir/ozet?ay=${bugun().slice(0, 7)}`)
    expect(w.text()).toContain('Denetim paketi (Excel)')
  })

  it('Denetim paketi (Excel): görünen ayla indirir, dosya adı sunucudan', async () => {
    const tikla = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    })
    vi.mocked(ozetApi.denetimPaketi).mockResolvedValue({
      blob: new Blob(['x']),
      ad: 'denetim-paketi-2026-10.xlsx',
    })
    const w = await kur()
    await w
      .findAll('button')
      .find((b) => b.text() === 'Denetim paketi (Excel)')!
      .trigger('click')
    await flushPromises()
    expect(ozetApi.denetimPaketi).toHaveBeenCalledWith(bugun().slice(0, 7))
    expect((tikla.mock.contexts[0] as HTMLAnchorElement).download).toBe(
      'denetim-paketi-2026-10.xlsx',
    )
    tikla.mockRestore()
  })

  it('Denetim paketi indirilemezse hata gösterilir, özet yerinde kalır', async () => {
    vi.mocked(ozetApi.denetimPaketi).mockRejectedValue(
      new ApiHata(0, 'AG_HATASI', 'Sunucuya ulaşılamadı.'),
    )
    const w = await kur()
    await w
      .findAll('button')
      .find((b) => b.text() === 'Denetim paketi (Excel)')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toBe('Sunucuya ulaşılamadı.')
    expect(w.find('[data-test="doluluk"]').exists()).toBe(true)
  })

  it('rota: yalnız yönetici (meta.rol Y), Raporlar menüsünde; yazdırma rotası menüsüz ve yönetici', () => {
    const r = routes.find((x) => x.name === 'rapor-ozet')!
    expect(r.path).toBe('/rapor/ozet')
    expect(r.meta?.rol).toBe('Y')
    expect(r.meta?.menu?.grup).toBe('Raporlar')
    const y = yazdirRoutes.find((x) => x.name === 'yazdir-ozet')!
    expect(y.path).toBe('/yazdir/ozet')
    expect(y.meta?.rol).toBe('Y')
    expect(y.meta?.yazdir).toBe(true)
    expect(y.meta?.menu).toBeUndefined()
  })
})
