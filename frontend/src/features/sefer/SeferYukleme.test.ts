import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./seferApi', () => ({ seferApi: { yukle: vi.fn(), sablon: vi.fn() } }))

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
})
