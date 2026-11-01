import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/rapor/cizelgeApi', () => ({ cizelgeApi: { getir: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/tanim/tanimApi')>()),
  tanimApi: { liste: vi.fn() },
}))

import { kurumGuncelle, kurumSifirla } from '@/composables/kurum'
import { kullanici } from '@/composables/oturum'
import { cizelgeApi, type CizelgeYaniti } from '@/features/rapor/cizelgeApi'
import { tanimApi } from '@/features/tanim/tanimApi'
import CizelgeYazdir from './CizelgeYazdir.vue'
import routes from './routes'

const bos = { template: '<div />' }
const yanit: CizelgeYaniti = {
  ay: '2026-10',
  gunSayisi: 31,
  tur: 'MAKINIST',
  garId: null,
  kontrol: false,
  uretimZamani: '2026-10-12T07:20:00Z', // İstanbul 10:20
  ureten: 'Ata Maci',
  tatiller: [{ gun: 29, ad: 'Cumhuriyet Bayramı' }],
  satirlar: [
    {
      makinistId: 1,
      sicil: 'D001',
      adSoyad: 'Ali Veli',
      bagliGar: 'Ankara',
      gorevSaat: 16,
      geceSaat: 2.5,
      km: 500,
      seferSayisi: 2,
      yolculukSayisi: 0,
      izinGunu: 0,
      hucreler: [
        {
          gun: 1,
          gorevler: [{ seferId: 7, trenNo: 'T1', kalkis: '06:00', varis: '14:00', rol: 'ASIL' }],
        },
        {
          gun: 12,
          gorevler: [
            { seferId: 9, trenNo: 'T2', kalkis: '22:00', varis: '06:00', rol: 'ASIL', gece: true },
          ],
        },
      ],
    },
    {
      makinistId: 2,
      sicil: 'D002',
      adSoyad: 'Ayşe Kaya',
      bagliGar: 'Ankara',
      gorevSaat: 0,
      geceSaat: 0,
      km: 0,
      seferSayisi: 0,
      yolculukSayisi: 0,
      izinGunu: 2,
      hucreler: [{ gun: 5, izin: 'YILLIK' }],
    },
  ],
}

async function kur(props: Record<string, string> = { ay: '2026-10', boyut: 'A4' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/rapor/cizelge', name: 'rapor-cizelge', component: bos }],
  })
  await router.push('/rapor/cizelge')
  const w = mount(CizelgeYazdir, { props, global: { plugins: [router] } })
  await flushPromises()
  return w
}
const sayfaKurali = () =>
  document.head.querySelector('style[data-cizelge-sayfa]')?.textContent ?? ''

describe('CizelgeYazdir: A3/A4 ilan baskısı (F5, AK-5.8)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.head.querySelectorAll('style[data-cizelge-sayfa]').forEach((s) => s.remove())
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit)
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'Eskişehir' },
    ])
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
    kurumGuncelle({ ad: 'Örnek İşletme (demo)', logoVar: false, logoSurum: 0 })
  })

  it('ay + süzgeçle ister; başlıkta kurum adı, dönem, personel türü ve gar; altbilgide sunucu zamanı ve yazdıran', async () => {
    const w = await kur({ ay: '2026-10', boyut: 'A3', garId: '2' })
    expect(cizelgeApi.getir).toHaveBeenCalledWith('2026-10', {
      tur: undefined,
      garId: 2,
      kontrol: false,
    })
    expect(w.get('[data-test="yazdir-kurum"]').text()).toContain('Örnek İşletme (demo)')
    expect(w.get('h1').text()).toBe('Aylık çizelge')
    const filtre = w.get('.yazdir-filtre').text()
    expect(filtre).toContain('Dönem: Ekim 2026')
    expect(filtre).toContain('Personel: Makinist')
    expect(filtre).toContain('Gar: Eskişehir')
    const alt = w.get('footer').text().replace(/\s+/g, ' ')
    expect(alt).toContain('Son güncelleme: 12.10.2026 10:20')
    expect(alt).toContain('Yazdıran: Ayşe Kaya')
    expect(alt).toContain('Kayıt sayısı: 2')
    expect(w.get('.no-print button').attributes('disabled')).toBeUndefined()
  })

  it('kurum adı girilmemişse ürün adı; kondüktör türü başlıkta', async () => {
    kurumSifirla()
    const w = await kur({ ay: '2026-10', boyut: 'A4', tur: 'KONDUKTOR' })
    expect(cizelgeApi.getir).toHaveBeenCalledWith('2026-10', {
      tur: 'KONDUKTOR',
      garId: null,
      kontrol: false,
    })
    expect(w.get('.yazdir-filtre').text()).toContain('Personel: Kondüktör')
    expect(w.get('.yazdir-filtre').text()).not.toContain('Gar:')
  })

  it('sayfa boyutu: A3 ve A4 ayrı @page size kuralı (yatay); bilinmeyen boyut A4; ayrılınca kural kalkar', async () => {
    const a3 = await kur({ ay: '2026-10', boyut: 'A3' })
    expect(sayfaKurali()).toContain('size: A3 landscape')
    expect(sayfaKurali()).toContain('@page')
    a3.unmount()
    expect(sayfaKurali()).toBe('')
    const a4 = await kur({ ay: '2026-10', boyut: 'A4' })
    expect(sayfaKurali()).toContain('size: A4 landscape')
    expect(sayfaKurali()).not.toContain('A3')
    a4.unmount()
    const bilinmeyen = await kur({ ay: '2026-10', boyut: 'A0' })
    expect(sayfaKurali()).toContain('size: A4 landscape')
    expect(document.head.querySelectorAll('style[data-cizelge-sayfa]')).toHaveLength(1)
    bilinmeyen.unmount()
  })

  it('tablo: thead (her sayfada yinelenir), sütunlar Personel + gün + 4 toplam; satır başına bir tr; hücre kısa kod', async () => {
    const w = await kur({ ay: '2026-10', boyut: 'A4' })
    expect(w.get('table').classes()).toContain('yazdir-tablo')
    expect(w.findAll('thead th[scope="col"]')).toHaveLength(1 + 31 + 4)
    expect(w.findAll('tbody tr')).toHaveLength(2)
    expect(w.findAll('tbody th[scope="row"]')).toHaveLength(2)
    const d1 = w.findAll('tbody tr')[0]!.findAll('td.hucre')
    expect(d1).toHaveLength(31)
    expect(d1[0]!.get('[aria-hidden="true"]').text()).toBe('T1') // A4: görünür yalnız kısa kod (saat yok)
    expect(d1[0]!.find('.hucre-saat').exists()).toBe(false)
    expect(w.findAll('tbody tr')[1]!.findAll('td.hucre')[4]!.text()).toContain('İ')
    expect(d1[11]!.find('svg[data-simge="ay"]').exists()).toBe(true)
    expect(d1[11]!.text()).toContain('gece görevi')
  })

  it('A3: hücrede kalkış saati de yazar (daha geniş sütun)', async () => {
    const w = await kur({ ay: '2026-10', boyut: 'A3' })
    expect(w.findAll('tbody tr')[0]!.findAll('td.hucre')[0]!.text()).toContain('06:00')
  })

  it('hafta sonu ve tatil sütunları başlıkta metinle işaretli', async () => {
    const w = await kur()
    const gunler = w.findAll('thead th.gun')
    expect(gunler.filter((t) => t.classes().includes('hafta-sonu'))).toHaveLength(9)
    expect(gunler[28]!.text()).toContain('Cumhuriyet Bayramı')
  })

  it('veri hazır değilken (hata) basılmaz: Yazdır pasif, hata görünür', async () => {
    vi.mocked(cizelgeApi.getir).mockRejectedValue(new Error('x'))
    const w = await kur()
    expect(w.find('table').exists()).toBe(false)
    expect(w.get('.no-print button').attributes('disabled')).toBeDefined()
    expect(w.get('[role="alert"]').text()).toContain('Çizelge alınamadı')
  })

  it('D-7: bozuk ay (2026-13, ekim) istek atmaz; "Ay geçersiz" der, dönem satırında "Invalid Date" yazmaz', async () => {
    for (const ay of ['2026-13', 'ekim', '2026-1']) {
      vi.mocked(cizelgeApi.getir).mockClear()
      const w = await kur({ ay, boyut: 'A4' })
      expect(cizelgeApi.getir).not.toHaveBeenCalled()
      expect(w.get('[role="alert"]').text()).toContain('Ay geçersiz')
      expect(w.text()).not.toContain('Invalid Date')
    }
  })

  it("BUG-3: basılı hücrede tren no bölünmez: sığmayan uzun no yalnız numarasıyla, kısa no aynen; tam no erişilebilir adda; '+N' kendi satırında", async () => {
    const uzun: CizelgeYaniti = {
      ...yanit,
      satirlar: [
        {
          ...yanit.satirlar[0]!,
          hucreler: [
            {
              gun: 1,
              gorevler: [
                { seferId: 7, trenNo: 'AS-035', kalkis: '06:00', varis: '14:00', rol: 'ASIL' },
                { seferId: 8, trenNo: 'T1', kalkis: '15:00', varis: '17:00', rol: 'ASIL' },
              ],
            },
            {
              gun: 2,
              gorevler: [
                { seferId: 9, trenNo: 'T5', kalkis: '06:00', varis: '14:00', rol: 'ASIL' },
              ],
            },
          ],
        },
      ],
    }
    for (const boyut of ['A4', 'A3']) {
      vi.mocked(cizelgeApi.getir).mockResolvedValue(uzun)
      const w = await kur({ ay: '2026-10', boyut })
      const d = w.findAll('tbody tr')[0]!.findAll('td.hucre')
      expect(d[0]!.get('[aria-hidden="true"]').text()).toContain('035')
      expect(d[0]!.get('[aria-hidden="true"]').text()).not.toContain('AS-')
      expect(d[0]!.get('.gorunmez').text()).toContain('AS-035')
      expect(d[0]!.get('b.ek').text()).toBe('+1')
      expect(d[1]!.get('[aria-hidden="true"]').text()).toBe('T5')
    }
    const kaynak = readFileSync(
      join(process.cwd(), 'src/features/yazdir/CizelgeYazdir.vue'),
      'utf8',
    )
    expect(kaynak).toMatch(/td\.hucre \{[^}]*white-space: nowrap/)
    expect(kaynak).not.toMatch(/td\.hucre \{[^}]*overflow-wrap: anywhere/)
  })

  it('BUG-3: numara hiç kırpılmaz: sığmayan uzun numara tam basılır ve hücrede satır kırar', async () => {
    const uzunNo: CizelgeYaniti = {
      ...yanit,
      satirlar: [
        {
          ...yanit.satirlar[0]!,
          hucreler: [
            {
              gun: 1,
              gorevler: [
                { seferId: 7, trenNo: '32001', kalkis: '06:00', varis: '14:00', rol: 'ASIL' },
              ],
            },
            {
              gun: 2,
              gorevler: [
                { seferId: 9, trenNo: 'B-123456', kalkis: '06:00', varis: '14:00', rol: 'ASIL' },
              ],
            },
          ],
        },
      ],
    }
    vi.mocked(cizelgeApi.getir).mockResolvedValue(uzunNo)
    const w = await kur({ ay: '2026-10', boyut: 'A4' })
    const d = w.findAll('tbody tr')[0]!.findAll('td.hucre')
    expect(d[0]!.get('[aria-hidden="true"]').text()).toBe('32001')
    expect(d[0]!.get('[aria-hidden="true"]').classes()).toContain('kirilir')
    expect(d[1]!.get('[aria-hidden="true"]').text()).toBe('B-123456')
  })

  it('ay yoksa istek atılmaz, "Ay gerekli." gösterilir', async () => {
    const w = await kur({ boyut: 'A4' })
    expect(cizelgeApi.getir).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toBe('Ay gerekli.')
  })

  it('yazdır rotası: menüsüz (meta.yazdir), sorgu özelliklere çevrilir', () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    const r = router.resolve('/yazdir/cizelge?ay=2026-10&boyut=A3&tur=KONDUKTOR&garId=2')
    expect(r.name).toBe('yazdir-cizelge')
    expect(r.meta.yazdir).toBe(true)
    expect(r.meta.menu).toBeUndefined()
    const props = r.matched[0]!.props.default as (r: unknown) => Record<string, unknown>
    expect(props(r)).toEqual({ ay: '2026-10', boyut: 'A3', tur: 'KONDUKTOR', garId: '2' })
  })

  it('yazı boyutu: yazdırma CSS kuralındaki her punto ≥ 6,5 pt (31 sütun okunur, AK-5.8); ham renk yok', () => {
    const kaynak = readFileSync(
      join(process.cwd(), 'src/features/yazdir/CizelgeYazdir.vue'),
      'utf8',
    )
    const punto = [...kaynak.matchAll(/font-size:\s*([\d.]+)pt/g)].map((m) => Number(m[1]))
    expect(punto.length).toBeGreaterThanOrEqual(2) // A4 ve A3
    expect(Math.min(...punto)).toBeGreaterThanOrEqual(6.5)
    expect(kaynak).not.toMatch(/#[0-9a-fA-F]{3,8}\b|rgba?\(/)
  })
})
