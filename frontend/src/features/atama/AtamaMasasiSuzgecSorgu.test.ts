import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    kirmiziSayisi: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }

function gunVerisi(): GunListesi {
  return {
    tarih: '2026-10-12',
    uretimZamani: '2026-10-12T07:00:00Z',
    satirlar: [
      satir(1, { makinistler: [makinist()] }),
      satir(2, { kirmizi: true, makinistler: [makinist({ kirmizi: true })] }),
      satir(3), // BOŞ
    ],
  }
}

async function kur(sorgu: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push(`/atama${sorgu}`)
  const w = mount(AtamaMasasi, { global: { plugins: [router] } })
  await flushPromises()
  return { w, router }
}
const satirSayisi = (w: Awaited<ReturnType<typeof kur>>['w']) => w.findAll('tr.sefer-satiri').length

describe('AtamaMasasi: ?suzgec= derin bağlantısı (AK-6.2)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(gunVerisi()))
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 1,
      kirmiziAtama: 1,
      ilkKirmiziGun: '2026-10-12',
    })
  })

  it('suzgec=kirmizi ile açılınca yalnız kırmızı satır ve çip basılı', async () => {
    const { w } = await kur('?tarih=2026-10-12&suzgec=kirmizi')
    expect(satirSayisi(w)).toBe(1)
    expect(w.get('[data-test="kirmizi-sayac"]').attributes('aria-pressed')).toBe('true')
    expect(w.get('[data-test="atanmamis-sayac"]').attributes('aria-pressed')).toBe('false')
  })

  it('suzgec=atanmamis ile açılınca yalnız boş satır', async () => {
    const { w } = await kur('?tarih=2026-10-12&suzgec=atanmamis')
    expect(satirSayisi(w)).toBe(1)
    expect(w.get('[data-test="atanmamis-sayac"]').attributes('aria-pressed')).toBe('true')
  })

  it('D1/QA-7: suzgec=bitmeyen ile açılınca yalnız henüz bitmeyen boş seferler (pano sayısı = liste); Atanmamış çipi basılı, not + "Bitenleri de göster"', async () => {
    const g = gunVerisi()
    g.satirlar = [
      satir(1, { makinistler: [makinist()], guncelVaris: '2999-01-01T07:00:00Z' }), // atanmış
      satir(2, { guncelVaris: '2999-01-01T07:00:00Z' }), // boş, bitmedi
      satir(3), // boş, bitmiş (2026-10-12)
      satir(4, { guncelVaris: '2999-01-01T07:00:00Z', durum: 'IPTAL' }), // iptal: sayılmaz
    ]
    vi.mocked(atamaApi.gun).mockImplementation(() => Promise.resolve(structuredClone(g)))
    const { w } = await kur('?tarih=2026-10-12&suzgec=bitmeyen')
    expect(w.findAll('tr.sefer-satiri').map((r) => r.text())).toHaveLength(1)
    expect(w.get('tr.sefer-satiri').text()).toContain('T1002')
    expect(w.get('[data-test="atanmamis-sayac"]').attributes('aria-pressed')).toBe('true')
    expect(w.get('[data-test="bitmeyen-notu"]').text()).toContain('henüz bitmeyen')
    await w.get('[data-test="bitenleri-goster"]').trigger('click')
    expect(satirSayisi(w)).toBe(2) // T1002 + bitmiş T1003
    expect(w.find('[data-test="bitmeyen-notu"]').exists()).toBe(false)
    expect(w.get('[data-test="atanmamis-sayac"]').attributes('aria-pressed')).toBe('true')
  })

  it('geçersiz suzgec yok sayılır: tüm satırlar, hiçbir çip basılı değil', async () => {
    const { w } = await kur('?tarih=2026-10-12&suzgec=hepsi')
    expect(satirSayisi(w)).toBe(3)
    expect(w.get('[data-test="kirmizi-sayac"]').attributes('aria-pressed')).toBe('false')
  })

  it('rota sorgusu değişince süzgeç güncellenir; çip basışı sorguyu yeniden yazmaz', async () => {
    const { w, router } = await kur('?tarih=2026-10-12')
    expect(satirSayisi(w)).toBe(3)
    await router.push('/atama?tarih=2026-10-12&suzgec=kirmizi')
    await flushPromises()
    expect(satirSayisi(w)).toBe(1)
    await w.get('[data-test="kirmizi-sayac"]').trigger('click') // süzgeci kapatır
    expect(satirSayisi(w)).toBe(3)
    expect(router.currentRoute.value.query.suzgec).toBe('kirmizi') // sorgu aynen
  })
})
