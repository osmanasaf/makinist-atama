import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./seferApi', () => ({ seferApi: { yukle: vi.fn(), sablon: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import SeferYukleme from './SeferYukleme.vue'
import { seferApi, type YuklemeSonucu } from './seferApi'

// QA (M1): arayüz durumları. Sunucu sözleşmesi gerçek yanıttan alındı: kalkis/varis ISO ofsetli zaman.
const bos: YuklemeSonucu = {
  onizleme: true,
  toplamSatir: 0,
  eklenen: 0,
  zatenVar: 0,
  hataSayisi: 0,
  satirlar: [],
  hatalar: [],
}
const gercekYanit: YuklemeSonucu = {
  onizleme: true,
  toplamSatir: 1,
  eklenen: 1,
  zatenVar: 0,
  hataSayisi: 0,
  satirlar: [
    {
      satirNo: 2,
      trenNo: 'QA-001',
      tarih: '2026-12-25',
      kalkisGar: 'Ankara',
      varisGar: 'Eskişehir',
      kalkis: '2026-12-25T08:00:00+03:00',
      varis: '2026-12-25T09:30:00+03:00',
      aracTipi: 'YHT',
      hat: 'Ankara–Eskişehir',
      durum: 'YENI',
    },
  ],
  hatalar: [],
}

async function dosyaSec(ad = 'seferler.xlsx') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'seferler', component: { template: '<div />' } }],
  })
  await router.push('/')
  await router.isReady()
  const w = mount(SeferYukleme, { global: { plugins: [router] } })
  const girdi = w.get('#dosya')
  Object.defineProperty(girdi.element, 'files', {
    value: [new File(['x'], ad)],
    configurable: true,
  })
  await girdi.trigger('change')
  await flushPromises()
  return w
}

describe('SeferYukleme (QA durumları)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('yalnız başlıklı dosya: sıfır satır özeti görünür, onay düğmesi kapalı, hata yok', async () => {
    vi.mocked(seferApi.yukle).mockResolvedValue(bos)
    const w = await dosyaSec()
    expect(w.get('[role=status]').text()).toContain('Toplam satır: 0')
    expect(w.find('[role=alert]').exists()).toBe(false)
    const onay = w.findAll('button').find((b) => b.text() === 'Onayla ve yükle')!
    expect(onay.attributes('disabled')).toBeDefined()
  })

  it('boş / geçersiz dosya: sunucu mesajı alarm olarak gösterilir, önizleme tablosu yok', async () => {
    vi.mocked(seferApi.yukle).mockRejectedValue(new ApiHata(400, 'GECERSIZ_DOSYA', 'Dosya boş.'))
    const w = await dosyaSec('bos.xlsx')
    expect(w.get('[role=alert]').text()).toBe('Dosya boş.')
    expect(w.find('table').exists()).toBe(false)
  })

  it('5 MB üstü: 413 sunucu mesajı gösterilir', async () => {
    vi.mocked(seferApi.yukle).mockRejectedValue(
      new ApiHata(413, 'DOSYA_COK_BUYUK', 'Dosya çok büyük. En çok 5 MB yüklenebilir.'),
    )
    const w = await dosyaSec('dev.xlsx')
    expect(w.get('[role=alert]').text()).toContain('En çok 5 MB')
  })

  // BUG-4 (QA M1): önizleme tablosu kalkış/varışı ham ISO dizesi basıyordu.
  it('BUG-4: önizleme kalkış/varış saatini okunur biçimde (25.12.2026 08:00) gösterir, ham ISO değil', async () => {
    vi.mocked(seferApi.yukle).mockResolvedValue(gercekYanit)
    const w = await dosyaSec()
    const tablo = w.get('table[aria-label="Geçerli satırlar"]').text()
    expect(tablo).not.toContain('2026-12-25T08:00:00+03:00')
    expect(tablo).toContain('25.12.2026 08:00')
  })

  // BUG-5 (QA M1): 5 MB üstü dosya hiç gönderilmez (erken yanıt + bağlantı sıfırlama "Sunucuya ulaşılamadı" gösteriyordu).
  it('BUG-5: 5 MB üstü dosya sunucuya gitmeden anlamlı mesajla reddedilir', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', name: 'seferler', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const w = mount(SeferYukleme, { global: { plugins: [router] } })
    const dev = new File(['x'], 'dev.xlsx')
    Object.defineProperty(dev, 'size', { value: 6 * 1024 * 1024 })
    const girdi = w.get('#dosya')
    Object.defineProperty(girdi.element, 'files', { value: [dev], configurable: true })
    await girdi.trigger('change')
    await flushPromises()
    expect(w.get('[role=alert]').text()).toBe('Dosya çok büyük. En çok 5 MB yüklenebilir.')
    expect(seferApi.yukle).not.toHaveBeenCalled()
  })
})
