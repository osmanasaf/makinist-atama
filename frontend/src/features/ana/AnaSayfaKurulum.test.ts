import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import { kullanici } from '@/composables/oturum'

vi.mock('@/features/atama/atamaApi', () => ({
  atamaApi: { gun: vi.fn(), kirmiziSayisi: vi.fn(), gordumOzeti: vi.fn() },
}))
vi.mock('@/features/takas/takasApi', () => ({ takasApi: { liste: vi.fn() } }))
vi.mock('@/features/belge/belgeApi', () => ({ belgeApi: { durum: vi.fn() } }))
vi.mock('@/features/rapor/raporApi', () => ({ raporApi: { izinListe: vi.fn() } }))
vi.mock('@/features/kurulum/kurulumApi', () => ({ kurulumApi: { durum: vi.fn() } }))
vi.mock('./demoApi', () => ({
  demoApi: { durum: vi.fn(), yukle: vi.fn(), sifirla: vi.fn(), intikalOrnek: vi.fn() },
}))

import { atamaApi } from '@/features/atama/atamaApi'
import { belgeApi } from '@/features/belge/belgeApi'
import { kurulumApi, type KurulumDurumu } from '@/features/kurulum/kurulumApi'
import { raporApi } from '@/features/rapor/raporApi'
import { takasApi } from '@/features/takas/takasApi'
import AnaSayfa from './AnaSayfa.vue'
import { demoApi } from './demoApi'

const kurulan: Array<{ unmount: () => void }> = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

const durum = (yapilan: number, ek: Partial<KurulumDurumu> = {}): KurulumDurumu => ({
  adimlar: ['KURUM', 'TANIM', 'MAKINIST', 'PROFIL', 'SEFER'].map((kod, i) => ({
    kod: kod as KurulumDurumu['adimlar'][number]['kod'],
    ad: kod,
    yapildi: i < yapilan,
    ayrinti: '',
  })),
  tamam: yapilan === 5,
  gizli: false,
  ...ek,
})

async function kur(rol: KullaniciDto['rol']) {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/atama', '/seferler/yukle', '/belgeler', '/izin', '/takas', '/kurulum'].map(
      (path, i) => ({
        path,
        name: ['ana', 'atama', 'sefer-yukle', 'belgeler', 'izin', 'takas', 'kurulum'][i],
        component: { template: '<div />' },
      }),
    ),
  })
  const w = mount(AnaSayfa, { global: { plugins: [router] } })
  kurulan.push(w)
  await vi.dynamicImportSettled()
  await flushPromises()
  return w
}

describe('AnaSayfa: kurulum hatırlatma kartı (F1, AK-1.9)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-11',
      uretimZamani: '2026-10-11T07:00:00Z',
      satirlar: [],
    })
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '',
      bit: '',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(belgeApi.durum).mockResolvedValue({
      bugun: '2026-10-11',
      uyariGun: 30,
      makinistler: [],
    })
    vi.mocked(raporApi.izinListe).mockResolvedValue([])
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({
      bas: '',
      bit: '',
      toplam: 0,
      gorulen: 0,
      gorulmeyen: 0,
      hesapsiz: 0,
      uyari: 0,
    } as never)
    vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 0, talepler: [] })
    vi.mocked(demoApi.durum).mockRejectedValue(new ApiHata(404, 'BULUNAMADI', 'yok'))
  })

  it('yönetici + eksik adım: kart "N / 5 adım tamam" ve kuruluma bağlantı gösterir', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(durum(2))
    const w = await kur('YONETICI')
    const kart = w.get('[data-test=kurulum-karti]')
    expect(kart.text()).toContain('Kurulumu tamamlayın')
    expect(kart.text()).toContain('2 / 5 adım tamam')
    expect(kart.get('a').attributes('href')).toBe('/kurulum')
    expect(kart.attributes('aria-label')).toBe('Kurulum hatırlatması')
  })

  it('tüm adımlar tamamsa kart yok', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(durum(5))
    const w = await kur('YONETICI')
    expect(w.find('[data-test=kurulum-karti]').exists()).toBe(false)
  })

  it('yönetici hatırlatmayı gizlediyse kart yok', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(durum(1, { gizli: true }))
    const w = await kur('YONETICI')
    expect(w.find('[data-test=kurulum-karti]').exists()).toBe(false)
  })

  it('atamacıda uç hiç çağrılmaz, kart yok', async () => {
    vi.mocked(kurulumApi.durum).mockResolvedValue(durum(0))
    const w = await kur('ATAMACI')
    expect(kurulumApi.durum).not.toHaveBeenCalled()
    expect(w.find('[data-test=kurulum-karti]').exists()).toBe(false)
  })

  it('kurulum ucu hata verirse kart sessizce gizlenir; Ana Sayfa özeti etkilenmez', async () => {
    vi.mocked(kurulumApi.durum).mockRejectedValue(new ApiHata(500, 'SUNUCU_HATASI', 'x'))
    const w = await kur('YONETICI')
    expect(w.find('[data-test=kurulum-karti]').exists()).toBe(false)
    expect(w.find('[data-test=yapilacak-hata]').exists()).toBe(false)
    expect(w.get('h1').text()).toBe('Hoş geldiniz')
  })
})
