import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun } from '@/lib/tarih'

vi.mock('./puantajApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./puantajApi')>()),
  puantajApi: { rapor: vi.fn(), excel: vi.fn() },
}))
vi.mock('@/features/makinist/makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/makinist/makinistApi')>()),
  makinistApi: { liste: vi.fn() },
}))

import PuantajRaporu from './PuantajRaporu.vue'
import { puantajApi, type PuantajSatiri, type PuantajYaniti } from './puantajApi'
import { makinistApi, type MakinistOzet } from '@/features/makinist/makinistApi'

const bos = { template: '<div />' }

const makinist = (id: number, sicil: string, adSoyad: string): MakinistOzet => ({
  id,
  sicil,
  adSoyad,
  bagliGarId: 1,
  bagliGar: 'Ankara',
  durum: 'AKTIF',
  ehliyetBitis: null,
  saglikBitis: null,
  psikoteknikBitis: null,
})

const satir = (ek: Partial<PuantajSatiri> = {}): PuantajSatiri => ({
  makinistId: 1,
  sicil: 'D001',
  adSoyad: 'Ali Veli',
  calisilanGun: 20,
  fiiliSaat: 160.5,
  ucretlendirilenSaat: 168,
  fazlaMesaiSaat: 12.25,
  geceSaat: 30,
  km: 4200,
  kmBilinmeyenSefer: 0,
  fazlaMesaiTl: 1500,
  geceTl: 750.5,
  kmTl: 2100,
  ...ek,
})

const yanit = (ek: Partial<PuantajYaniti> = {}): PuantajYaniti => ({
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  etiket: 'Puantaj ön hesabı — ücret bordrosu değildir',
  demo: false,
  saatUcretiGirildi: true,
  kmBirimGirildi: true,
  oranlar: {
    saatUcreti: 100,
    fazlaMesaiCarpani: 1.5,
    geceTazminatOrani: 0.25,
    kmBirimTutari: 0.5,
    asgariGunlukSaat: 8,
    normalHaftalikSaat: 45,
    gecePenceresi: '20:00–07:00',
  },
  satirlar: [satir()],
  ...ek,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rapor/puantaj', name: 'rapor-puantaj', component: bos },
      { path: '/yazdir/puantaj', name: 'yazdir-puantaj', component: bos },
    ],
  })
  await router.push('/rapor/puantaj')
  const w = mount(PuantajRaporu, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const getir = async (w: Sayfa) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('PuantajRaporu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(puantajApi.rapor).mockResolvedValue(yanit())
    vi.mocked(makinistApi.liste).mockResolvedValue([
      makinist(1, 'D001', 'Ali Veli'),
      makinist(2, 'D002', 'Veli Can'),
    ])
  })
  afterEach(() => vi.unstubAllGlobals())

  it('form: varsayılan bu ay, makinist seçicisi "Tüm makinistler" + sicil — ad; rapor gelmeden Excel/Yazdır yok', async () => {
    const w = await kur()
    expect((w.get('#puantaj-ay').element as HTMLInputElement).value).toBe(bugun().slice(0, 7))
    const secenekler = w.findAll('#puantaj-makinist option').map((o) => o.text())
    expect(secenekler).toEqual(['Tüm makinistler', 'D001 — Ali Veli', 'D002 — Veli Can'])
  })

  it('sayfa açılınca varsayılan süzgeçle (bu ay, tüm makinistler) rapor otomatik getirilir; düğme kalır', async () => {
    const w = await kur()
    expect(puantajApi.rapor).toHaveBeenCalledTimes(1)
    expect(puantajApi.rapor).toHaveBeenCalledWith({ ay: bugun().slice(0, 7), makinist: '' })
    expect(w.find('table').exists()).toBe(true)
    expect(w.text()).toContain('Excel indir')
    expect(w.text()).toContain('Raporu getir')
  })

  it('personel türü değişince eski türün raporu ekranda kalmaz', async () => {
    const w = await kur()
    expect(w.find('table').exists()).toBe(true)
    await w.get('#puantaj-tur').setValue('KONDUKTOR')
    expect(w.find('table').exists()).toBe(false)
    expect(w.text()).not.toContain('Excel indir')
  })

  it('otomatik getirme hatası gösterilir', async () => {
    vi.mocked(puantajApi.rapor).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Rapor alınamadı.'))
    const w = await kur()
    expect(w.get('[role="alert"]').text()).toBe('Rapor alınamadı.')
  })

  it('Raporu getir: ay ve makinistle çağırır; tablo sicil/ad/saat/km değerlerini virgüllü gösterir', async () => {
    const w = await kur()
    await w.get('#puantaj-ay').setValue('2026-10')
    await w.get('#puantaj-makinist').setValue('1')
    await getir(w)
    expect(puantajApi.rapor).toHaveBeenCalledWith({ ay: '2026-10', makinist: '1' })
    const tablo = w.get('table')
    expect(tablo.attributes('aria-label')).toBe('Puantaj ön hesabı')
    const h = tablo.findAll('th').map((t) => t.text())
    expect(h).toContain('Ücretlendirilen saat')
    expect(h).toContain('Km TL')
    const hucreler = tablo
      .findAll('tbody tr')[0]!
      .findAll('td')
      .map((t) => t.text())
    expect(hucreler.slice(0, 8)).toEqual([
      'D001',
      'Ali Veli',
      '20',
      '160,5',
      '168',
      '12,25',
      '30',
      '4200',
    ])
    const ozet = w.get('[data-test="ozet"]').text().replace(/\s+/g, ' ')
    expect(ozet).toContain('Üretim zamanı: 12.10.2026 10:20')
    expect(ozet).toContain('Üreten: Mehmet Demir')
  })

  it('TL doluyken para biçimi (1.500,00 TL), uyarı bandı ve "oran girilmedi" yok', async () => {
    const w = await kur()
    await getir(w)
    const tl = w
      .get('tbody tr')
      .findAll('td')
      .slice(8)
      .map((t) => t.text())
    expect(tl).toEqual(['1.500,00 TL', '750,50 TL', '2.100,00 TL'])
    expect(w.text()).not.toContain('oran girilmedi')
    expect(w.text()).not.toContain('Saat ücreti / km birim tutarı girilmemiş')
  })

  it('TL null ise hücrede "oran girilmedi" ve üstte uyarı bandı; oranlar özetinde "girilmedi"', async () => {
    vi.mocked(puantajApi.rapor).mockResolvedValue(
      yanit({
        saatUcretiGirildi: false,
        kmBirimGirildi: false,
        oranlar: {
          saatUcreti: null,
          fazlaMesaiCarpani: 1.5,
          geceTazminatOrani: 0.25,
          kmBirimTutari: null,
          asgariGunlukSaat: 8,
          normalHaftalikSaat: 45,
          gecePenceresi: '20:00–07:00',
        },
        satirlar: [satir({ fazlaMesaiTl: null, geceTl: null, kmTl: null })],
      }),
    )
    const w = await kur()
    await getir(w)
    expect(w.findAll('tbody td .soluk').map((t) => t.text())).toEqual([
      'oran girilmedi',
      'oran girilmedi',
      'oran girilmedi',
    ])
    expect(w.text()).toContain(
      'Saat ücreti / km birim tutarı girilmemiş: yalnız miktarlar (saat, km) gösteriliyor. Oranları Ayarlar ekranından girin.',
    )
    const oranlar = w.get('[data-test="oranlar"]').text().replace(/\s+/g, ' ')
    expect(oranlar).toContain('Saat ücreti:girilmedi')
    expect(oranlar).toContain('Km birim tutarı:girilmedi')
    expect(oranlar).toContain('Fazla mesai çarpanı:1,5')
    expect(oranlar).toContain('Asgari günlük süre:8 saat')
    expect(oranlar).toContain('Normal haftalık süre:45 saat')
    expect(oranlar).toContain('Gece penceresi:20:00–07:00')
    // Miktarlar yine görünür.
    expect(w.get('tbody tr').text()).toContain('160,5')
  })

  it('demo true: "DEMO – gerçek değil" bandı; demo false: yok', async () => {
    vi.mocked(puantajApi.rapor).mockResolvedValue(yanit({ demo: true }))
    const w = await kur()
    await getir(w)
    expect(w.text()).toContain('DEMO – gerçek değil')

    vi.mocked(puantajApi.rapor).mockResolvedValue(yanit({ demo: false }))
    await getir(w)
    expect(w.text()).not.toContain('DEMO')
  })

  it('her zaman "ücret bordrosu değildir" etiketini gösterir', async () => {
    const w = await kur()
    await getir(w)
    expect(w.text()).toContain('Puantaj ön hesabı — ücret bordrosu değildir')
  })

  it('km bilinmeyen sefer varsa dipnot gösterir', async () => {
    vi.mocked(puantajApi.rapor).mockResolvedValue(
      yanit({ satirlar: [satir({ kmBilinmeyenSefer: 2 })] }),
    )
    const w = await kur()
    await getir(w)
    expect(w.get('tbody tr').text()).toContain('4200 *')
    expect(w.get('.dipnot').text()).toContain('D001 (2 sefer)')
  })

  it('Yazdır: uygulanan süzgeçle yeni sekmede yazdır rotasına bağlanır', async () => {
    const w = await kur()
    await w.get('#puantaj-ay').setValue('2026-09')
    await w.get('#puantaj-makinist').setValue('2')
    await getir(w)
    const a = w.get('a[target="_blank"]')
    expect(a.text()).toBe('Yazdır')
    const href = a.attributes('href')!
    expect(href).toContain('/yazdir/puantaj?')
    expect(href).toContain('ay=2026-09')
    expect(href).toContain('makinist=2')
  })

  it('Excel indir: aynı süzgeçle indirir; dosya adı sunucudan', async () => {
    const tikla = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    })
    vi.mocked(puantajApi.excel).mockResolvedValue({
      blob: new Blob(['x']),
      ad: 'puantaj-2026-10.xlsx',
    })
    const w = await kur()
    await w.get('#puantaj-ay').setValue('2026-10')
    await getir(w)
    await w.get('#puantaj-ay').setValue('2026-01') // görünen raporun süzgeci geçerli
    await w
      .findAll('button')
      .find((b) => b.text() === 'Excel indir')!
      .trigger('click')
    await flushPromises()
    expect(puantajApi.excel).toHaveBeenCalledWith({ ay: '2026-10', makinist: '' })
    expect(tikla).toHaveBeenCalledTimes(1)
    expect((tikla.mock.contexts[0] as HTMLAnchorElement).download).toBe('puantaj-2026-10.xlsx')
    tikla.mockRestore()
  })

  it('Excel indirilemezse hata gösterilir', async () => {
    vi.mocked(puantajApi.excel).mockRejectedValue(
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
    expect(w.find('table').exists()).toBe(true)
    vi.mocked(puantajApi.rapor).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    await getir(w)
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(w.find('table').exists()).toBe(false)
  })
})
