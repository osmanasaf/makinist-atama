import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/tanim/tanimApi', () => ({ tanimApi: { liste: vi.fn() } }))
vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: { getir: vi.fn(), olustur: vi.fn(), guncelle: vi.fn(), izinEkle: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import { tanimApi } from '@/features/tanim/tanimApi'
import MakinistKarti from './MakinistKarti.vue'
import { makinistApi } from './makinistApi'

// QA (M1): eksik belge (sağlık / psikoteknik / ehliyet) durumunda arayüz davranışı.
async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/makinistler', name: 'makinistler', component: { template: '<div />' } },
      { path: '/makinistler/yeni', name: 'makinist-yeni', component: MakinistKarti },
      { path: '/makinistler/:id(\\d+)', name: 'makinist-kart', component: MakinistKarti },
    ],
  })
  await router.push('/makinistler/yeni')
  await router.isReady()
  const w = mount(MakinistKarti, { global: { plugins: [router] } })
  await flushPromises()
  await w.get('#sicil').setValue('QA101')
  await w.get('#adSoyad').setValue('QA Makinist')
  await w.get('#bagliGar').setValue('1')
  return w
}

// Gerçek sunucu yanıtı (curl ile alındı): üç belge tarihi de boşken alanlar haritası.
const eksikBelgeHatasi = new ApiHata(
  400,
  'DOGRULAMA',
  'Girilen bilgilerde hata var. Alanları kontrol edin.',
  {
    alanlar: {
      ehliyetBitis: 'Ehliyet bitiş tarihi gerekli.',
      psikoteknikBitis: 'Psikoteknik bitiş tarihi gerekli.',
      saglikBitis: 'Sağlık raporu bitiş tarihi gerekli.',
    },
  },
)

describe('MakinistKarti (QA eksik belge)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockImplementation(async (tip) =>
      tip === 'GAR' ? [{ id: 1, tip, ad: 'Ankara' }] : [],
    )
    vi.mocked(makinistApi.olustur).mockRejectedValue(eksikBelgeHatasi)
  })

  it('ehliyet bitişi eksikse alanın altında sunucu mesajı görünür', async () => {
    const w = await kur()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Ehliyet bitiş tarihi gerekli.')
  })

  // BUG-1 (QA M1): sağlık ve psikoteknik alanlarında hata satırı yoktu; yalnız genel bant görünüyordu.
  it('BUG-1: sağlık ve psikoteknik bitişi eksikse her alanın altında mesaj görünür', async () => {
    const w = await kur()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Sağlık raporu bitiş tarihi gerekli.')
    expect(w.text()).toContain('Psikoteknik bitiş tarihi gerekli.')
  })

  // BUG-2 (QA M1): yeni -> kart rotası aynı bileşen; kayıttan sonra sunucudaki kayıt (büyük harf sicil, geçerlilik bitişi) forma yazılmalı.
  it('BUG-2: kayıttan sonra form sunucudaki kaydı gösterir', async () => {
    vi.mocked(makinistApi.olustur).mockResolvedValue({
      id: 42,
      sicil: 'QA101',
      adSoyad: 'QA Makinist',
      bagliGarId: 1,
      bagliGar: 'Ankara',
      durum: 'AKTIF',
      ehliyetBitis: '2030-01-01',
      saglikBitis: '2027-06-01',
      psikoteknikBitis: '2028-01-01',
      sonSurus: null,
      versiyon: 0,
      broveAracTipleri: [
        {
          aracTipiId: 7,
          aracTipi: 'E68000',
          sinavTarihi: '2022-03-10',
          gecerlilikBitis: '2027-03-10',
        },
      ],
      broveHatlar: [],
      izinler: [],
    })
    const w = await kur()
    await w.get('#ehliyetBitis').setValue('2030-01-01')
    await w.get('#saglikBitis').setValue('2027-06-01')
    await w.get('#psikoteknikBitis').setValue('2028-01-01')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect((w.get('#sicil').element as HTMLInputElement).value).toBe('QA101')
    expect(w.text()).toContain('Geçerlilik bitişi: 10.03.2027')
    expect(w.text()).toContain('Kaydedildi.')
  })
})
