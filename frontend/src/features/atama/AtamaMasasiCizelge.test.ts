/** B1 zaman çizelgesi (AK-9.1..9.5, MK-159): aynı tablo + "Zaman" sütunu; Liste varsayılan; yazma yok. */
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { gun: vi.fn(), adaylar: vi.fn(), kirmiziSayisi: vi.fn(), gordumOzeti: vi.fn() },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi } from './atamaApi'
import { geceAraligi } from './gece'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const ANAHTAR = 'atama.masa.gorunum'
const Z = (sa: number, dk = 0, g = 0) =>
  new Date(
    Date.parse('2026-10-12T00:00:00+03:00') + ((g * 24 + sa) * 60 + dk) * 60_000,
  ).toISOString()

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return w
}

const gun = () => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:00:00Z',
  satirlar: [
    satir(1, {
      trenNo: 'AE-001',
      guncelKalkis: Z(6),
      guncelVaris: Z(8),
      makinistler: [makinist()],
    }),
    satir(2, { trenNo: 'AE-002', guncelKalkis: Z(7), guncelVaris: Z(9) }), // BOŞ
    satir(3, {
      trenNo: 'AS-003',
      guncelKalkis: Z(9),
      guncelVaris: Z(11),
      kirmizi: true,
      makinistler: [makinist({ atamaId: 3, makinistId: 3, kirmizi: true, kirmiziNedenler: ['x'] })],
    }),
    satir(4, { trenNo: 'AK-004', guncelKalkis: Z(10), guncelVaris: Z(12), durum: 'IPTAL' }),
    satir(5, {
      trenNo: 'AS-083',
      guncelKalkis: Z(21, 22),
      guncelVaris: Z(2, 47, 1), // gece yarısını geçer
      makinistler: [makinist({ atamaId: 5, makinistId: 5 })],
    }),
  ],
})

const cubuklar = (w: Awaited<ReturnType<typeof kur>>) => w.findAll('[data-test=cubuk]')
const anahtar = (w: Awaited<ReturnType<typeof kur>>, ad: string) =>
  w.findAll('[aria-label="Görünüm"] button').find((b) => b.text() === ad)!

describe('AtamaMasasi: zaman çizelgesi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    try {
      localStorage.clear()
    } catch {
      /* yok */
    }
    geceAraligi.value = { baslangicDk: 20 * 60, bitisDk: 6 * 60 }
    vi.mocked(atamaApi.gun).mockResolvedValue(gun())
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 1,
      kirmiziAtama: 1,
    })
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      toplam: 0,
      gorulen: 0,
      gorulmeyen: 0,
      hesapsiz: 0,
      uyari: 0,
    })
  })
  afterEach(() => vi.useRealTimers())

  it("AK-9.1: varsayılan Liste; anahtar aria-pressed; Liste DOM'unda çubuk ve Zaman sütunu yok", async () => {
    const w = await kur()
    expect(anahtar(w, 'Liste').attributes('aria-pressed')).toBe('true')
    expect(anahtar(w, 'Çizelge').attributes('aria-pressed')).toBe('false')
    expect(cubuklar(w)).toHaveLength(0)
    expect(w.findAll('thead th').map((t) => t.text())).toEqual([
      'Saat',
      'Tren no',
      'Güzergâh',
      'Araç · Hat',
      'Personel',
      'Durum',
      'İşlem',
    ])
  })

  it('AK-9.1: Çizelge\'ye geçince her satırda bir çubuk ve "Zaman" sütunu; satır sayısı aynı', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    expect(anahtar(w, 'Çizelge').attributes('aria-pressed')).toBe('true')
    expect(anahtar(w, 'Liste').attributes('aria-pressed')).toBe('false')
    expect(w.findAll('tr.sefer-satiri')).toHaveLength(5)
    expect(cubuklar(w)).toHaveLength(5)
    expect(w.findAll('thead th').some((t) => t.text().startsWith('Zaman'))).toBe(true)
    await anahtar(w, 'Liste').trigger('click')
    expect(cubuklar(w)).toHaveLength(0)
  })

  it('AK-9.1: seçim hatırlanır; yeniden açılınca Çizelge', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    expect(localStorage.getItem(ANAHTAR)).toBe('cizelge')
    w.unmount()
    const w2 = await kur()
    expect(anahtar(w2, 'Çizelge').attributes('aria-pressed')).toBe('true')
    expect(cubuklar(w2)).toHaveLength(5)
  })

  it('AK-9.1: localStorage erişilemezse (okuma/yazma hata fırlatır) masa Liste ile çalışır, anahtar yine çalışır', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('engelli')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('engelli')
    })
    const w = await kur()
    expect(w.findAll('tr.sefer-satiri')).toHaveLength(5)
    await anahtar(w, 'Çizelge').trigger('click')
    expect(cubuklar(w)).toHaveLength(5)
    vi.restoreAllMocks()
  })

  it('AK-9.2: çubuk durum sınıfları: atanmış / boş / kırmızı / iptal', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const sinif = (i: number) => cubuklar(w)[i]!.classes()
    expect(sinif(0)).toContain('cubuk-atanmis')
    expect(sinif(1)).toContain('cubuk-bos')
    expect(sinif(2)).toContain('cubuk-kirmizi')
    expect(sinif(3)).toContain('cubuk-iptal')
  })

  it('AK-9.4: her çubuğun erişilebilir adı sefer + güzergâh + saat + durum (renk tek başına değil)', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const ad = (i: number) => cubuklar(w)[i]!.attributes('aria-label')!
    expect(cubuklar(w)[0]!.attributes('role')).toBe('img')
    expect(ad(0)).toContain('AE-001')
    expect(ad(0)).toContain('Ankara – Eskişehir')
    expect(ad(0)).toContain('06:00–08:00')
    expect(ad(0)).toContain('atanmış')
    expect(ad(1)).toContain('boş')
    expect(ad(2)).toContain('kontrol bekliyor')
    expect(ad(3)).toContain('iptal')
  })

  it('AK-9.3: gece yarısını geçen sefer kırpılır ve belli edilir (kırpık sınıfı, ad "ertesi güne sürüyor")', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const gece = cubuklar(w)[4]!
    expect(gece.classes()).toContain('cubuk-sag-kirpik')
    expect(gece.attributes('aria-label')).toContain('21:22–02:47')
    expect(gece.attributes('aria-label')).toContain('ertesi güne sürüyor')
    expect(cubuklar(w)[0]!.classes()).not.toContain('cubuk-sag-kirpik')
    const stil = gece.attributes('style')!
    expect(stil).toContain('left')
    expect(
      parseFloat(/left:\s*([\d.]+)%/.exec(stil)![1]!) +
        parseFloat(/width:\s*([\d.]+)%/.exec(stil)![1]!),
    ).toBeCloseTo(100, 3)
  })

  it('AK-9.2: gece bantları geceAraligi (ayar) değişince değişir; sabit saat yok', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const konum = () =>
      w
        .findAll('tr.sefer-satiri')[0]!
        .findAll('[data-test=gece-bandi]')
        .map((b) => b.attributes('style'))
    const once = konum()
    expect(once.length).toBeGreaterThan(0)
    geceAraligi.value = { baslangicDk: 8 * 60, bitisDk: 10 * 60 }
    await flushPromises()
    const sonra = konum()
    expect(sonra).not.toEqual(once)
    geceAraligi.value = { baslangicDk: 1 * 60, bitisDk: 2 * 60 } // pencerenin (06:00-24:00) dışında
    await flushPromises()
    expect(konum()).toHaveLength(0)
  })

  it('AK-9.2: "şimdi" çizgisi yalnız seçili gün bugünse', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(Z(10)))
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    expect(w.findAll('[data-test=simdi-cizgisi]').length).toBeGreaterThan(0)
    w.unmount()
    vi.setSystemTime(new Date(Z(10, 0, 3)))
    const w2 = await kur()
    await anahtar(w2, 'Çizelge').trigger('click')
    expect(w2.findAll('[data-test=simdi-cizgisi]')).toHaveLength(0)
  })

  it('seçili sefer ve aday paneli iki görünümde aynı çalışır; ↓/↑ satırlar arasında gezdirir', async () => {
    const w = await kur()
    for (const [ad, i] of [
      ['Liste', 1],
      ['Çizelge', 3],
    ] as const) {
      await anahtar(w, ad).trigger('click')
      vi.mocked(atamaApi.adaylar).mockClear()
      const satirlar = w.findAll('tr.sefer-satiri')
      await satirlar[i]!.trigger('click')
      await flushPromises()
      expect(atamaApi.adaylar).toHaveBeenCalledTimes(1)
      expect(vi.mocked(atamaApi.adaylar).mock.calls[0]![0]).toBe(i + 1)
      expect(w.findAll('tr.sefer-satiri')[i]!.attributes('aria-selected')).toBe('true')
      expect(w.get('aside h2').text()).toContain(`00${i + 1}`)
      ;(satirlar[i]!.element as HTMLElement).focus()
      await satirlar[i]!.trigger('keydown', { key: 'ArrowDown' })
      expect(document.activeElement).toBe(satirlar[i + 1]!.element)
      await satirlar[i + 1]!.trigger('keydown', { key: 'ArrowUp' })
      expect(document.activeElement).toBe(satirlar[i]!.element)
    }
  })

  it('çubuk tıklanabilir yüzey değil: satıra tık seçimi sürdürür (çubuğa tık da satırı seçer)', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    await cubuklar(w)[2]!.trigger('click')
    await flushPromises()
    expect(w.findAll('tr.sefer-satiri')[2]!.attributes('aria-selected')).toBe('true')
  })

  it('yazdırmada liste kullanılır: Zaman sütunu gizlenir, Güzergâh / Araç·Hat geri gelir (kaynak sözleşmesi)', () => {
    const kaynak = readFileSync(join(process.cwd(), 'src/features/atama/AtamaMasasi.vue'), 'utf8')
    const yazdir = kaynak.slice(kaynak.indexOf('@media print'))
    expect(yazdir).toMatch(/\.kol-zaman[^}]*display:\s*none/)
    expect(yazdir).toMatch(/masa-tablo--cizelge \.kol-guzergah[^}]*display:\s*table-cell/)
    expect(yazdir).toMatch(/masa-tablo--cizelge \.kol-saat[^}]*display:\s*table-cell/) // OK-42c: Saat geri gelir
  })

  it("OK-42a: Çizelge'de ihlal / uyarı metni tek satır (CSS) ve tam metin title'da; Liste DOM'unda title yok", async () => {
    const g = gun()
    g.satirlar[2]!.makinistler = [
      makinist({
        atamaId: 3,
        makinistId: 3,
        kirmizi: true,
        kirmiziNedenler: ['Çakışma: başka görev', 'Hat yetkisi yok'],
        uyarilar: ['Konum uyuşmazlığı: Sivas ≠ Ankara'],
      }),
    ]
    vi.mocked(atamaApi.gun).mockResolvedValue(g)
    const w = await kur()
    expect(w.get('.kirmizi-neden').attributes('title')).toBeUndefined() // Liste
    expect(w.findAll('.gorevli-ad')[0]!.attributes('title')).toBeUndefined() // Liste
    await anahtar(w, 'Çizelge').trigger('click')
    expect(w.findAll('.gorevli-ad')[0]!.attributes('title')).toBe(
      w.findAll('.gorevli-ad')[0]!.text(),
    ) // tam ad (hücre kesilir)
    expect(w.get('.kirmizi-neden').attributes('title')).toBe(
      'Çakışma: başka görev; Hat yetkisi yok',
    )
    expect(w.get('.uyari-notu').attributes('title')).toBe(
      'Uyarı: Konum uyuşmazlığı: Sivas ≠ Ankara',
    )
    const kaynak = readFileSync(join(process.cwd(), 'src/features/atama/AtamaMasasi.vue'), 'utf8')
    expect(kaynak).toMatch(/masa-tablo--cizelge \.kirmizi-neden[^}]*white-space:\s*nowrap/)
    expect(kaynak).toMatch(/masa-tablo--cizelge \.uyari-notu[^}]*text-overflow:\s*ellipsis/)
  })

  it("OK-42c: Çizelge'de İşlem düğmeleri simge + erişilebilir ad (metin DOM'da kalır, görünmez); Durum rozeti metni title'da", async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const ata = w.get('button[aria-label="AE-002 seferine ata"]')
    expect(ata.get('.btn-metin').text()).toBe('Ata')
    const kontrol = w.findAll('.kol-durum .rozet')[0]!
    expect(kontrol.attributes('title')).toBe('KONTROL')
    expect(kontrol.get('.rozet-metin').text()).toBe('KONTROL')
    const kaynak = readFileSync(join(process.cwd(), 'src/features/atama/AtamaMasasi.vue'), 'utf8')
    expect(kaynak).toMatch(/masa-tablo--cizelge \.kol-islem \.btn-metin[^}]*clip-path/)
    expect(kaynak).toMatch(/masa-tablo--cizelge \.kol-durum :deep\(\.rozet-metin\)[^}]*clip-path/)
  })

  it('OK-42: çubuğun güzergâh yazısı çubuğun yanında (içinde değil), kırpılmadan okunur; erişilebilir ad çubukta kalır', async () => {
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    const serit = w.findAll('.zaman-seridi')[0]!
    expect(serit.find('[data-test=cubuk] .cubuk-yazi').exists()).toBe(false)
    expect(serit.get('.cubuk-yazi').text()).toBe('Ankara – Eskişehir')
    expect(serit.get('.cubuk-yazi').attributes('aria-hidden')).toBe('true')
    expect(serit.get('[data-test=cubuk]').attributes('aria-label')).toContain('Ankara – Eskişehir')
  })

  it('OK-42: çubuk pencerenin çoğunu kaplıyorsa (iki yanda yer yok) yazı çubuğun içinde kalır', async () => {
    const g = gun()
    g.satirlar = [
      satir(1, { trenNo: 'AS-001', guncelKalkis: Z(6), guncelVaris: Z(11, 30) }), // pencere 06–12
    ]
    vi.mocked(atamaApi.gun).mockResolvedValue(g)
    const w = await kur()
    await anahtar(w, 'Çizelge').trigger('click')
    expect(w.find('[data-test=cubuk] .cubuk-yazi').exists()).toBe(true)
    expect(w.findAll('.cubuk-yazi-dis')).toHaveLength(0)
  })

  const kaynakOku = (ad: string) =>
    readFileSync(join(process.cwd(), 'src/features/atama', ad), 'utf8')

  it('D1/QA-1: çubuklar kendi yığın bağlamında (isolation) kalır: sabit başlığın (z-index 2) üstüne boyanmaz', () => {
    expect(kaynakOku('ZamanSeridi.vue')).toMatch(/[.]zaman-seridi [{][^}]*isolation: isolate/)
    expect(kaynakOku('AtamaMasasi.vue')).toMatch(
      /[.]masa-tablo th [{][^}]*position: sticky[^}]*z-index: 2/,
    )
  })

  it("D1/QA-3 (OK-42c): Çizelge'de personel adı kalan yeri alıp kesilir, kaldır düğmesi adın yanında kalır; notlar kendi satırında", () => {
    const k = kaynakOku('AtamaMasasi.vue')
    expect(k).toMatch(/masa-tablo--cizelge [.]gorevli-ad [{][^}]*flex: 1 1 0/)
    expect(k).toMatch(/masa-tablo--cizelge [.]kirmizi-neden,[^}]*flex: 1 1 100%/)
  })
})
