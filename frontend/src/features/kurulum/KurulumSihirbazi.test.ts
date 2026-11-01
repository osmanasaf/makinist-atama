import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./kurulumApi', () => ({ kurulumApi: { durum: vi.fn(), gizle: vi.fn() } }))
vi.mock('@/features/kurum/kurumApi', () => ({
  kurumApi: { adYaz: vi.fn(), logoYaz: vi.fn(), logoSil: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import { kurumSifirla } from '@/composables/kurum'
import { kurumApi } from '@/features/kurum/kurumApi'
import KurulumSihirbazi from './KurulumSihirbazi.vue'
import { kurulumApi, type KurulumDurumu } from './kurulumApi'
import kurulumRotalari from './routes'

const adimlar = (yapilan: number): KurulumDurumu => ({
  adimlar: (
    [
      ['KURUM', 'Kurum adı ve logo', 'Kurum adı girilmedi'],
      ['TANIM', 'Gar, hat ve araç tipi tanımları', '1 gar, 0 hat, 0 araç tipi'],
      ['MAKINIST', 'Makinist listesi', '0 aktif makinist'],
      ['PROFIL', 'Kural profili', '0 profil uygulaması'],
      ['SEFER', 'İlk seferler', '0 sefer'],
    ] as const
  ).map(([kod, ad, ayrinti], i) => ({ kod, ad, yapildi: i < yapilan, ayrinti })),
  tamam: yapilan === 5,
  gizli: false,
})

async function kur() {
  const yol = (path: string, name: string) => ({ path, name, component: { template: '<div />' } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      yol('/', 'ana'),
      yol('/tanimlar', 'tanimlar'),
      yol('/makinistler/yukle', 'makinist-yukle'),
      yol('/profil', 'profil'),
      yol('/seferler/yukle', 'sefer-yukle'),
    ],
  })
  await router.push('/')
  const w = mount(KurulumSihirbazi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('KurulumSihirbazi (F1, AK-1.9)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kurumSifirla()
  })

  it('beş adım sırayla; durum sunucudan gelir; yapılan "Tamam", yapılmayan "Bekliyor" (metin + simge)', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(adimlar(2))
    const w = await kur()
    expect(w.get('h1').text()).toBe('İlk kurulum')
    const li = w.findAll('ol.kurulum-adimlar > li')
    expect(li).toHaveLength(5)
    expect(li.map((x) => x.get('h2').text())).toEqual([
      'Kurum adı ve logo',
      'Gar, hat ve araç tipi tanımları',
      'Makinist listesi',
      'Kural profili',
      'İlk seferler',
    ])
    expect(li[0]!.get('.rozet').text()).toBe('Tamam')
    expect(li[1]!.get('.rozet').text()).toBe('Tamam')
    expect(li[2]!.get('.rozet').text()).toBe('Bekliyor')
    expect(li[2]!.find('.rozet svg').exists()).toBe(true)
    expect(li[2]!.text()).toContain('0 aktif makinist')
    expect(w.text()).toContain('2 / 5 adım tamam')
  })

  it('her adım mevcut sayfaya bağlanır (Tanımlar, Personel yükleme, Profil, Sefer yükleme)', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(adimlar(0))
    const w = await kur()
    const href = (kod: string) => w.get(`[data-test=adim-${kod}] a.btn`).attributes('href')
    expect(href('TANIM')).toBe('/tanimlar')
    expect(href('MAKINIST')).toBe('/makinistler/yukle')
    expect(href('PROFIL')).toBe('/profil')
    expect(href('SEFER')).toBe('/seferler/yukle')
  })

  it('ilk adım satır içi kurum formunu taşır; ad kaydedilince durum yeniden okunur', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValueOnce(adimlar(0)).mockResolvedValueOnce(adimlar(1))
    vi.mocked(kurumApi.adYaz).mockResolvedValue({
      ad: 'Örnek İşletme (demo)',
      logoVar: false,
      logoSurum: 1,
    })
    const w = await kur()
    const adim = w.get('[data-test=adim-KURUM]')
    await adim.get('#kurum-ad').setValue('Örnek İşletme (demo)')
    await adim.get('form').trigger('submit')
    await flushPromises()
    expect(kurumApi.adYaz).toHaveBeenCalledWith('Örnek İşletme (demo)')
    expect(kurulumApi.durum).toHaveBeenCalledTimes(2)
    expect(w.get('[data-test=adim-KURUM] .rozet').text()).toBe('Tamam')
  })

  it('tamamlanınca "Kurulum tamamlandı" bandı; gizle düğmesi çıkmaz', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(adimlar(5))
    const w = await kur()
    expect(w.get('[data-test=tamam]').text()).toContain('Kurulum tamamlandı')
    expect(w.find('[data-test=gizle]').exists()).toBe(false)
  })

  it('eksik adım varken "Hatırlatmayı gizle" kalıcı olarak gizler; gizliyken "yeniden göster" çıkar', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(adimlar(1))
    vi.mocked(kurulumApi.gizle).mockResolvedValue({ ...adimlar(1), gizli: true })
    const w = await kur()
    await w.get('[data-test=gizle]').trigger('click')
    await flushPromises()
    expect(kurulumApi.gizle).toHaveBeenCalledWith(true)
    expect(w.get('[data-test=gizle]').text()).toBe('Hatırlatmayı yeniden göster')
    vi.mocked(kurulumApi.gizle).mockResolvedValue({ ...adimlar(1), gizli: false })
    await w.get('[data-test=gizle]').trigger('click')
    await flushPromises()
    expect(kurulumApi.gizle).toHaveBeenLastCalledWith(false)
  })

  it('durum yüklenemezse hata durumu ve "Yeniden dene" (role=alert)', async () => {
    vi.mocked(kurulumApi.durum)
      .mockRejectedValueOnce(new ApiHata(500, 'SUNUCU_HATASI', 'Durum alınamadı.'))
      .mockResolvedValueOnce(adimlar(0))
    const w = await kur()
    expect(w.get('[role=alert]').text()).toContain('Durum alınamadı.')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Yeniden dene')!
      .trigger('click')
    await flushPromises()
    expect(w.findAll('ol.kurulum-adimlar > li')).toHaveLength(5)
  })

  it('rota yalnız yönetici içindir ve Ayarlar menü grubundadır', () => {
    const r = kurulumRotalari[0]!
    expect(r.path).toBe('/kurulum')
    expect(r.meta).toMatchObject({ rol: 'Y', menu: { ad: 'Kurulum', grup: 'Ayarlar' } })
  })
})
