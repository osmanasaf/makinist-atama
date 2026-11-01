import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./parametreApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./parametreApi')>()),
  parametreApi: {
    parametreler: vi.fn(),
    kurallar: vi.fn(),
    ayarlar: vi.fn(),
    parametreKaydet: vi.fn(),
    kuralKaydet: vi.fn(),
    ayarKaydet: vi.fn(),
  },
}))

import { kullanici } from '@/composables/oturum'
import AyarlarSayfasi from './AyarlarSayfasi.vue'
import { GRUPLAR, parametreApi, type AyarDto } from './parametreApi'

/** V35 / MK-173: 12 ayar (grup MARJ). */
const MARJLAR: [string, string, number, string, number, number][] = [
  ['marj.aktif', 'Sınıra yakınlık işareti', 1, '0/1', 0, 1],
  ['marj.gunluk_dk', 'Günlük süre sınırına yakınlık', 60, 'dk', 0, 720],
  ['marj.gece_dk', 'Gece nöbeti süresi sınırına yakınlık', 30, 'dk', 0, 240],
  ['marj.dinlenme_dk', 'Dinlenme sınırına yakınlık', 60, 'dk', 0, 720],
  ['marj.hafta_tatili_dk', 'Hafta tatili sınırına yakınlık', 120, 'dk', 0, 1440],
  ['marj.yillik_saat', 'Yıllık fazla mesai sınırına yakınlık', 20, 'saat', 0, 270],
  ['marj.aktarma_dk', 'Aktarma süresi sınırına yakınlık', 10, 'dk', 0, 120],
  ['marj.gece_hafta', 'Ardışık gece haftası sınırına yakınlık', 0, 'hafta', 0, 3],
  ['yorgunluk.gun', 'Yorgunluk göstergesi penceresi', 7, 'gün', 1, 14],
  ['yorgunluk.hafta_saat', 'Yorgunluk: pencere çalışma süresi vurgu eşiği', 40, 'saat', 1, 168],
  ['yorgunluk.gece_adet', 'Yorgunluk: gece nöbeti vurgu eşiği', 3, 'adet', 1, 14],
  ['yorgunluk.dinlenme_saat', 'Yorgunluk: en kısa dinlenme vurgu eşiği', 12, 'saat', 1, 72],
]
const marjAyarlari: AyarDto[] = MARJLAR.map(([anahtar, ad, deger, birim, alt, ust], i) => ({
  anahtar,
  ad,
  deger,
  birim,
  alt,
  ust,
  aciklama: 'Bilgi amaçlı eşik. [V] kaynaksız yer tutucu',
  varsayilan: deger,
  grup: 'MARJ',
  sira: (i + 1) * 10,
}))
const diger: AyarDto = {
  anahtar: 'gordum.kalem_gun',
  ad: 'Gördüm kalemi süresi',
  deger: 7,
  birim: 'gün',
  alt: 1,
  ust: 60,
  aciklama: 'Gördüm kalemleri',
  varsayilan: 7,
  grup: 'MAKINIST',
  sira: 30,
}

async function kur() {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'YONETICI', aktif: true }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ozel-kurallar', name: 'ozel-kurallar', component: { template: '<div />' } }],
  })
  const w = mount(AyarlarSayfasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('AyarlarSayfasi: Sınıra yakınlık ve yorgunluk grubu (F3, MK-173)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(parametreApi.kurallar).mockResolvedValue([])
    vi.mocked(parametreApi.parametreler).mockResolvedValue([])
    vi.mocked(parametreApi.ayarlar).mockResolvedValue([diger, ...marjAyarlari])
  })

  it('GRUPLAR listesinde MARJ başlığı "Sınıra yakınlık ve yorgunluk (bilgi)", makinist görünümünden sonra', () => {
    const kodlar = GRUPLAR.map((g) => g.kod)
    expect(GRUPLAR.find((g) => g.kod === 'MARJ')?.baslik).toBe(
      'Sınıra yakınlık ve yorgunluk (bilgi)',
    )
    expect(kodlar.indexOf('MARJ')).toBeGreaterThan(kodlar.indexOf('MAKINIST'))
  })

  it('grup kendi başlığıyla görünür, üst notu "bilgi amaçlıdır; engel/uyarı üretmez; bilimsel model değildir" der', async () => {
    const w = await kur()
    const baslik = w.findAll('h2').find((h) => h.text() === 'Sınıra yakınlık ve yorgunluk (bilgi)')
    expect(baslik).toBeTruthy()
    const not = w.get('[data-test="grup-notu-marj"]')
    expect(not.text()).toBe(
      'Bilgi amaçlıdır; engel ya da uyarı üretmez. Yorgunluk göstergesi bilimsel bir model değildir.',
    )
    // not başlığın hemen altında, aynı bölümde
    expect(baslik!.element.parentElement).toBe(not.element.parentElement)
    expect(w.findAll('[data-test="grup-notu-marj"]')).toHaveLength(1) // diğer gruplarda not yok
  })

  it('12 satır katalogdan otomatik: her birinin adı ve değeri görünür; marj.aktif onay kutusu', async () => {
    const w = await kur()
    const bolum = w.get('section[aria-labelledby="grup-MARJ"]')
    expect(bolum.findAll('tbody tr')).toHaveLength(12)
    for (const [, ad] of MARJLAR) expect(bolum.text()).toContain(ad)
    expect(bolum.get('input[aria-label="Sınıra yakınlık işareti değeri"]').attributes('type')).toBe(
      'checkbox',
    )
    expect(
      w.get('input[aria-label="Günlük süre sınırına yakınlık değeri"]').element,
    ).toHaveProperty('value', '60')
  })

  it('arama MARJ satırlarını bulur', async () => {
    const w = await kur()
    await w.get('input[type="search"]').setValue('yorgunluk')
    await flushPromises()
    const bolum = w.get('section[aria-labelledby="grup-MARJ"]')
    expect(bolum.findAll('tbody tr')).toHaveLength(4)
    expect(w.find('section[aria-labelledby="grup-MAKINIST"]').exists()).toBe(false)
  })
})
