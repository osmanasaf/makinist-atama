import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./belgeApi', () => ({ belgeApi: { durum: vi.fn() } }))

import BelgePanosu from './BelgePanosu.vue'
import { belgeApi, type BelgeDurumuYaniti } from './belgeApi'

const yanit: BelgeDurumuYaniti = {
  bugun: '2026-10-10',
  uyariGun: 30,
  makinistler: [
    {
      makinistId: 1,
      sicil: 'M001',
      adSoyad: 'Işık Yılmaz',
      durum: 'AKTIF',
      enKotu: 'DOLDU',
      belgeler: [
        { tur: 'SAGLIK', ad: 'Sağlık', bitis: '2026-10-09', kalanGun: -1, durum: 'DOLDU' },
        { tur: 'EHLIYET', ad: 'Ehliyet', bitis: '2027-05-01', kalanGun: 203, durum: 'GECERLI' },
      ],
    },
    {
      makinistId: 2,
      sicil: 'M002',
      adSoyad: 'Ali Veli',
      durum: 'ASKIDA',
      enKotu: 'YAKLASIYOR',
      belgeler: [
        { tur: 'EHLIYET', ad: 'Ehliyet', bitis: '2026-10-25', kalanGun: 15, durum: 'YAKLASIYOR' },
      ],
    },
    {
      makinistId: 3,
      sicil: 'M003',
      adSoyad: 'Zeynep Kaya',
      durum: 'AKTIF',
      enKotu: 'GECERLI',
      belgeler: [
        { tur: 'EHLIYET', ad: 'Ehliyet', bitis: '2028-01-01', kalanGun: 448, durum: 'GECERLI' },
      ],
    },
  ],
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/m/:id', name: 'makinist-kart', component: { template: '<div />' } }],
  })
  const w = mount(BelgePanosu, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

const satirlar = (w: Awaited<ReturnType<typeof kur>>) => w.findAll('tbody tr')

describe('BelgePanosu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(belgeApi.durum).mockResolvedValue(yanit)
  })

  it('özet sayaçlarını enKotu durumuna göre gösterir', async () => {
    const w = await kur()
    const sayaclar = w.findAll('.sayaclar li').map((li) => li.text())
    expect(sayaclar).toEqual(['✖ Dolmuş: 1', '⚠ Yaklaşan: 1', '✔ Geçerli: 1'])
  })

  it('kalan gün metni: "15 gün kaldı" ve "1 gün geçti (DOLDU)"', async () => {
    const w = await kur()
    expect(w.text()).toContain('Ehliyet · 25.10.2026 · 15 gün kaldı')
    expect(w.text()).toContain('Sağlık · 09.10.2026 · 1 gün geçti (DOLDU)')
    // sorunlu belge rozeti: geçerli ehliyet rozet listesinde değil
    expect(w.findAll('.rozet').some((r) => r.text().includes('01.05.2027'))).toBe(false)
  })

  it('makinist adı kartına bağlantı verir', async () => {
    const w = await kur()
    expect(w.get('a').attributes('href')).toBe('/m/1')
  })

  it('durum süzgeci yalnız o enKotu durumundakileri bırakır', async () => {
    const w = await kur()
    expect(satirlar(w)).toHaveLength(3)
    await w.findAll('.suzgec button')[2]!.trigger('click') // Yaklaşan
    expect(satirlar(w)).toHaveLength(1)
    expect(w.text()).toContain('Ali Veli')
    await w.findAll('.suzgec button')[3]!.trigger('click') // Girilmemiş
    expect(w.text()).toContain('Kayıt bulunamadı.')
  })

  it('arama sicil/ad üzerinde Türkçe büyük-küçük harf duyarsızdır', async () => {
    const w = await kur()
    await w.get('#belge-arama').setValue('IŞIK') // I → ı (tr-TR) ile "Işık" eşleşir
    expect(satirlar(w)).toHaveLength(1)
    expect(w.text()).toContain('M001')
    await w.get('#belge-arama').setValue('m003')
    expect(w.text()).toContain('Zeynep Kaya')
    expect(w.text()).not.toContain('Ali Veli')
    await w.get('#belge-arama').setValue('yok')
    expect(w.text()).toContain('Kayıt bulunamadı.')
  })
})
