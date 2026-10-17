import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))

import { atamaApi, type GunListesi } from '@/features/atama/atamaApi'
import { makinist, satir } from '@/features/atama/testVerisi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const bos = { template: '<div />' }

const liste = (tarih: string, tren: string): GunListesi => ({
  tarih,
  uretimZamani: `${tarih}T07:00:00Z`,
  satirlar: [satir(1, { trenNo: tren, makinistler: [makinist()] })],
})

async function kur(tarih: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/atama', name: 'atama', component: bos }],
  })
  await router.push('/atama')
  const w = mount(GunListesiYazdir, { props: { tarih }, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('GunListesiYazdir: adresteki tarih değişince', () => {
  beforeEach(() => vi.resetAllMocks())

  it('yeni günün listesi yeniden yüklenir ve başlık yeni tarihi gösterir', async () => {
    vi.mocked(atamaApi.gun).mockImplementation(async (t: string) =>
      t === '2026-10-12' ? liste(t, 'GUN12') : liste(t, 'GUN13'),
    )
    const w = await kur('2026-10-12')
    expect(w.text()).toContain('GUN12')
    expect(w.text()).toContain('Tarih: 12.10.2026')

    await w.setProps({ tarih: '2026-10-13' })
    await flushPromises()

    expect(atamaApi.gun).toHaveBeenLastCalledWith('2026-10-13')
    expect(w.text()).toContain('GUN13')
    expect(w.text()).not.toContain('GUN12')
    expect(w.text()).toContain('Tarih: 13.10.2026')
  })

  it('geç dönen eski günün yanıtı yeni günü ezmez', async () => {
    let eskiyiBitir!: (g: GunListesi) => void
    vi.mocked(atamaApi.gun).mockImplementation((t: string) =>
      t === '2026-10-12'
        ? new Promise((c) => (eskiyiBitir = c))
        : Promise.resolve(liste(t, 'GUN13')),
    )
    const w = mount(GunListesiYazdir, {
      props: { tarih: '2026-10-12' },
      global: {
        plugins: [
          createRouter({
            history: createMemoryHistory(),
            routes: [{ path: '/', name: 'atama', component: bos }],
          }),
        ],
      },
    })
    await w.setProps({ tarih: '2026-10-13' })
    await flushPromises()
    eskiyiBitir(liste('2026-10-12', 'GUN12'))
    await flushPromises()

    expect(w.text()).toContain('GUN13')
    expect(w.text()).not.toContain('GUN12')
  })
})
