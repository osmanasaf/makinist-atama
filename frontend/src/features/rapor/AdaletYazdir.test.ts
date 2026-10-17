import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { adalet: vi.fn() },
}))

import { kullanici } from '@/composables/oturum'
import AdaletYazdir from './AdaletYazdir.vue'
import { raporApi } from './raporApi'
import routes from './routes'
import { adaletYaniti } from './testVerisi'

const bos = { template: '<div />' }

async function kur(props: Record<string, string> = { ay: '2026-10' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/rapor/adalet', name: 'rapor-adalet', component: bos }],
  })
  await router.push('/rapor/adalet')
  const w = mount(AdaletYazdir, { props, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('AdaletYazdir', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(raporApi.adalet).mockResolvedValue(adaletYaniti)
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('ay ile raporu ister; başlık, süzgeç, yazdırma tablosu ve altbilgi (sunucu zamanı)', async () => {
    const w = await kur()
    expect(raporApi.adalet).toHaveBeenCalledWith('2026-10')
    expect(w.get('h1').text()).toBe('Adalet raporu')
    expect(w.get('.yazdir-filtre').text()).toBe('Ay: 2026-10')
    expect(w.get('table').classes()).toContain('yazdir-tablo')
    expect(w.text()).toContain('⚠ 0 saat')
    const alt = w.get('footer').text().replace(/\s+/g, ' ')
    expect(alt).toContain('Son güncelleme: 12.10.2026 10:20')
    expect(alt).toContain('Kayıt sayısı: 3')
    expect(alt).toContain('Yazdıran: Ayşe Kaya')
    expect(w.get('.no-print button').attributes('disabled')).toBeUndefined()
  })

  it('ay eksikse istek atılmaz; hatada Yazdır kapalı', async () => {
    const w = await kur({})
    expect(raporApi.adalet).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toContain('Ay')
    expect(w.get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(raporApi.adalet).mockRejectedValue(new ApiHata(400, 'DOGRULAMA', 'Geçersiz ay.'))
    const h = await kur()
    expect(h.get('[role="alert"]').text()).toBe('Geçersiz ay.')
    expect(h.get('.no-print button').attributes('disabled')).toBeDefined()
  })

  it('rota: /yazdir/adalet?ay= yazdır işaretli, ay özelliğe çevrilir', () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    const r = router.resolve('/yazdir/adalet?ay=2026-10')
    expect(r.name).toBe('yazdir-adalet')
    expect(r.meta.yazdir).toBe(true)
    const props = r.matched[0]!.props.default as (r: unknown) => Record<string, unknown>
    expect(props(r)).toEqual({ ay: '2026-10' })
    expect(router.resolve('/rapor/yedek').name).toBe('rapor-yedek')
    expect(router.resolve('/izin').name).toBe('izin')
    expect(router.resolve('/rapor/adalet').meta.menu).toEqual({ ad: 'Adalet Raporu', sira: 42 })
  })
})
