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

// OK-50 düzeltme turu (O-5, O-6, BUG-6, D-7): çizelge odak, erişilebilir ad ve hata/boş durumları.
const bos = { template: '<div />' }
const satir = (id: number): CizelgeSatiri => ({
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
})
const yanit = (n: number): CizelgeYaniti => ({
  ay: '2026-10',
  gunSayisi: 31,
  tur: 'MAKINIST',
  garId: null,
  kontrol: false,
  uretimZamani: '2026-10-01T07:20:00Z',
  ureten: 'Ata Maci',
  tatiller: [],
  satirlar: Array.from({ length: n }, (_, i) => satir(i + 1)),
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

describe('Aylık çizelge düzeltmeleri (OK-50)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(tanimApi.liste).mockResolvedValue([{ id: 1, tip: 'GAR', ad: 'Ankara' }])
  })

  it('O-5: son sayfaya geçilince pasif olan "Sonraki sayfa"dan odak "Önceki sayfa"ya geçer (body\'ye düşmez)', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit(51))
    const w = await kur()
    const sonraki = w.get<HTMLButtonElement>('[data-test="sonraki-sayfa"]')
    sonraki.element.focus()
    await sonraki.trigger('click')
    await flushPromises()
    expect(sonraki.attributes('disabled')).toBeDefined()
    expect(document.activeElement).toBe(w.get('[data-test="onceki-sayfa"]').element)
    // ve ters yön: ilk sayfaya dönünce odak "Sonraki sayfa"ya geçer
    await w.get('[data-test="onceki-sayfa"]').trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(w.get('[data-test="sonraki-sayfa"]').element)
  })

  it('O-6: tablo grid rolünde (ok tuşu gezintisi); kontrol düğmesinin adı etiketini de içerir, görünen "İşaretle" adın parçasıdır', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit(2))
    const w = await kur()
    expect(w.get('table').attributes('role')).toBe('grid')
    expect(w.get('table').attributes('aria-readonly')).toBe('true')
    const d = w.get('button[data-test="kontrol"]')
    const adlari = d
      .attributes('aria-labelledby')!
      .split(' ')
      .map((id) => document.getElementById(id)!.textContent!.trim())
    expect(adlari.join(' ')).toBe('Kontrol bekleyen atamalar İşaretle')
  })

  it('O-6: ay/süzgeç değişince veri doluyken kap aria-busy olur ve durum duyurulur', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValueOnce(yanit(2))
    const w = await kur()
    expect(w.get('.cizelge-kap').attributes('aria-busy')).toBe('false')
    let bitir!: (y: CizelgeYaniti) => void
    vi.mocked(cizelgeApi.getir).mockReturnValueOnce(new Promise((c) => (bitir = c)))
    await w.get('select#cizelge-gar').setValue('1')
    expect(w.get('.cizelge-kap').attributes('aria-busy')).toBe('true')
    expect(w.get('p[role="status"]').text()).toBe('Çizelge güncelleniyor…')
    bitir(yanit(2))
    await flushPromises()
    expect(w.get('.cizelge-kap').attributes('aria-busy')).toBe('false')
    expect(w.get('p[role="status"]').text()).toBe('')
  })

  it('BUG-6: sunucu hatasında "Yeniden dene" düğmesi aynı isteği yeniden yapar ve tabloyu getirir', async () => {
    vi.mocked(cizelgeApi.getir).mockRejectedValueOnce(
      new ApiHata(503, 'SUNUCU', 'Sunucuya ulaşılamadı.'),
    )
    const w = await kur()
    expect(w.text()).toContain('Sunucuya ulaşılamadı.')
    vi.mocked(cizelgeApi.getir).mockResolvedValueOnce(yanit(2))
    await w.get('[data-test="yeniden-dene"]').trigger('click')
    await flushPromises()
    expect(w.text()).not.toContain('Sunucuya ulaşılamadı.')
    expect(w.findAll('tbody tr')).toHaveLength(2)
  })

  it('D-7: ay alanı boşaltılınca eski ayın tablosu kalmaz, "Ay seçin." denir; ay yeniden girilince tablo gelir', async () => {
    vi.mocked(cizelgeApi.getir).mockResolvedValue(yanit(2))
    const w = await kur()
    expect(w.findAll('tbody tr')).toHaveLength(2)
    await w.get('input[type="month"]').setValue('')
    await flushPromises()
    expect(w.findAll('tbody tr')).toHaveLength(0)
    expect(w.text()).toContain('Ay seçin.')
    await w.get('input[type="month"]').setValue('2026-10')
    await flushPromises()
    expect(w.text()).not.toContain('Ay seçin.')
    expect(w.findAll('tbody tr')).toHaveLength(2)
  })
})
