import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./degisiklikApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./degisiklikApi')>()),
  degisiklikApi: { rapor: vi.fn(), excel: vi.fn() },
}))

import DegisiklikRaporu from './DegisiklikRaporu.vue'
import { degisiklikApi, type DegisiklikYaniti } from './degisiklikApi'

const bos = { template: '<div />' }

const gordumYaniti: DegisiklikYaniti = {
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  filtre: { bas: '2026-10-06', bit: '2026-10-12', tur: 'GORDUM' },
  kayitSayisi: 1,
  bolumler: [
    {
      kod: 'GORDUM',
      ad: 'Gördüm kayıtları (tebliğ kanıtı)',
      sayi: 1,
      satirlar: [
        {
          id: 1,
          zaman: '2026-10-12T06:14:00Z',
          kim: 'Demo Makinist D001',
          tur: 'GORDUM',
          turAdi: 'Görev görüldü (gördüm)',
          sicil: 'D001',
          trenNo: 'T1',
          seferTarihi: '2026-10-13',
          eski: null,
          yeni: 'Asıl · Ankara 13.10 08:00 → Sivas 13.10 10:00',
          neden: null,
          nedenNotu: null,
          uyarili: false,
          uyariGerekcesi: null,
          uyarilar: [],
          otomatik: false,
        },
      ],
    },
  ],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/degisiklik', name: 'degisiklik', component: bos },
      { path: '/yazdir/degisiklik', name: 'yazdir-degisiklik', component: bos },
    ],
  })
  await router.push('/degisiklik')
  const w = mount(DegisiklikRaporu, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('DegisiklikRaporu: gördüm kayıtları', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(degisiklikApi.rapor).mockResolvedValue(gordumYaniti)
  })

  it('varsayılan süzgeç GORDUM istemez; onay kutusu tür seçiciyi kapatır ve tur=GORDUM gönderir', async () => {
    const w = await kur()
    expect(w.get('#rapor-gordum').element).toHaveProperty('checked', false)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(degisiklikApi.rapor).mock.calls[0]![0].tur).toBe('')

    await w.get('#rapor-gordum').setValue(true)
    expect(w.get('#rapor-tur').attributes('disabled')).toBeDefined()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(vi.mocked(degisiklikApi.rapor).mock.calls[1]![0].tur).toBe('GORDUM')
  })

  it('GORDUM bölümü ayrı başlıkla çizilir; satırda yalnız yeni özet, süzgeç özeti "Gördüm" der', async () => {
    const w = await kur()
    await w.get('#rapor-gordum').setValue(true)
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(w.text()).toContain('Gördüm kayıtları (tebliğ kanıtı) (1)')
    expect(w.text()).toContain('Demo Makinist D001')
    expect(w.text()).toContain('Asıl · Ankara 13.10 08:00 → Sivas 13.10 10:00')
    expect(w.text()).not.toContain('— →')
    expect(w.get('[data-test=ozet]').text()).toContain('Tür: Gördüm (tebliğ kanıtı)')
  })
})
