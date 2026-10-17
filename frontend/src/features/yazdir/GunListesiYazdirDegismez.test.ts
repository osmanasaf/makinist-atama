import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))

import { kullanici } from '@/composables/oturum'
import { atamaApi, type GunListesi } from '@/features/atama/atamaApi'
import { makinist, satir } from '@/features/atama/testVerisi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const bos = { template: '<div />' }

/** NFR-M07 / T7.3: panoya asılan liste "gördüm" bilgisinden etkilenmez: aynı gün listesinin yazdırma çıktısı `gordu` alanları olsa da olmasa da karakter karakter aynıdır. */
const temel = (): GunListesi => ({
  tarih: '2026-10-12',
  uretimZamani: '2026-10-12T07:15:00Z',
  satirlar: [
    satir(1, { makinistler: [makinist()] }),
    satir(2, {
      makinistler: [
        makinist({ atamaId: 11, makinistId: 2, sicil: 'D002', ad: 'Veli Kaya', rol: 'YARDIMCI' }),
      ],
    }),
    satir(3, {
      kirmizi: true,
      makinistler: [
        makinist({
          atamaId: 12,
          makinistId: 3,
          sicil: 'D003',
          ad: 'Kırmızı',
          kirmizi: true,
          kirmiziNedenler: ['Dinlenme 9 s < 11 s'],
        }),
      ],
    }),
    satir(4),
    satir(5, { durum: 'IPTAL', makinistler: [] }),
  ],
})

/** Aynı liste; her görevliye sunucunun ek `gordu` alanları eklenmiş. */
function gorduluListe(): GunListesi {
  const g = temel()
  const durumlar = ['GORDU', 'GORMEDI', 'HESAP_YOK'] as const
  let i = 0
  for (const s of g.satirlar) {
    for (const m of s.makinistler) {
      m.gordu = durumlar[i++ % 3]
      m.gorduZamani = m.gordu === 'GORDU' ? '2026-10-12T06:14:00Z' : null
      m.gorduUyari = m.gordu === 'GORMEDI'
    }
  }
  return g
}

async function yazdirHtml(liste: GunListesi): Promise<string> {
  vi.mocked(atamaApi.gun).mockResolvedValue(liste)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/atama', name: 'atama', component: bos }],
  })
  await router.push('/atama')
  const w = mount(GunListesiYazdir, {
    props: { tarih: '2026-10-12' },
    global: { plugins: [router] },
  })
  await flushPromises()
  return w.html()
}

describe('GunListesiYazdir: pano çıktısı gördüm bilgisinden etkilenmez', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('gordu alanlı ve alansız yanıt: aynı HTML, "gördü / görmedi / hesap yok" metni yok', async () => {
    const eski = await yazdirHtml(temel())
    const yeni = await yazdirHtml(gorduluListe())

    expect(eski.length).toBeGreaterThan(500) // gerçekten çizildi
    expect(yeni).toBe(eski)
    expect(yeni.toLowerCase()).not.toMatch(/gördü|görmedi|hesap yok/)
  })
})
