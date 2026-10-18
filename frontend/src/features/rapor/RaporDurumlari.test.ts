import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { adalet: vi.fn(), adaletExcel: vi.fn(), yedek: vi.fn() },
}))
vi.mock('@/features/konaklama/konaklamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/konaklama/konaklamaApi')>()),
  konaklamaApi: { rapor: vi.fn(), raporExcel: vi.fn() },
}))
vi.mock('@/features/degisiklik/degisiklikApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/degisiklik/degisiklikApi')>()),
  degisiklikApi: { rapor: vi.fn(), excel: vi.fn() },
}))

import AdaletRaporu from './AdaletRaporu.vue'
import YedekTakvimi from './YedekTakvimi.vue'
import KonaklamaRaporu from '@/features/konaklama/KonaklamaRaporu.vue'
import DegisiklikRaporu from '@/features/degisiklik/DegisiklikRaporu.vue'
import { raporApi } from './raporApi'
import { konaklamaApi } from '@/features/konaklama/konaklamaApi'
import { degisiklikApi } from '@/features/degisiklik/degisiklikApi'
import { adaletYaniti } from './testVerisi'

const stubs = { RouterLink: { template: '<a><slot /></a>' } }

describe('rapor ekranları: ortak durum ve form kuralları (E3)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('her rapor sayfasında tek h1; bekleme sırasında yükleniyor durumu (role=status)', async () => {
    let coz: (r: typeof adaletYaniti) => void = () => {}
    vi.mocked(raporApi.adalet).mockReturnValue(new Promise((c) => (coz = c)))
    const w = mount(AdaletRaporu, { global: { stubs } })
    await flushPromises()
    expect(w.findAll('h1')).toHaveLength(1)
    expect(w.get('h1').text()).toBe('Adalet raporu')
    expect(w.get('.veri-durumu-yukleniyor').attributes('role')).toBe('status')
    coz(adaletYaniti)
    await flushPromises()
    expect(w.find('.veri-durumu-yukleniyor').exists()).toBe(false)
    expect(w.findAll('h1')).toHaveLength(1)
  })

  it('Adalet: Excel/Yazdır başlık eylemlerinde; ay alanı zorunlu işaretli; boş rapor "Kayıt yok."', async () => {
    vi.mocked(raporApi.adalet).mockResolvedValue({ ...adaletYaniti, satirlar: [] })
    const w = mount(AdaletRaporu, { global: { stubs } })
    await flushPromises()
    expect(w.get('.sayfa-baslik-eylemler').text()).toContain('Excel indir')
    expect(w.get('#adalet-ay').element.closest('.alan')!.classList).toContain('alan-zorunlu')
    expect(w.get('tbody .veri-durumu-bos').text()).toBe('Kayıt yok.')
  })

  it('Adalet: ay boşsa hata alanın ALTINDA (.alan-hata, aria-invalid + aria-describedby), istek atılmaz', async () => {
    vi.mocked(raporApi.adalet).mockResolvedValue(adaletYaniti)
    const w = mount(AdaletRaporu, { global: { stubs } })
    await flushPromises()
    vi.mocked(raporApi.adalet).mockClear()
    await w.get('#adalet-ay').setValue('')
    await w.get('form').trigger('submit')
    await flushPromises()
    const girdi = w.get('#adalet-ay')
    const hata = w.get('.alan-hata')
    expect(hata.text()).toBe('Ay seçin.')
    expect(girdi.attributes('aria-invalid')).toBe('true')
    expect(girdi.attributes('aria-describedby')).toBe(hata.attributes('id'))
    expect(girdi.element.closest('.alan')!.contains(hata.element)).toBe(true)
    expect(raporApi.adalet).not.toHaveBeenCalled()
  })

  it('Yedek planı: hata VeriDurumu hata (role=alert); durum hücresi rozet', async () => {
    vi.mocked(raporApi.yedek).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Rapor alınamadı.'))
    const w = mount(YedekTakvimi)
    await flushPromises()
    expect(w.get('.veri-durumu-hata').attributes('role')).toBe('alert')
    expect(w.get('h1').text()).toBe('Yedek planı')
  })

  it('Konaklama raporu: kayıt yoksa boş durum; hata VeriDurumu', async () => {
    vi.mocked(konaklamaApi.rapor).mockResolvedValue({
      ay: '2026-10',
      uretimZamani: '2026-10-31T10:00:00+03:00',
      ureten: 'Ata Maci',
      toplamGece: 0,
      toplamEksikOneri: 0,
      makinistler: [],
    })
    const w = mount(KonaklamaRaporu)
    await flushPromises()
    expect(w.get('.veri-durumu-bos').text()).toContain('Bu ay konaklama kaydı ve öneri yok.')
    vi.mocked(konaklamaApi.rapor).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Rapor alınamadı.'))
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('.veri-durumu-hata').text()).toBe('Rapor alınamadı.')
    expect(w.findAll('h1')).toHaveLength(1)
  })

  it('Denetim raporu: rapor yokken boş durum; tarih hatası bitiş alanının altında, odak o alana', async () => {
    const w = mount(DegisiklikRaporu, { global: { stubs }, attachTo: document.body })
    await flushPromises()
    expect(w.find('[data-test="denetim-bos"]').exists()).toBe(true)
    await w.get('#rapor-bas').setValue('2026-10-12')
    await w.get('#rapor-bit').setValue('2026-10-01')
    await w.get('form').trigger('submit')
    await flushPromises()
    const bit = w.get('#rapor-bit')
    const hata = w.get('#rapor-bit-hata')
    expect(hata.classes()).toContain('alan-hata')
    expect(bit.attributes('aria-invalid')).toBe('true')
    expect(bit.attributes('aria-describedby')).toBe('rapor-bit-hata')
    expect(w.get('#rapor-bas').attributes('aria-invalid')).toBeUndefined()
    expect(document.activeElement).toBe(bit.element)
    expect(degisiklikApi.rapor).not.toHaveBeenCalled()
    w.unmount()
  })
})
