import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: {
    izinListe: vi.fn(),
    izinTalep: vi.fn(),
    izinOnay: vi.fn(),
    izinRed: vi.fn(),
    yedek: vi.fn(),
  },
}))
vi.mock('@/features/makinist/makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/makinist/makinistApi')>()),
  makinistApi: { liste: vi.fn(), izinEtkisi: vi.fn() },
}))

import { makinistApi, type IzinEtkisiSatiri } from '@/features/makinist/makinistApi'
import IzinSayfasi from './IzinSayfasi.vue'
import { raporApi, type IzinKaydi, type YedekRaporu } from './raporApi'

const bos = { template: '<div />' }

const izin = (id: number, ek: Partial<IzinKaydi> = {}): IzinKaydi => ({
  id,
  makinistId: 1,
  sicil: 'D001',
  adSoyad: 'Ali Veli',
  baslangic: '2026-10-13',
  bitis: '2026-10-15',
  tur: 'YILLIK',
  durum: 'TALEP',
  olusturan: 'Ayşe Kaya',
  kararVeren: null,
  ...ek,
})

const etkiSatiri: IzinEtkisiSatiri = {
  atamaId: 7,
  seferId: 70,
  tarih: '2026-10-14',
  trenNo: 'T1001',
  rol: 'ASIL',
  kalkisGar: 'Ankara',
  varisGar: 'İstanbul',
  kalkis: '2026-10-14T05:30:00Z', // 08:30
  varis: '2026-10-14T10:00:00Z', // 13:00
}

const yedek = (gunler: YedekRaporu['gunler']): YedekRaporu => ({
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'X',
  aktifMakinist: 5,
  oran: 0.1,
  min: 1,
  eksikGunSayisi: 0,
  toplamAcik: 0,
  gunler,
})
const gun = (tarih: string, acik: number) => ({
  tarih,
  planliSefer: 1,
  ihtiyac: 1,
  mevcut: 1,
  acik,
  izinli: 0,
  gorevli: 1,
})

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/izin', name: 'izin', component: bos },
      { path: '/atama', name: 'atama', component: bos },
    ],
  })
  await router.push('/izin')
  const w = mount(IzinSayfasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const dugme = (w: Sayfa, ad: string) => w.findAll('button').find((b) => b.text() === ad)!

describe('IzinSayfasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(raporApi.izinListe).mockResolvedValue([izin(1)])
    vi.mocked(makinistApi.liste).mockResolvedValue([
      { id: 1, sicil: 'D001', adSoyad: 'Ali Veli' } as never,
      { id: 2, sicil: 'D002', adSoyad: 'Ayşe Kaya' } as never,
    ])
    vi.mocked(makinistApi.izinEtkisi).mockResolvedValue([])
    vi.mocked(raporApi.yedek).mockResolvedValue(yedek([]))
  })

  it('varsayılan süzgeç Talep; makinist seçici "sicil — ad"; tür seçenekleri Türkçe', async () => {
    const w = await kur()
    expect(raporApi.izinListe).toHaveBeenCalledWith('TALEP')
    const secenekler = w.findAll('#izin-makinist option').map((o) => o.text())
    expect(secenekler).toContain('D001 — Ali Veli')
    expect(w.findAll('#izin-tur option').map((o) => o.text())).toEqual(['Yıllık', 'Rapor', 'Diğer'])
    const pressed = w
      .findAll('[aria-label="Durum süzgeci"] button')
      .filter((b) => b.attributes('aria-pressed') === 'true')
    expect(pressed.map((b) => b.text())).toEqual(['Talep'])
  })

  it('süzgeç değişince listeyi yeniden çeker; Hepsi durum göndermez', async () => {
    const w = await kur()
    await dugme(w, 'Hepsi').trigger('click')
    await flushPromises()
    expect(raporApi.izinListe).toHaveBeenLastCalledWith(undefined)
    await dugme(w, 'Onaylı').trigger('click')
    await flushPromises()
    expect(raporApi.izinListe).toHaveBeenLastCalledWith('ONAY')
  })

  it('liste: tarih gg.aa.yyyy, Türkçe tür/durum; yalnız TALEP satırında Onayla/Reddet', async () => {
    vi.mocked(raporApi.izinListe).mockResolvedValue([
      izin(1),
      izin(2, { durum: 'ONAY', tur: 'RAPOR', kararVeren: 'Mehmet' }),
    ])
    const w = await kur()
    const satirlar = w.findAll('tbody tr')
    expect(satirlar[0]!.text()).toContain('13.10.2026')
    expect(satirlar[0]!.text()).toContain('15.10.2026')
    expect(satirlar[0]!.text()).toContain('Talep')
    expect(satirlar[0]!.findAll('button')).toHaveLength(2)
    expect(satirlar[1]!.text()).toContain('Rapor')
    expect(satirlar[1]!.text()).toContain('Onaylı')
    expect(satirlar[1]!.text()).toContain('Mehmet')
    expect(satirlar[1]!.findAll('button')).toHaveLength(0)
  })

  it('liste boşsa "İzin kaydı yok."', async () => {
    vi.mocked(raporApi.izinListe).mockResolvedValue([])
    expect((await kur()).text()).toContain('İzin kaydı yok.')
  })

  it('talep et: gövdeyle çağırır, listeyi yeniler, tarihleri temizler', async () => {
    vi.mocked(raporApi.izinTalep).mockResolvedValue(izin(5))
    const w = await kur()
    await w.get('#izin-makinist').setValue(2)
    await w.get('#izin-bas').setValue('2026-11-02')
    await w.get('#izin-bit').setValue('2026-11-04')
    await w.get('#izin-tur').setValue('RAPOR')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(raporApi.izinTalep).toHaveBeenCalledWith({
      makinistId: 2,
      baslangic: '2026-11-02',
      bitis: '2026-11-04',
      tur: 'RAPOR',
    })
    expect(raporApi.izinListe).toHaveBeenCalledTimes(2)
    expect((w.get('#izin-bas').element as HTMLInputElement).value).toBe('')
  })

  it('talep: bitiş başlangıçtan önceyse istek atılmaz; sunucu alan hatası gösterilir', async () => {
    const w = await kur()
    await w.get('#izin-makinist').setValue(1)
    await w.get('#izin-bas').setValue('2026-11-05')
    await w.get('#izin-bit').setValue('2026-11-01')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(raporApi.izinTalep).not.toHaveBeenCalled()
    expect(w.text()).toContain('Bitiş tarihi başlangıçtan önce olamaz.')

    await w.get('#izin-bit').setValue('2026-11-06')
    vi.mocked(raporApi.izinTalep).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Geçersiz izin.', { alanlar: { bitis: 'Çakışan izin var.' } }),
    )
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('.alan-hata').text()).toBe('Çakışan izin var.')
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz izin.')
  })

  it('Onayla: onay çağrılır, liste yenilenir; etki paneli atanmış seferleri ve Atama Masası bağlantısını gösterir', async () => {
    vi.mocked(raporApi.izinOnay).mockResolvedValue(izin(1, { durum: 'ONAY' }))
    vi.mocked(makinistApi.izinEtkisi).mockResolvedValue([etkiSatiri])
    vi.mocked(raporApi.yedek).mockResolvedValue(yedek([gun('2026-10-14', 2), gun('2026-10-20', 4)]))
    const w = await kur()
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(raporApi.izinOnay).toHaveBeenCalledWith(1)
    expect(raporApi.izinListe).toHaveBeenCalledTimes(2)
    expect(makinistApi.izinEtkisi).toHaveBeenCalledWith(1, '2026-10-13', '2026-10-15')
    expect(raporApi.yedek).toHaveBeenCalledWith('2026-10')
    const panel = w.get('[data-test="etki"]')
    expect(panel.text()).toContain('13.10.2026 – 15.10.2026 izni onaylandı')
    expect(w.get('[data-test="sefer-etkisi"]').text().replace(/\s+/g, ' ')).toContain(
      "⚠ Bu izin 1 atanmış seferi kırmızı yaptı → Atama Masası'ndan düzeltin",
    )
    const a = panel.get('a')
    expect(a.attributes('href')).toBe('/atama?tarih=2026-10-14')
    expect(panel.text()).toContain('T1001')
    expect(panel.text()).toContain('Ankara → İstanbul')
    expect(panel.text()).toContain('Asıl')
    // yalnız izin aralığındaki açık gün
    const yedekMetni = w.get('[data-test="yedek-acigi"]').element.parentElement!.textContent!
    expect(yedekMetni).toContain('14.10.2026 — 2 eksik')
    expect(yedekMetni).not.toContain('20.10.2026')
  })

  it('etki yoksa "atanmış sefer yok" ve "Yedek açığı yok."; iki aya yayılan izinde iki ay sorgulanır', async () => {
    vi.mocked(raporApi.izinListe).mockResolvedValue([
      izin(1, { baslangic: '2026-10-30', bitis: '2026-11-02' }),
    ])
    vi.mocked(raporApi.izinOnay).mockResolvedValue(
      izin(1, { durum: 'ONAY', baslangic: '2026-10-30', bitis: '2026-11-02' }),
    )
    const w = await kur()
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(raporApi.yedek).toHaveBeenCalledWith('2026-10')
    expect(raporApi.yedek).toHaveBeenCalledWith('2026-11')
    expect(w.find('[data-test="sefer-yok"]').exists()).toBe(true)
    expect(w.get('[data-test="yedek-yok"]').text()).toBe('Yedek açığı yok.')
  })

  it('etki sorguları başarısız olsa da onay kalır; hata metni panelde görünür', async () => {
    vi.mocked(raporApi.izinOnay).mockResolvedValue(izin(1, { durum: 'ONAY' }))
    vi.mocked(makinistApi.izinEtkisi).mockRejectedValue(
      new ApiHata(500, 'SUNUCU', 'Etki hesaplanamadı.'),
    )
    const w = await kur()
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="etki"]').text()).toContain('Etki hesaplanamadı.')
    expect(w.find('[data-test="liste-hata"]').exists()).toBe(false)
  })

  it('Reddet: red çağrılır, etki paneli açılmaz', async () => {
    vi.mocked(raporApi.izinRed).mockResolvedValue(izin(1, { durum: 'RED' }))
    const w = await kur()
    await dugme(w, 'Reddet').trigger('click')
    await flushPromises()
    expect(raporApi.izinRed).toHaveBeenCalledWith(1)
    expect(raporApi.izinOnay).not.toHaveBeenCalled()
    expect(w.find('[data-test="etki"]').exists()).toBe(false)
    expect(raporApi.izinListe).toHaveBeenCalledTimes(2)
  })

  it('409 IZIN_KARARLI: sunucu metni gösterilir ve liste yenilenir', async () => {
    vi.mocked(raporApi.izinOnay).mockRejectedValue(
      new ApiHata(409, 'IZIN_KARARLI', 'Bu izin için karar zaten verilmiş.'),
    )
    const w = await kur()
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="liste-hata"]').text()).toBe('Bu izin için karar zaten verilmiş.')
    expect(raporApi.izinListe).toHaveBeenCalledTimes(2)
    expect(w.find('[data-test="etki"]').exists()).toBe(false)
  })
})
