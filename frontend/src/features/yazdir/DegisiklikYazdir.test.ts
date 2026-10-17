import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('@/features/degisiklik/degisiklikApi', async (orijinal) => ({
  ...(await orijinal<typeof import('@/features/degisiklik/degisiklikApi')>()),
  degisiklikApi: { rapor: vi.fn() },
}))

import { kullanici } from '@/composables/oturum'
import { degisiklikApi, type DegisiklikYaniti } from '@/features/degisiklik/degisiklikApi'
import DegisiklikYazdir from './DegisiklikYazdir.vue'

const bos = { template: '<div />' }

const yanit: DegisiklikYaniti = {
  uretimZamani: '2026-10-12T07:20:00Z',
  ureten: 'Mehmet Demir',
  filtre: { bas: '2026-10-06', bit: '2026-10-12', kullanici: 'ayse' },
  kayitSayisi: 1,
  bolumler: [
    {
      kod: 'ATAMA',
      ad: 'Atamalar',
      sayi: 1,
      satirlar: [
        {
          id: 1,
          zaman: '2026-10-12T07:15:00Z',
          kim: 'Ayşe Kaya',
          tur: 'ATAMA',
          turAdi: 'Atama',
          sicil: 'D001',
          trenNo: 'T1001',
          seferTarihi: '2026-10-13',
          eski: null,
          yeni: 'Ali Veli (D001)',
          neden: null,
          nedenNotu: null,
          uyarili: true,
          uyariGerekcesi: 'Amir onayı',
          uyarilar: ['Ö-002: Görev sayısı: 4 > 3 (Kural: Gün sınırı)'],
          otomatik: false,
        },
      ],
    },
    { kod: 'SEFER_IPTAL', ad: 'Sefer iptalleri', sayi: 0, satirlar: [] },
  ],
}

async function kur(props: Record<string, string> = { bas: '2026-10-06', bit: '2026-10-12' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/degisiklik', name: 'degisiklik', component: bos }],
  })
  await router.push('/degisiklik')
  const w = mount(DegisiklikYazdir, { props, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('DegisiklikYazdir', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(degisiklikApi.rapor).mockResolvedValue(yanit)
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('sorgudan gelen süzgeçle raporu ister; başlık, süzgeç özeti ve altbilgi (sunucu zamanı, kayıt sayısı)', async () => {
    const w = await kur({ bas: '2026-10-06', bit: '2026-10-12', kullanici: 'ayse', tur: 'ATAMA' })
    expect(degisiklikApi.rapor).toHaveBeenCalledWith({
      bas: '2026-10-06',
      bit: '2026-10-12',
      kullanici: 'ayse',
      tur: 'ATAMA',
      trenNo: undefined,
    })
    expect(w.get('h1').text()).toBe('Değişiklik (denetim) raporu')
    expect(w.get('.yazdir-filtre').text()).toBe('Tarih: 06.10.2026 – 12.10.2026 · Kullanıcı: ayse')
    const alt = w.get('footer').text().replace(/\s+/g, ' ')
    expect(alt).toContain('Son güncelleme: 12.10.2026 10:20')
    expect(alt).toContain('Kayıt sayısı: 1')
    expect(alt).toContain('Yazdıran: Ayşe Kaya')
  })

  it('bölümler renksiz okunur: uyarılı satırda "⚠ Uyarılı: gerekçe", boş bölümde "Kayıt yok."', async () => {
    const w = await kur()
    expect(w.findAll('section.bolum')).toHaveLength(2)
    expect(w.text()).toContain('Atamalar (1)')
    expect(w.text()).toContain('⚠ Uyarılı: Amir onayı')
    expect(w.text()).toContain('Uyaran kural: Ö-002: Görev sayısı: 4 > 3 (Kural: Gün sınırı)')
    expect(w.text()).toContain('Sefer iptalleri (0)')
    expect(w.text()).toContain('Kayıt yok.')
  })

  it('tablo yazdır kapsayıcı desenindedir (yazdir-tablo-kap > yazdir-tablo)', async () => {
    const w = await kur()
    const tablo = w.get('table')
    expect(tablo.classes()).toContain('yazdir-tablo')
    expect(tablo.element.parentElement!.classList).toContain('yazdir-tablo-kap')
  })

  it('Yazdır düğmesi yükleniyorken ve hatada kapalı, veri gelince açık', async () => {
    vi.mocked(degisiklikApi.rapor).mockReturnValue(new Promise(() => {}))
    expect((await kur()).get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(degisiklikApi.rapor).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Aralık en çok 366 gün olabilir.'),
    )
    const hatali = await kur()
    expect(hatali.get('[role="alert"]').text()).toBe('Aralık en çok 366 gün olabilir.')
    expect(hatali.get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(degisiklikApi.rapor).mockResolvedValue(yanit)
    expect((await kur()).get('.no-print button').attributes('disabled')).toBeUndefined()
  })

  it('tarih eksikse istek atılmaz, hata gösterilir, Yazdır kapalı', async () => {
    const w = await kur({ bas: '', bit: '' })
    expect(degisiklikApi.rapor).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toContain('tarih')
    expect(w.get('.no-print button').attributes('disabled')).toBeDefined()
  })
})
