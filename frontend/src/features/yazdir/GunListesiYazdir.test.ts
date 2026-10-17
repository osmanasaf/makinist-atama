import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/features/atama/atamaApi', () => ({ atamaApi: { gun: vi.fn() } }))

import { kullanici } from '@/composables/oturum'
import { atamaApi, type GunListesi, type GunSatiri } from '@/features/atama/atamaApi'
import { makinist } from '@/features/atama/testVerisi'
import GunListesiYazdir from './GunListesiYazdir.vue'

const bos = { template: '<div />' }

/** 100 satır: 4 atanmamış (0-3), 1 iptal (4), 1 gecikmeli (5), kalanı atanmış. */
function satir(i: number): GunSatiri {
  const saat = String(5 + (i % 10)).padStart(2, '0') // UTC; İstanbul +3
  return {
    seferId: i + 1,
    trenNo: `T${1000 + i}`,
    kalkisGar: 'Ankara',
    varisGar: 'Eskişehir',
    planlananKalkis: `2026-10-12T${saat}:00:00Z`,
    planlananVaris: `2026-10-12T${saat}:30:00Z`,
    guncelKalkis: `2026-10-12T${saat}:00:00Z`,
    guncelVaris: `2026-10-12T${saat}:30:00Z`,
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    aracTipi: 'YHT',
    hat: 'Ankara–Eskişehir',
    km: 230,
    durum: 'PLANLI',
    kirmizi: false,
    makinistler: [makinist({ atamaId: i, makinistId: i, sicil: `D${i}`, ad: `Makinist ${i}` })],
    bosNeden: null,
  }
}
const satirlar = Array.from({ length: 100 }, (_, i) => satir(i))
for (let i = 0; i < 4; i++) satirlar[i]!.makinistler = []
satirlar[4]!.durum = 'IPTAL'
satirlar[4]!.makinistler = []
satirlar[5]!.kalkisGecikmeDk = 90
satirlar[5]!.guncelKalkis = '2026-10-12T05:30:00Z' // İstanbul 08:30
satirlar[6]!.makinistler.push(
  makinist({ atamaId: 900, rol: 'YARDIMCI', makinistId: 900, sicil: 'D900', ad: 'Veli Yardım' }),
)

satirlar[7]!.makinistler = [
  makinist({
    atamaId: 700,
    makinistId: 700,
    sicil: 'D700',
    ad: 'Kırmızı Kişi',
    kirmizi: true,
    kirmiziNedenler: ['Dinlenme 9 s 30 dk < 11 s', 'İkinci neden'],
  }),
]

const liste: GunListesi = { tarih: '2026-10-12', uretimZamani: '2026-10-12T07:15:00Z', satirlar }

async function kur() {
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
  return w
}

describe('GunListesiYazdir', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.gun).mockResolvedValue(liste)
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('100 satırı basar; durum sütununda 4 BOŞ, 1 İPTAL, 1 gecikme metni', async () => {
    const w = await kur()
    expect(atamaApi.gun).toHaveBeenCalledWith('2026-10-12')
    expect(w.findAll('tbody tr')).toHaveLength(100)
    const durumlar = w.findAll('td.durum').map((td) => td.text())
    expect(durumlar.filter((d) => d === 'BOŞ')).toHaveLength(4)
    expect(durumlar.filter((d) => d === 'İPTAL')).toHaveLength(1)
    expect(durumlar.filter((d) => d === '+90 dk (yeni kalkış 08:30)')).toHaveLength(1)
  })

  it('kırmızı görevli: makinist hücresinde (KONTROL: ilk neden), durumda KONTROL; diğerlerinde yok', async () => {
    const w = await kur()
    const satir8 = w.findAll('tbody tr')[7]!
    expect(satir8.get('td.makinist').text()).toContain(
      'Kırmızı Kişi (D700) (KONTROL: Dinlenme 9 s 30 dk < 11 s)',
    )
    expect(satir8.get('td.makinist').text()).not.toContain('İkinci neden')
    expect(satir8.get('td.durum').text()).toBe('KONTROL')
    expect(w.findAll('td.makinist').filter((td) => td.text().includes('KONTROL'))).toHaveLength(1)
  })

  it('satır içeriği: saat aralığı (İstanbul), güzergâh, asıl ve yardımcı makinist', async () => {
    const w = await kur()
    const satir7 = w.findAll('tbody tr')[6]!.text() // i=6, yardımcılı
    expect(satir7).toContain('14:00–14:30')
    expect(satir7).toContain('T1006')
    expect(satir7).toContain('Ankara – Eskişehir')
    expect(satir7).toContain('Makinist 6 (D6)')
    expect(satir7).toContain('Yrd: Veli Yardım')
  })

  it('altbilgi: kayıt sayısı 100, Son güncelleme sunucu uretimZamani (istemci saati değil), yazdıran kullanıcı', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2030-01-01T00:00:00Z'))
    try {
      const w = await kur()
      const alt = w.get('footer').text().replace(/\s+/g, ' ')
      expect(alt).toContain('Kayıt sayısı: 100')
      expect(alt).toContain('Son güncelleme: 12.10.2026 10:15')
      expect(alt).not.toContain('2030')
      expect(alt).toContain('Yazdıran: Ayşe Kaya')
      expect(w.text()).toContain('Tarih: 12.10.2026')
    } finally {
      vi.useRealTimers()
    }
  })

  it('tablo, ekranda yatay kaydırılabilen kapsayıcıdadır (dar ekranda taşmaz)', async () => {
    const w = await kur()
    expect(w.get('table').element.parentElement!.classList).toContain('yazdir-tablo-kap')
  })

  it('Yazdır düğmesi veri gelene kadar ve hatada kapalı, veri gelince açıktır', async () => {
    vi.mocked(atamaApi.gun).mockReturnValue(new Promise(() => {}))
    const yukleniyor = await kur()
    expect(yukleniyor.get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(atamaApi.gun).mockRejectedValue(new Error('x'))
    const hatali = await kur()
    expect(hatali.get('[role="alert"]').text()).toBe('Liste yüklenemedi.')
    expect(hatali.get('.no-print button').attributes('disabled')).toBeDefined()

    vi.mocked(atamaApi.gun).mockResolvedValue(liste)
    const hazir = await kur()
    expect(hazir.get('.no-print button').attributes('disabled')).toBeUndefined()
  })

  it('Yazdır düğmesi ve Geri no-print sınıfındadır; Yazdır window.print çağırır', async () => {
    const w = await kur()
    const yazdir = vi.fn()
    vi.stubGlobal('print', yazdir)
    expect(w.get('.no-print').classes()).toContain('no-print')
    await w.get('.no-print button').trigger('click')
    expect(yazdir).toHaveBeenCalledTimes(1)
    vi.unstubAllGlobals()
  })
})

describe('GunListesiYazdir: nöbet notu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol: 'ATAMACI', aktif: true }
  })

  it('kırmızı neden nöbet içeriyorsa sayfada yalnız bir not basılır', async () => {
    const nobetli = (i: number) =>
      makinist({
        atamaId: 800 + i,
        makinistId: 800 + i,
        kirmizi: true,
        kirmiziNedenler: ['Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk'],
      })
    const ikisi = [satir(1), satir(2)].map((s, i) => ({ ...s, makinistler: [nobetli(i)] }))
    vi.mocked(atamaApi.gun).mockResolvedValue({ ...liste, satirlar: ikisi })
    const w = await kur()
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })

  it('nöbet geçmiyorsa not yok', async () => {
    vi.mocked(atamaApi.gun).mockResolvedValue(liste)
    const w = await kur()
    expect(w.find('[data-test="nobet-notu"]').exists()).toBe(false)
  })
})
