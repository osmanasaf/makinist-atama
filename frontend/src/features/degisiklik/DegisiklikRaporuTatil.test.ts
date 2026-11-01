import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./degisiklikApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./degisiklikApi')>()),
  degisiklikApi: { rapor: vi.fn(), excel: vi.fn() },
}))

import DegisiklikRaporu from './DegisiklikRaporu.vue'
import { degisiklikApi, TUR_ETIKETLERI } from './degisiklikApi'

// Tur 4'ten açık kalan (F5, MK-178): resmi tatil kayıtları (TATIL_EKLE / TATIL_SIL) ekran süzgecinde seçilebilir.
describe('DegisiklikRaporu: resmi tatil türleri süzgeçte', () => {
  beforeEach(() => vi.resetAllMocks())

  it('tür seçicide "Resmi tatil eklendi" ve "Resmi tatil silindi" var; seçilince sunucuya o tür gider', async () => {
    vi.mocked(degisiklikApi.rapor).mockResolvedValue({
      uretimZamani: '2026-10-12T07:20:00Z',
      ureten: 'Mehmet Demir',
      filtre: { bas: '2026-10-06', bit: '2026-10-12' },
      kayitSayisi: 0,
      bolumler: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'x', component: { template: '<div />' } },
        {
          path: '/yazdir/degisiklik',
          name: 'yazdir-degisiklik',
          component: { template: '<div />' },
        },
      ],
    })
    await router.push('/')
    const w = mount(DegisiklikRaporu, { global: { plugins: [router] } })
    await flushPromises()
    expect(TUR_ETIKETLERI.TATIL_EKLE).toBe('Resmi tatil eklendi')
    expect(TUR_ETIKETLERI.TATIL_SIL).toBe('Resmi tatil silindi')
    const secenekler = w.findAll('option').map((o) => [o.attributes('value'), o.text()])
    expect(secenekler).toContainEqual(['TATIL_EKLE', 'Resmi tatil eklendi'])
    expect(secenekler).toContainEqual(['TATIL_SIL', 'Resmi tatil silindi'])
    await w.get('select').setValue('TATIL_SIL')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(degisiklikApi.rapor).mock.lastCall?.[0]).toMatchObject({ tur: 'TATIL_SIL' })
  })
})
