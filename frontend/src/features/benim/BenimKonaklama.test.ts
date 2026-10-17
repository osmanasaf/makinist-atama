import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./benimApi', () => ({ benimApi: { konaklama: vi.fn() } }))

import BenimKonaklama from './BenimKonaklama.vue'
import { benimApi } from './benimApi'

describe('BenimKonaklama (F2b OK-10)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('kendi konaklamaları: gar, yer tipi, giriş–çıkış ve gece; not alanı yok', async () => {
    vi.mocked(benimApi.konaklama).mockResolvedValue([
      {
        id: 1,
        gar: 'Sivas',
        yerTipi: 'OTEL',
        giris: '2026-10-14T11:30:00+03:00',
        cikis: '2026-10-15T06:00:00+03:00',
        gece: 1,
      },
    ])
    const w = mount(BenimKonaklama)
    await flushPromises()

    const kart = w.get('[data-test="konaklama"]')
    expect(kart.text()).toContain('Sivas')
    expect(kart.text()).toContain('Otel')
    expect(kart.text()).toContain('1 gece')
    expect(w.find('[data-test="bos"]').exists()).toBe(false)
  })

  it('kayıt yoksa açık bir mesaj; hata gösterilir', async () => {
    vi.mocked(benimApi.konaklama).mockResolvedValue([])
    const bos = mount(BenimKonaklama)
    await flushPromises()
    expect(bos.get('[data-test="bos"]').text()).toContain('konaklama kaydınız yok')

    vi.mocked(benimApi.konaklama).mockRejectedValue(new Error('x'))
    const hatali = mount(BenimKonaklama)
    await flushPromises()
    expect(hatali.find('[role="alert"]').exists()).toBe(true)
  })
})
