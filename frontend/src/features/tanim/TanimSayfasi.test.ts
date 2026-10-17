import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./tanimApi')>()),
  tanimApi: {
    liste: vi.fn(),
    ekle: vi.fn(),
    adlandir: vi.fn(),
    intikalSureleri: vi.fn(),
    intikalSuresiYaz: vi.fn(),
    intikalSuresiSil: vi.fn(),
  },
}))

import { kullanici } from '@/composables/oturum'
import type { Rol } from '@/api/tipler'
import TanimSayfasi from './TanimSayfasi.vue'
import { tanimApi } from './tanimApi'

function girisYap(rol: Rol) {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
}

async function kur() {
  const w = mount(TanimSayfasi)
  await flushPromises()
  return w
}

describe('TanimSayfasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'İzmir' },
    ])
    vi.mocked(tanimApi.intikalSureleri).mockResolvedValue([])
  })

  it('tanım listesini gösterir', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    expect(tanimApi.liste).toHaveBeenCalledWith('GAR')
    expect(w.text()).toContain('Ankara')
    expect(w.text()).toContain('İzmir')
  })

  it('ATAMACI için ekle ve yeniden adlandır düğmesi yoktur', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    expect(w.text()).not.toContain('Ekle')
    expect(w.text()).not.toContain('Yeniden adlandır')
  })

  it('YONETICI ekleyebilir', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.ekle).mockResolvedValue({ id: 3, tip: 'GAR', ad: 'Konya' })
    const w = await kur()
    await w.get('#yeni-ad').setValue(' Konya ')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(tanimApi.ekle).toHaveBeenCalledWith('GAR', 'Konya')
  })

  it('sekme değişince o tipin listesi istenir', async () => {
    girisYap('YONETICI')
    const w = await kur()
    await w.findAll('[role=tab]')[1]!.trigger('click')
    await flushPromises()
    expect(tanimApi.liste).toHaveBeenLastCalledWith('HAT')
  })
})
