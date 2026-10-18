import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./seferApi', () => ({ seferApi: { yukle: vi.fn(), sablon: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import SeferYukleme from './SeferYukleme.vue'
import { seferApi, type YuklemeSonucu } from './seferApi'

const onizleme: YuklemeSonucu = {
  onizleme: true,
  toplamSatir: 4,
  eklenen: 2,
  zatenVar: 1,
  hataSayisi: 1,
  satirlar: [
    {
      satirNo: 2,
      trenNo: '1001',
      tarih: '2026-10-12',
      kalkisGar: 'Ankara',
      varisGar: 'İzmir',
      kalkis: '08:00',
      varis: '14:00',
      aracTipi: 'E68000',
      hat: 'Ankara-İzmir',
      durum: 'YENI',
    },
    {
      satirNo: 3,
      trenNo: '1002',
      tarih: '2026-10-12',
      kalkisGar: 'Ankara',
      varisGar: 'İzmir',
      kalkis: '09:00',
      varis: '15:00',
      aracTipi: 'E68000',
      hat: 'Ankara-İzmir',
      durum: 'ZATEN_VAR',
    },
  ],
  hatalar: [{ satirNo: 5, neden: 'Bilinmeyen gar: Xyz' }],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'seferler', component: { template: '<div />' } }],
  })
  await router.push('/')
  await router.isReady()
  const w = mount(SeferYukleme, { global: { plugins: [router] } })
  const girdi = w.get('#dosya')
  const dosya = new File(['x'], 'seferler.xlsx')
  Object.defineProperty(girdi.element, 'files', { value: [dosya], configurable: true })
  await girdi.trigger('change')
  await flushPromises()
  return { w, dosya }
}

describe('SeferYukleme', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(seferApi.yukle).mockResolvedValue(onizleme)
  })

  it('dosya seçilince önizleme (onizle=true) istenir; hata satırı satır no ve nedenle, zaten var sayısı görünür', async () => {
    const { w, dosya } = await kur()
    expect(seferApi.yukle).toHaveBeenCalledWith(dosya, true)
    const hataTablosu = w.get('table[aria-label="Hatalı satırlar"]')
    expect(hataTablosu.text()).toContain('5')
    expect(hataTablosu.text()).toContain('Bilinmeyen gar: Xyz')
    expect(w.get('[role=status]').text()).toContain('Zaten var: 1')
  })

  it('hata satırı olsa da onay açıktır; tıklanınca onizle=false isteği gider', async () => {
    const { w, dosya } = await kur()
    vi.mocked(seferApi.yukle).mockResolvedValue({ ...onizleme, onizleme: false })
    const onay = w.findAll('button').find((b) => b.text() === 'Onayla ve yükle')!
    expect(onay.attributes('disabled')).toBeUndefined()
    await onay.trigger('click')
    await flushPromises()
    expect(seferApi.yukle).toHaveBeenLastCalledWith(dosya, false)
    expect(w.get('[role=status]').text()).toContain('Yükleme tamamlandı')
  })

  it('önizleme tablosu: satır no sağa hizalı, durum rozeti; dosya alanı zorunlu işaretli ve yardım metinli', async () => {
    const { w } = await kur()
    const satirlar = w.findAll('table[aria-label="Geçerli satırlar"] tbody tr')
    expect(satirlar[0]!.findAll('td')[0]!.classes()).toContain('sayisal')
    expect(satirlar[0]!.get('.rozet').classes()).toContain('rozet-basari')
    expect(satirlar[0]!.get('.rozet').text()).toBe('Yeni')
    expect(satirlar[1]!.get('.rozet').text()).toBe('Zaten var')
    expect(w.get('#dosya').element.closest('.alan')!.classList.contains('alan-zorunlu')).toBe(true)
    expect(w.get('#dosya').attributes('aria-describedby')).toBe('dosya-yardim')
    expect(w.get('#dosya-yardim').text()).toContain('5 MB')
  })

  it('dosya hatası dosya alanının altında: aria-invalid + aria-describedby ile bağlı', async () => {
    vi.mocked(seferApi.yukle).mockRejectedValue(new ApiHata(400, 'GECERSIZ_DOSYA', 'Dosya boş.'))
    const { w } = await kur()
    const girdi = w.get('#dosya')
    expect(girdi.attributes('aria-invalid')).toBe('true')
    expect(girdi.attributes('aria-describedby')).toBe('dosya-yardim dosya-hata')
    const hata = w.get('#dosya-hata')
    expect(hata.classes()).toContain('alan-hata')
    expect(hata.element.parentElement).toBe(girdi.element.parentElement)
  })
})
