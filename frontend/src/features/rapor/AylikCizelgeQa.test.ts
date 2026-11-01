import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./cizelgeApi', () => ({ cizelgeApi: { getir: vi.fn() } }))
vi.mock('@/features/tanim/tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/tanim/tanimApi')>()),
  tanimApi: { liste: vi.fn() },
}))

import AylikCizelge from './AylikCizelge.vue'
import { cizelgeApi, type CizelgeSatiri, type CizelgeYaniti } from './cizelgeApi'
import { tanimApi } from '@/features/tanim/tanimApi'

/** QA (yeni-ozellikler): çizelge sınır durumları: 28/29/31 günlük ay, 50/51 satır sayfa sınırı, tatil + hafta sonu çakışması, Türkçe arama, sıfır sonuç. */
const bos = { template: '<div />' }
const satir = (id: number, ek: Partial<CizelgeSatiri> = {}): CizelgeSatiri => ({
  makinistId: id,
  sicil: `D${String(id).padStart(3, '0')}`,
  adSoyad: `Makinist ${id}`,
  bagliGar: 'Ankara',
  gorevSaat: 0,
  geceSaat: 0,
  km: 0,
  seferSayisi: 0,
  yolculukSayisi: 0,
  izinGunu: 0,
  hucreler: [],
  ...ek,
})
const yanit = (
  ay: string,
  gunSayisi: number,
  satirlar: CizelgeSatiri[],
  tatiller: CizelgeYaniti['tatiller'] = [],
): CizelgeYaniti => ({
  ay,
  gunSayisi,
  tur: 'MAKINIST',
  garId: null,
  kontrol: false,
  uretimZamani: '2026-02-01T07:20:00Z',
  ureten: 'Ata Maci',
  tatiller,
  satirlar,
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
  return w
}

describe('Aylık çizelge sınır durumları (QA)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(tanimApi.liste).mockResolvedValue([{ id: 1, tip: 'GAR', ad: 'Ankara' }])
  })

  it.each([
    ['2026-02', 28],
    ['2024-02', 29],
    ['2026-04', 30],
    ['2026-12', 31],
  ])('%s: %i sütun başlığı ve satır başına %i hücre', async (ay, gun) => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(
      yanit(ay, gun, [satir(1, { hucreler: [{ gun, izin: 'YILLIK' }] })]),
    )
    const w = await kur()
    expect(w.findAll('thead th.gun')).toHaveLength(gun)
    expect(w.findAll('tbody tr')[0]!.findAll('td.hucre')).toHaveLength(gun)
    expect(w.findAll('tbody tr')[0]!.findAll('td.hucre')[gun - 1]!.text()).toContain('İ') // ayın son günü
  })

  it('sayfa sınırı: 50 satır tek sayfa (düğme yok); 51 satır iki sayfa, ikinci sayfada 1 satır', async () => {
    const liste = (n: number) => Array.from({ length: n }, (_, i) => satir(i + 1))
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit('2026-10', 31, liste(50)))
    let w = await kur()
    expect(w.findAll('tbody tr')).toHaveLength(50)
    expect(w.find('[data-test="sonraki-sayfa"]').exists()).toBe(false)
    expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('1–50 / 50')
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit('2026-10', 31, liste(51)))
    document.body.innerHTML = ''
    w = await kur()
    expect(w.findAll('tbody tr')).toHaveLength(50)
    await w.get('[data-test="sonraki-sayfa"]').trigger('click')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    expect(w.get('[data-test="sayfa-durumu"]').text()).toContain('51–51 / 51')
    expect(w.get('[data-test="sonraki-sayfa"]').attributes('disabled')).toBeDefined()
  })

  it('resmi tatil hafta sonuna denk gelirse başlıkta ikisi de işaretli ve metinle okunur (renk tek başına değil)', async () => {
    // Mart 2026: 21 Cumartesi, 22 Pazar
    vi.mocked(cizelgeApi.getir).mockResolvedValue(
      yanit(
        '2026-03',
        31,
        [satir(1)],
        [
          { gun: 21, ad: 'Ramazan Bayramı' },
          { gun: 22, ad: 'Ramazan Bayramı 2. gün' },
        ],
      ),
    )
    const w = await kur()
    const b = w.findAll('thead th.gun')
    for (const i of [20, 21]) {
      expect(b[i]!.classes()).toEqual(expect.arrayContaining(['hafta-sonu', 'tatil']))
      expect(b[i]!.text()).toContain('Tatil')
      expect(b[i]!.text()).toContain('hafta sonu')
      expect(b[i]!.text()).toContain('resmi tatil')
    }
    expect(b[22]!.classes()).not.toContain('tatil')
  })

  it('arama Türkçe büyük/küçük harf duyarsız: "ISPARTA" / "ıspar", "ŞÜKRÜ" / "şükrü", "İSMAİL" / "ismail"', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(
      yanit('2026-10', 31, [
        satir(1, { adSoyad: 'Işık Isparta' }),
        satir(2, { adSoyad: 'Şükrü Çelik' }),
        satir(3, { adSoyad: 'İsmail Yıldız' }),
      ]),
    )
    const w = await kur()
    const ara = w.get('input[type="search"]')
    await ara.setValue('ıspar')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    await ara.setValue('ŞÜKRÜ')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    await ara.setValue('İSMAİL')
    expect(w.findAll('tbody tr')).toHaveLength(1)
    await ara.setValue('ismail')
    expect(w.findAll('tbody tr')).toHaveLength(1)
  })

  it('arama hiçbir satırla eşleşmezse "1–0 / 0" gibi anlamsız aralık yazılmaz ve boş durum metni gösterilir', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit('2026-10', 31, [satir(1), satir(2)]))
    const w = await kur()
    await w.get('input[type="search"]').setValue('zzzzzz')
    expect(w.findAll('tbody tr')).toHaveLength(0)
    expect(w.get('[data-test="sayfa-durumu"]').text()).not.toMatch(/1–0/)
    expect(w.text()).toMatch(/bulunamadı|eşleşen|personel yok/i)
  })
})
