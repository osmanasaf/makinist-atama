import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./cizelgeApi', () => ({ cizelgeApi: { getir: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/tanim/tanimApi')>()),
  tanimApi: { liste: vi.fn() },
}))

import AylikCizelge from './AylikCizelge.vue'
import { cizelgeApi, type CizelgeSatiri, type CizelgeYaniti } from './cizelgeApi'
import { tanimApi } from '@/features/tanim/tanimApi'

const bos = { template: '<div />' }
const satir = (id: number, ek: Partial<CizelgeSatiri> = {}): CizelgeSatiri => ({
  makinistId: id,
  sicil: `D00${id}`,
  adSoyad: `Makinist ${id}`,
  bagliGar: 'Ankara',
  gorevSaat: 16,
  geceSaat: 2.5,
  km: 500,
  seferSayisi: 2,
  yolculukSayisi: 0,
  izinGunu: 0,
  hucreler: [],
  ...ek,
})
const yanit = (ek: Partial<CizelgeYaniti> = {}): CizelgeYaniti => ({
  ay: '2026-10',
  gunSayisi: 31,
  tur: 'MAKINIST',
  garId: null,
  kontrol: false,
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Ata Maci',
  tatiller: [{ gun: 29, ad: 'Cumhuriyet Bayramı' }],
  satirlar: [
    satir(1, {
      hucreler: [
        {
          gun: 1,
          gorevler: [
            { seferId: 7, trenNo: 'T1', kalkis: '06:00', varis: '14:00', rol: 'ASIL' },
            { seferId: 8, trenNo: 'T9', kalkis: '15:00', varis: '17:00', rol: 'ASIL' },
          ],
        },
        {
          gun: 12,
          gorevler: [
            { seferId: 9, trenNo: 'T2', kalkis: '22:00', varis: '06:00', rol: 'ASIL', gece: true },
          ],
        },
      ],
    }),
    satir(2, {
      hucreler: [
        { gun: 5, izin: 'YILLIK' },
        { gun: 6, izin: 'YILLIK' },
      ],
    }),
    satir(3, { adSoyad: 'Zeynep Kaya', sicil: 'D003' }),
  ],
  ...ek,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rapor/cizelge', name: 'rapor-cizelge', component: AylikCizelge },
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/cizelge', name: 'yazdir-cizelge', component: bos },
    ],
  })
  await router.push('/rapor/cizelge')
  const w = mount(AylikCizelge, { attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return { w, router }
}

describe('Aylık çizelge ızgarası (F5, AK-5.5..5.7, 5.9)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit())
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'Eskişehir' },
    ])
  })

  it('açılışta bu ay istenir; tablo semantiği: caption, sütun başlıkları (scope=col), satır başlıkları (scope=row)', async () => {
    const { w } = await kur()
    expect(cizelgeApi.getir).toHaveBeenCalledTimes(1)
    expect(vi.mocked(cizelgeApi.getir).mock.calls[0]![0]).toMatch(/^\d{4}-\d{2}$/)
    expect(w.get('table caption').text()).toContain('Aylık çizelge')
    // Personel + 31 gün + 5 toplam
    expect(w.findAll('thead th[scope="col"]')).toHaveLength(1 + 31 + 5)
    const satirBasliklari = w.findAll('tbody th[scope="row"]')
    expect(satirBasliklari).toHaveLength(3)
    expect(satirBasliklari[0]!.text()).toContain('D001')
    expect(satirBasliklari[0]!.text()).toContain('Makinist 1')
    expect(w.findAll('tbody tr')).toHaveLength(3)
    expect(w.findAll('tbody tr')[0]!.findAll('td.hucre')).toHaveLength(31)
  })

  it('hücre içeriği: kısa kod, "+N", gece simgesi + metin, izin "İ"; renk tek başına anlam taşımaz', async () => {
    const { w } = await kur()
    const d1 = w.findAll('tbody tr')[0]!
    const gun1 = d1.findAll('td.hucre')[0]!
    expect(gun1.text()).toContain('T1')
    expect(gun1.text()).toContain('+1')
    expect(gun1.text()).toContain('06:00')
    const gun12 = d1.findAll('td.hucre')[11]!
    expect(gun12.find('svg[data-simge="ay"]').exists()).toBe(true)
    expect(gun12.get('a').attributes('aria-label')).toContain('gece görevi') // erişilebilir ad
    const izin = w.findAll('tbody tr')[1]!.findAll('td.hucre')[4]!
    expect(izin.text()).toContain('İ')
    expect(izin.text()).toContain('yıllık (onaylı izin)')
    // boş hücre: içerik yok
    expect(d1.findAll('td.hucre')[1]!.text()).toBe('')
  })

  it('hafta sonu ve resmi tatil sütunları başlıkta metinle işaretli (Ekim 2026: 9 hafta sonu günü; 29 Ekim tatil)', async () => {
    const { w } = await kur()
    // yanıt ayı 2026-10; açılışta bugünün ayı istenir ama başlıklar yanıttaki aya göre çizilir
    const basliklar = w.findAll('thead th.gun')
    expect(basliklar).toHaveLength(31)
    expect(basliklar.filter((t) => t.classes().includes('hafta-sonu'))).toHaveLength(9)
    const tatil = basliklar[28]!
    expect(tatil.classes()).toContain('tatil')
    expect(tatil.text()).toContain('Tatil')
    expect(tatil.text()).toContain('Cumhuriyet Bayramı')
    expect(basliklar[2]!.text()).toContain('hafta sonu') // 3 Ekim Cumartesi
    expect(basliklar[0]!.text()).toContain('Per') // 1 Ekim 2026 Perşembe
  })

  it('satır toplamları sunucudaki adalet toplamlarıdır (saat, gece, km, sefer, izin)', async () => {
    const { w } = await kur()
    const toplam = w.findAll('tbody tr')[0]!.findAll('td.toplam')
    expect(toplam.map((t) => t.text())).toEqual(['16', '2,5', '500', '2', '0'])
  })

  it('derin bağlantı: görev hücresi gün + sefer ile atama masasını açar (tıklama)', async () => {
    const { w, router } = await kur()
    const baglanti = w.findAll('tbody tr')[0]!.findAll('td.hucre')[0]!.get('a')
    expect(baglanti.attributes('href')).toBe('/atama?tarih=2026-10-01&sefer=7')
    // OK-50 (O-6, WCAG 2.5.3): erişilebilir ad görünen metinle ("T1+1 06:00") başlar, önceki ad aynen devam eder
    expect(baglanti.attributes('aria-label')).toBe(
      'T1+1 06:00: Makinist 1, 1 Ekim 2026: T1 06:00–14:00, Asıl; T9 15:00–17:00, Asıl',
    )
    await baglanti.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/atama?tarih=2026-10-01&sefer=7')
  })

  it('izin hücresi bağlantı değildir (sefer yok); boş hücre de değil', async () => {
    const { w } = await kur()
    expect(w.findAll('tbody tr')[1]!.findAll('td.hucre')[4]!.find('a').exists()).toBe(false)
    expect(w.findAll('tbody tr')[2]!.find('a').exists()).toBe(false)
  })

  it('klavye: tek sekme durağı (roving); oklar hücreler arasında gezer; Enter bağlantıyı açar', async () => {
    const { w, router } = await kur()
    const hucreler = () => w.findAll('td.hucre')
    expect(hucreler().filter((h) => h.attributes('tabindex') === '0')).toHaveLength(1)
    const ilk = w.findAll('tbody tr')[0]!.findAll('td.hucre')[0]!
    ;(ilk.element as HTMLElement).focus()
    await ilk.trigger('focusin')
    await ilk.trigger('keydown', { key: 'ArrowRight' })
    expect(document.activeElement).toBe(w.findAll('tbody tr')[0]!.findAll('td.hucre')[1]!.element)
    await w.findAll('tbody tr')[0]!.findAll('td.hucre')[1]!.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(w.findAll('tbody tr')[1]!.findAll('td.hucre')[1]!.element)
    await w.findAll('tbody tr')[1]!.findAll('td.hucre')[1]!.trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(w.findAll('tbody tr')[1]!.findAll('td.hucre')[30]!.element)
    await w.findAll('tbody tr')[1]!.findAll('td.hucre')[30]!.trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(w.findAll('tbody tr')[1]!.findAll('td.hucre')[0]!.element)
    // roving: tabindex=0 artık odaktaki hücrede
    expect(w.findAll('td.hucre').filter((h) => h.attributes('tabindex') === '0')).toHaveLength(1)
    expect(w.findAll('tbody tr')[1]!.findAll('td.hucre')[0]!.attributes('tabindex')).toBe('0')
    // üst satırdan çıkılmaz
    await w.findAll('tbody tr')[0]!.findAll('td.hucre')[0]!.trigger('keydown', { key: 'ArrowUp' })
    // Enter: görev hücresinde atama masası
    const gorev = w.findAll('tbody tr')[0]!.findAll('td.hucre')[0]!
    await gorev.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/atama?tarih=2026-10-01&sefer=7')
  })

  it('salt okunur: ızgarada düğme/atama eylemi yok; yalnız GET (cizelgeApi.getir tek işlev)', async () => {
    const { w } = await kur()
    const tablo = w.get('table')
    expect(tablo.findAll('button')).toHaveLength(0)
    expect(w.text()).not.toMatch(/Kaldır|Yerine ata|Otomatik ata/)
    expect(Object.keys(cizelgeApi)).toEqual(['getir'])
  })

  it('süzgeçler: tür, gar, kontrol istekle gider; arama istemcide süzer (yeni istek yok)', async () => {
    const { w } = await kur()
    const [ay0] = vi.mocked(cizelgeApi.getir).mock.calls[0]!
    // tür
    const turDugmeleri = w.findAll('.suzgec[aria-label="Personel türü"] button')
    expect(turDugmeleri.map((b) => b.attributes('aria-pressed'))).toEqual(['true', 'false'])
    await turDugmeleri[1]!.trigger('click')
    await flushPromises()
    expect(cizelgeApi.getir).toHaveBeenLastCalledWith(ay0, {
      tur: 'KONDUKTOR',
      garId: null,
      kontrol: false,
    })
    // gar
    await w.get('select#cizelge-gar').setValue('2')
    await flushPromises()
    expect(cizelgeApi.getir).toHaveBeenLastCalledWith(ay0, {
      tur: 'KONDUKTOR',
      garId: 2,
      kontrol: false,
    })
    // kontrol
    const kontrol = w.get('button[data-test="kontrol"]')
    expect(kontrol.attributes('aria-pressed')).toBe('false')
    await kontrol.trigger('click')
    await flushPromises()
    expect(w.get('button[data-test="kontrol"]').attributes('aria-pressed')).toBe('true')
    expect(cizelgeApi.getir).toHaveBeenLastCalledWith(ay0, {
      tur: 'KONDUKTOR',
      garId: 2,
      kontrol: true,
    })
    // arama: istemci
    const cagri = vi.mocked(cizelgeApi.getir).mock.calls.length
    await w.get('input[type="search"]').setValue('zeynep')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect(w.get('tbody th').text()).toContain('Zeynep Kaya')
    await w.get('input[type="search"]').setValue('d002')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect(vi.mocked(cizelgeApi.getir).mock.calls.length).toBe(cagri)
  })

  it('ay değişince yeni ay istenir', async () => {
    const { w } = await kur()
    await w.get('input[type="month"]').setValue('2026-09')
    await flushPromises()
    expect(cizelgeApi.getir).toHaveBeenLastCalledWith('2026-09', {
      tur: 'MAKINIST',
      garId: null,
      kontrol: false,
    })
  })

  it('yazdırma bağlantıları: A4 ve A3, ay + süzgeç sorguda', async () => {
    const { w } = await kur()
    const a4 = w.get('a[data-test="yazdir-A4"]')
    const a3 = w.get('a[data-test="yazdir-A3"]')
    expect(a4.attributes('href')).toMatch(/^\/yazdir\/cizelge\?ay=\d{4}-\d{2}&boyut=A4$/)
    expect(a3.attributes('href')).toMatch(/boyut=A3$/)
    await w.get('select#cizelge-gar').setValue('2')
    await flushPromises()
    expect(w.get('a[data-test="yazdir-A3"]').attributes('href')).toMatch(/boyut=A3&garId=2$/)
  })

  it('boş liste: VeriDurumu "boş"; hata: VeriDurumu "hata" (ızgara yok)', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit({ satirlar: [] }))
    const bosSayfa = await kur()
    expect(bosSayfa.w.find('table').exists()).toBe(false)
    expect(bosSayfa.w.text()).toContain('personel yok')
    vi.mocked(cizelgeApi.getir).mockRejectedValue(new ApiHata(500, 'X', 'Sunucu hatası'))
    const hataSayfa = await kur()
    expect(hataSayfa.w.find('table').exists()).toBe(false)
    expect(hataSayfa.w.text()).toContain('Sunucu hatası')
  })

  // 300 × 31 = 9.300 hücrenin tek seferde çizimi gerçek tarayıcıda ~1,1 sn sürdü (ilk çizim bütçesi 1 sn): sayfalama (50 satır) + süzgeç; ölçüm 04-gelistirme.md "Tur 4".
  describe('sayfalama (300 personel)', () => {
    const uc = () =>
      Array.from({ length: 300 }, (_, i) =>
        satir(i + 1, {
          sicil: `S${String(i + 1).padStart(3, '0')}`,
          adSoyad: `Personel ${i + 1}`,
          hucreler: [{ gun: 1 + (i % 31), izin: 'RAPOR' }],
        }),
      )

    it('ilk sayfa 50 satır × 31 gün; 6 sayfada 300 personelin tamamı, 9.300 hücre', async () => {
      vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit({ satirlar: uc() }))
      const { w } = await kur()
      expect(w.findAll('tbody tr')).toHaveLength(50)
      expect(w.findAll('td.hucre')).toHaveLength(50 * 31)
      expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('1–50 / 300 personel')
      expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('Sayfa 1 / 6')
      let hucre = 0
      let dolu = 0
      const sicilleri: string[] = []
      for (let s = 0; s < 6; s++) {
        hucre += w.findAll('td.hucre').length
        dolu += w.findAll('td.hucre').filter((h) => h.text() !== '').length
        sicilleri.push(...w.findAll('tbody th[scope="row"] small').map((t) => t.text().slice(0, 4)))
        if (s < 5) await w.get('[data-test="sonraki-sayfa"]').trigger('click')
      }
      expect(hucre).toBe(9300)
      expect(dolu).toBe(300)
      expect(new Set(sicilleri).size).toBe(300)
      expect(w.get('[data-test="sonraki-sayfa"]').attributes('disabled')).toBeDefined()
      expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('251–300 / 300 personel')
    })

    it('Önceki/Sonraki sınırlarda pasif; arama tüm listede süzer ve ilk sayfaya döner', async () => {
      vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit({ satirlar: uc() }))
      const { w } = await kur()
      expect(w.get('[data-test="onceki-sayfa"]').attributes('disabled')).toBeDefined()
      await w.get('[data-test="sonraki-sayfa"]').trigger('click')
      expect(w.get('[data-test="onceki-sayfa"]').attributes('disabled')).toBeUndefined()
      expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('51–100 / 300 personel')
      await w.get('input[type="search"]').setValue('personel 299')
      expect(w.findAll('tbody tr')).toHaveLength(1)
      expect(w.find('[data-test="sayfa-durumu"]').exists()).toBe(true)
      expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('1–1 / 1 personel')
      expect(w.find('[data-test="sonraki-sayfa"]').exists()).toBe(false) // tek sayfa: gezinme düğmesi yok
    })

    it('50 ve altı personelde sayfalama düğmesi yok', async () => {
      const { w } = await kur() // 3 satır
      expect(w.find('[data-test="sonraki-sayfa"]').exists()).toBe(false)
    })

    it('klavye: sayfa içi satır sınırında ArrowDown sayfa dışına çıkmaz', async () => {
      vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit({ satirlar: uc() }))
      const { w } = await kur()
      const son = w.findAll('tbody tr')[49]!.findAll('td.hucre')[0]!
      ;(son.element as HTMLElement).focus()
      await son.trigger('keydown', { key: 'ArrowDown' })
      expect(document.activeElement).toBe(son.element)
    })
  })
})
