import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kullanici } from '@/composables/oturum'

vi.mock('@/features/atama/atamaApi', () => ({
  atamaApi: { gun: vi.fn(), kirmiziSayisi: vi.fn() },
}))
vi.mock('@/features/belge/belgeApi', () => ({ belgeApi: { durum: vi.fn() } }))
vi.mock('@/features/rapor/raporApi', () => ({ raporApi: { izinListe: vi.fn() } }))
vi.mock('./demoApi', () => ({
  demoApi: { durum: vi.fn(), yukle: vi.fn(), sifirla: vi.fn(), intikalOrnek: vi.fn() },
}))

import { atamaApi } from '@/features/atama/atamaApi'
import { belgeApi } from '@/features/belge/belgeApi'
import { raporApi } from '@/features/rapor/raporApi'
import AnaSayfa from './AnaSayfa.vue'
import { demoApi } from './demoApi'

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/atama', '/seferler/yukle', '/belgeler', '/izin'].map((path, i) => ({
      path,
      name: ['ana', 'atama', 'sefer-yukle', 'belgeler', 'izin'][i],
      component: { template: '<div />' },
    })),
  })
  const w = mount(AnaSayfa, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

const sayac = (ek: Record<string, number> = {}) => ({
  bas: '2026-10-11',
  bit: '2026-10-18',
  kirmiziSefer: 0,
  kirmiziAtama: 0,
  ...ek,
})

describe('AnaSayfa: serbest intikal (OK-28)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = {
      id: 1,
      eposta: 'a@b.c',
      adSoyad: 'Yön Etici',
      rol: 'YONETICI',
      aktif: true,
    }
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-11',
      uretimZamani: '2026-10-11T07:00:00Z',
      satirlar: [],
    })
    vi.mocked(belgeApi.durum).mockResolvedValue({
      bugun: '2026-10-11',
      uyariGun: 30,
      makinistler: [],
    })
    vi.mocked(raporApi.izinListe).mockResolvedValue([])
    vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: true })
  })

  it('kırmızı intikal sayısı atama sayacından AYRI satırda görünür', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(
      sayac({ kirmiziSefer: 1, kirmiziAtama: 2, kirmiziIntikal: 3 }),
    )
    const w = await kur()

    expect(w.get('[data-test=kart-kirmizi]').text()).toContain('2 atama')
    expect(w.get('[data-test=kirmizi-intikal]').text()).toContain('3 kırmızı intikal')
  })

  it('kırmızı intikal yoksa (ya da eski yanıtta alan yoksa) satır çıkmaz', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(sayac())
    const w = await kur()

    expect(w.find('[data-test=kirmizi-intikal]').exists()).toBe(false)
  })

  it('demo: "İntikal örneği ekle" uçla konuşur; ikinci çağrının 409 hatası gösterilir', async () => {
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue(sayac())
    vi.mocked(demoApi.intikalOrnek).mockResolvedValueOnce({ sure: 3, intikal: 1, sicil: 'D031' })
    vi.mocked(demoApi.intikalOrnek).mockRejectedValueOnce(
      new ApiHata(409, 'DEMO_INTIKAL_VAR', 'İntikal örneği zaten var.'),
    )
    const w = await kur()

    await w.get('[data-test=demo-intikal]').trigger('click')
    await flushPromises()
    expect(w.get('[role=status]').text()).toContain('3 gar çifti süresi')
    expect(w.get('[role=status]').text()).toContain('D031')

    await w.get('[data-test=demo-intikal]').trigger('click')
    await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('zaten var')
  })
})
