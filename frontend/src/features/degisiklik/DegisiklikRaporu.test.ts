import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { bugun, gunKaydir } from '@/lib/tarih'

vi.mock('./degisiklikApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./degisiklikApi')>()),
  degisiklikApi: { rapor: vi.fn(), excel: vi.fn() },
}))

import DegisiklikRaporu from './DegisiklikRaporu.vue'
import { degisiklikApi, type DegisiklikSatiri, type DegisiklikYaniti } from './degisiklikApi'

const bos = { template: '<div />' }

const satir = (id: number, ek: Partial<DegisiklikSatiri> = {}): DegisiklikSatiri => ({
  id,
  zaman: '2026-10-12T07:15:00Z', // İstanbul 10:15
  kim: 'Ayşe Kaya',
  tur: 'ATAMA',
  turAdi: 'Atama',
  sicil: 'D001',
  trenNo: 'T1001',
  seferTarihi: '2026-10-13',
  eski: null,
  yeni: 'Ali Veli (D001)',
  neden: null,
  nedenNotu: null,
  uyarili: false,
  uyariGerekcesi: null,
  uyarilar: [],
  otomatik: false,
  ...ek,
})

const yanit: DegisiklikYaniti = {
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  filtre: { bas: '2026-10-06', bit: '2026-10-12', trenNo: 'T1001', tur: 'ATAMA' },
  kayitSayisi: 2,
  bolumler: [
    {
      kod: 'ATAMA',
      ad: 'Atamalar',
      sayi: 2,
      satirlar: [
        satir(1, { otomatik: true }),
        satir(2, {
          tur: 'ATAMA_DEGISTIR',
          turAdi: 'Atama değiştirildi',
          eski: 'Eski Kişi (D009)',
          yeni: 'Ali Veli (D001)',
          neden: 'Belge sorunu',
          nedenNotu: 'Ehliyet süresi dolmuş',
          uyarili: true,
          uyariGerekcesi: 'Günlük 9 s > 8 s; amir onayı',
          uyarilar: ['Ö-001: Tek sefer süresi: 5 s 35 dk > 5 s 30 dk (Kural: Dizel hatlar)'],
        }),
      ],
    },
    { kod: 'SEFER_IPTAL', ad: 'Sefer iptalleri', sayi: 0, satirlar: [] },
  ],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/degisiklik', name: 'degisiklik', component: bos },
      { path: '/yazdir/degisiklik', name: 'yazdir-degisiklik', component: bos },
    ],
  })
  await router.push('/degisiklik')
  const w = mount(DegisiklikRaporu, { global: { plugins: [router] } })
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const getir = async (w: Sayfa) => {
  await w.get('form').trigger('submit')
  await flushPromises()
}

describe('DegisiklikRaporu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(degisiklikApi.rapor).mockResolvedValue(yanit)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('süzgeç formu: varsayılan son 7 gün, Türkçe tür seçici; rapor gelmeden Excel/Yazdır yok', async () => {
    const w = await kur()
    const input = (id: string) => (w.get(id).element as HTMLInputElement).value
    expect(input('#rapor-bit')).toBe(bugun())
    expect(input('#rapor-bas')).toBe(gunKaydir(bugun(), -6))
    const turler = w.findAll('#rapor-tur option').map((o) => o.text())
    expect(turler[0]).toBe('Tümü')
    expect(turler).toContain('Atama değiştirildi')
    expect(turler).toContain('Otomatik atama')
    expect(turler).toContain('İntikal iptal edildi')
    expect(turler).toHaveLength(24)
    expect(degisiklikApi.rapor).not.toHaveBeenCalled()
    expect(w.text()).not.toContain('Excel indir')
    expect(w.find('a[target="_blank"]').exists()).toBe(false)
  })

  it('Raporu getir: süzgeçle çağırır; üretim zamanı, üreten, süzgeç özeti ve kayıt sayısını gösterir', async () => {
    const w = await kur()
    await w.get('#rapor-bas').setValue('2026-10-06')
    await w.get('#rapor-bit').setValue('2026-10-12')
    await w.get('#rapor-tren').setValue(' T1001 ')
    await w.get('#rapor-kullanici').setValue('ayse')
    await w.get('#rapor-tur').setValue('ATAMA')
    await getir(w)
    expect(degisiklikApi.rapor).toHaveBeenCalledWith({
      bas: '2026-10-06',
      bit: '2026-10-12',
      trenNo: ' T1001 ',
      kullanici: 'ayse',
      tur: 'ATAMA',
    })
    const ozet = w.get('[data-test="ozet"]').text().replace(/\s+/g, ' ')
    expect(ozet).toContain('Üretim zamanı: 12.10.2026 10:20')
    expect(ozet).toContain('Üreten: Mehmet Demir')
    expect(ozet).toContain('Tarih: 06.10.2026 – 12.10.2026 · Tren no: T1001 · Tür: Atama')
    expect(ozet).toContain('Kayıt sayısı: 2')
  })

  it('bölümler: başlık + sayı + tablo; satırda zaman, kim, tür, sicil, tren no, eski → yeni, neden ve not', async () => {
    const w = await kur()
    await getir(w)
    const bolumler = w.findAll('section.bolum')
    expect(bolumler).toHaveLength(2)
    expect(bolumler[0]!.get('h2').text()).toBe('Atamalar (2)')
    const satirlar = bolumler[0]!.findAll('tbody tr')
    expect(satirlar).toHaveLength(2)
    expect(satirlar[0]!.text()).toContain('12.10.2026 10:15')
    expect(satirlar[0]!.text()).toContain('Ayşe Kaya')
    expect(satirlar[0]!.text()).toContain('Atama')
    expect(satirlar[0]!.text()).toContain('D001')
    expect(satirlar[0]!.text()).toContain('T1001')
    expect(satirlar[0]!.text()).toContain('— → Ali Veli (D001)')
    expect(satirlar[0]!.text()).not.toContain('Uyarılı')
    expect(satirlar[0]!.text()).toContain('(otomatik)') // otomatik atama satırı ayırt edilir
    expect(satirlar[1]!.text()).not.toContain('(otomatik)')
    expect(satirlar[1]!.text()).toContain('Eski Kişi (D009) → Ali Veli (D001)')
    expect(satirlar[1]!.text()).toContain('Belge sorunu')
    expect(satirlar[1]!.text()).toContain('Ehliyet süresi dolmuş')
    expect(satirlar[1]!.text()).toContain('⚠ Uyarılı: Günlük 9 s > 8 s; amir onayı')
  })

  it('uyarılı satırda uyaran kurallar madde madde listelenir; uyarısı olmayan satırda liste yok', async () => {
    const w = await kur()
    await getir(w)
    const satirlar = w.findAll('section.bolum')[0]!.findAll('tbody tr')
    const maddeler = satirlar[1]!.findAll('.uyarili li')
    expect(maddeler.map((m) => m.text())).toEqual([
      'Uyaran kural: Ö-001: Tek sefer süresi: 5 s 35 dk > 5 s 30 dk (Kural: Dizel hatlar)',
    ])
    expect(satirlar[0]!.find('li').exists()).toBe(false)
  })

  it('KURAL_EKLE satırında eski boşsa yalnız yeni metin gösterilir (ok yok); güncellemede eski → yeni kalır', async () => {
    vi.mocked(degisiklikApi.rapor).mockResolvedValue({
      ...yanit,
      bolumler: [
        {
          kod: 'KURAL',
          ad: 'Kurallar',
          sayi: 2,
          satirlar: [
            satir(10, {
              tur: 'KURAL_EKLE',
              turAdi: 'Kural eklendi',
              eski: null,
              yeni: 'Tek sefer süresi (sefer) en fazla 5,5 s',
            }),
            satir(11, {
              tur: 'KURAL_GUNCELLE',
              turAdi: 'Kural güncellendi',
              eski: 'en fazla 5 s',
              yeni: 'en fazla 5,5 s',
            }),
          ],
        },
      ],
    })
    const w = await kur()
    await getir(w)
    const satirlar = w.findAll('section.bolum')[0]!.findAll('tbody tr')
    expect(satirlar[0]!.text()).toContain('Tek sefer süresi (sefer) en fazla 5,5 s')
    expect(satirlar[0]!.text()).not.toContain('→')
    expect(satirlar[1]!.text()).toContain('en fazla 5 s → en fazla 5,5 s')
  })

  it('UX-12: sunucunun ham kodları okunur gösterilir (ASIL:D058, GECIKME)', async () => {
    vi.mocked(degisiklikApi.rapor).mockResolvedValue({
      ...yanit,
      bolumler: [
        {
          kod: 'ATAMA',
          ad: 'Atamalar',
          sayi: 2,
          satirlar: [
            satir(3, { eski: null, yeni: 'ASIL:D058' }),
            satir(4, { eski: 'YARDIMCI:D001', yeni: 'ASIL:D058', neden: 'GECIKME' }),
          ],
        },
      ],
    })
    const w = await kur()
    await getir(w)
    const satirlar = w.findAll('tbody tr')
    expect(satirlar[0]!.text()).toContain('— → D058 (asıl)')
    expect(satirlar[1]!.text()).toContain('D001 (yardımcı) → D058 (asıl)')
    expect(satirlar[1]!.text()).toContain('Gecikme')
    expect(w.text()).not.toMatch(/ASIL:|GECIKME/)
  })

  it('boş bölüm: başlık ve "Kayıt yok."', async () => {
    const w = await kur()
    await getir(w)
    const bolum = w.findAll('section.bolum')[1]!
    expect(bolum.get('h2').text()).toBe('Sefer iptalleri (0)')
    expect(bolum.text()).toContain('Kayıt yok.')
    expect(bolum.find('table').exists()).toBe(false)
  })

  it('Yazdır: uygulanan süzgeçle yeni sekmede yazdır rotasına bağlanır', async () => {
    const w = await kur()
    await w.get('#rapor-bas').setValue('2026-10-06')
    await w.get('#rapor-bit').setValue('2026-10-12')
    await w.get('#rapor-tur').setValue('ATAMA')
    await getir(w)
    const a = w.get('a[target="_blank"]')
    expect(a.text()).toBe('Yazdır')
    const href = a.attributes('href')!
    expect(href).toContain('/yazdir/degisiklik?')
    expect(href).toContain('bas=2026-10-06')
    expect(href).toContain('bit=2026-10-12')
    expect(href).toContain('tur=ATAMA')
    expect(href).not.toContain('trenNo')
  })

  it('Excel indir: aynı süzgeçle indirir; dosya adı sunucudan', async () => {
    const tikla = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    })
    vi.mocked(degisiklikApi.excel).mockResolvedValue({
      blob: new Blob(['x']),
      ad: 'denetim-raporu.xlsx',
    })
    const w = await kur()
    await w.get('#rapor-bas').setValue('2026-10-06')
    await w.get('#rapor-bit').setValue('2026-10-12')
    await getir(w)
    // Form sonradan değişse de indirilen, görünen raporun süzgecidir.
    await w.get('#rapor-bas').setValue('2026-01-01')
    const excel = w.findAll('button').find((b) => b.text() === 'Excel indir')!
    await excel.trigger('click')
    await flushPromises()
    expect(degisiklikApi.excel).toHaveBeenCalledWith(
      expect.objectContaining({ bas: '2026-10-06', bit: '2026-10-12' }),
    )
    expect(tikla).toHaveBeenCalledTimes(1)
    expect((tikla.mock.contexts[0] as HTMLAnchorElement).download).toBe('denetim-raporu.xlsx')
    tikla.mockRestore()
  })

  it('Excel indirilemezse hata gösterilir', async () => {
    vi.mocked(degisiklikApi.excel).mockRejectedValue(
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

  it('bitiş başlangıçtan önceyse istek atılmaz ve hata gösterilir', async () => {
    const w = await kur()
    await w.get('#rapor-bas').setValue('2026-10-12')
    await w.get('#rapor-bit').setValue('2026-10-01')
    await getir(w)
    expect(degisiklikApi.rapor).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toContain('Bitiş tarihi')
  })

  it('sunucu hatası (aralık > 366 gün) gösterilir ve eski rapor kalkar', async () => {
    const w = await kur()
    await getir(w)
    expect(w.findAll('section.bolum')).toHaveLength(2)
    vi.mocked(degisiklikApi.rapor).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Aralık en çok 366 gün olabilir.'),
    )
    await getir(w)
    expect(w.get('[role="alert"]').text()).toBe('Aralık en çok 366 gün olabilir.')
    expect(w.findAll('section.bolum')).toHaveLength(0)
  })
})
