import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import { kullanici } from '@/composables/oturum'
import type { GunListesi, GunSatiri } from '@/features/atama/atamaApi'
import type { BelgeDurumuYaniti } from '@/features/belge/belgeApi'

vi.mock('@/features/atama/atamaApi', () => ({
  atamaApi: { gun: vi.fn(), kirmiziSayisi: vi.fn(), gordumOzeti: vi.fn() },
}))
vi.mock('@/features/takas/takasApi', () => ({ takasApi: { liste: vi.fn() } }))
vi.mock('@/features/belge/belgeApi', () => ({ belgeApi: { durum: vi.fn() } }))
vi.mock('@/features/rapor/raporApi', () => ({
  raporApi: { izinListe: vi.fn() },
}))
vi.mock('./demoApi', () => ({
  demoApi: {
    durum: vi.fn(),
    yukle: vi.fn(),
    sifirla: vi.fn(),
    yolculukOrnekleri: vi.fn(),
    parcaliOrnek: vi.fn(),
  },
}))

import { atamaApi } from '@/features/atama/atamaApi'
import { belgeApi } from '@/features/belge/belgeApi'
import { raporApi } from '@/features/rapor/raporApi'
import { takasApi } from '@/features/takas/takasApi'
import AnaSayfa from './AnaSayfa.vue'
import { demoApi } from './demoApi'

const GELECEK = '2999-01-01T10:00:00Z'
const GECMIS = '2000-01-01T10:00:00Z'

function satir(id: number, ek: Partial<GunSatiri> = {}): GunSatiri {
  return {
    seferId: id,
    trenNo: `T${id}`,
    kalkisGar: 'Ankara',
    varisGar: 'Sivas',
    planlananKalkis: GECMIS,
    planlananVaris: GELECEK,
    guncelKalkis: GECMIS,
    guncelVaris: GELECEK,
    kalkisGecikmeDk: 0,
    varisGecikmeDk: 0,
    aracTipi: 'YHT',
    hat: 'AS',
    km: 100,
    durum: 'PLANLI',
    kirmizi: false,
    makinistler: [],
    bosNeden: null,
    ...ek,
  }
}

const asil = {
  atamaId: 1,
  rol: 'ASIL' as const,
  makinistId: 1,
  sicil: 'D001',
  ad: 'Ali',
  kirmizi: false,
  kirmiziNedenler: [],
  uyarilar: [],
}

const gun = (satirlar: GunSatiri[]): GunListesi => ({
  tarih: '2026-10-11',
  uretimZamani: GECMIS,
  satirlar,
})

const belgeYaniti: BelgeDurumuYaniti = {
  bugun: '2026-10-11',
  uyariGun: 30,
  makinistler: [
    {
      makinistId: 1,
      sicil: 'D001',
      adSoyad: 'A',
      durum: 'AKTIF',
      enKotu: 'DOLDU',
      belgeler: [],
    },
    {
      makinistId: 2,
      sicil: 'D002',
      adSoyad: 'B',
      durum: 'AKTIF',
      enKotu: 'YAKLASIYOR',
      belgeler: [],
    },
    {
      makinistId: 3,
      sicil: 'D003',
      adSoyad: 'C',
      durum: 'AKTIF',
      enKotu: 'YAKLASIYOR',
      belgeler: [],
    },
    {
      makinistId: 4,
      sicil: 'D004',
      adSoyad: 'D',
      durum: 'ASKIDA',
      enKotu: 'DOLDU',
      belgeler: [],
    },
  ],
}

// Bağlı kalan sayfalar global oturum durumunu (kullanici) izlemeyi sürdürür: her testten sonra sökülür.
const kurulan: Array<{ unmount: () => void }> = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/atama', '/seferler/yukle', '/belgeler', '/izin', '/takas'].map((path, i) => ({
      path,
      name: ['ana', 'atama', 'sefer-yukle', 'belgeler', 'izin', 'takas'][i],
      component: { template: '<div />' },
    })),
  })
  const w = mount(AnaSayfa, { global: { plugins: [router] } })
  kurulan.push(w)
  await vi.dynamicImportSettled() // demo araçları tembel yüklenen ayrı bileşen (DemoAraclari.vue)
  await flushPromises()
  return w
}

describe('AnaSayfa', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = {
      id: 1,
      eposta: 'a@b.c',
      adSoyad: 'Ayşe Kaya',
      rol: 'ATAMACI',
      aktif: true,
    }
    vi.mocked(atamaApi.gun).mockResolvedValue(
      gun([
        satir(1, { makinistler: [asil] }), // atanmış
        satir(2), // atanmamış, henüz bitmemiş
        satir(3, { guncelVaris: GECMIS }), // atanmamış ama bitmiş: sayılmaz
        satir(4, { durum: 'IPTAL' }), // iptal: atanmamış sayılmaz
      ]),
    )
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-11',
      bit: '2026-10-18',
      kirmiziSefer: 3,
      kirmiziAtama: 4,
      ilkKirmiziGun: '2026-10-13',
    })
    vi.mocked(belgeApi.durum).mockResolvedValue(belgeYaniti)
    vi.mocked(raporApi.izinListe).mockResolvedValue([{ id: 1 }, { id: 2 }] as never)
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({
      bas: '',
      bit: '',
      toplam: 5,
      gorulen: 3,
      gorulmeyen: 2,
      hesapsiz: 0,
      uyari: 0,
    })
    vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 1, talepler: [] })
    vi.mocked(demoApi.durum).mockRejectedValue(new ApiHata(404, 'BULUNAMADI', 'yok'))
  })

  it('karşılama başlığını ve kullanıcı adını gösterir', async () => {
    const w = await kur()
    expect(w.get('h1').text()).toBe('Hoş geldiniz')
    expect(w.text()).toContain('Ayşe Kaya')
  })

  it('bugünün özetini gösterir: seferler, atanmamış (bitmeyen), kırmızı, aktif belge sorunları, izin talepleri', async () => {
    const w = await kur()
    const sefer = w.get('[data-test=kart-sefer]')
    expect(sefer.text()).toContain('4')
    expect(w.get('[data-test=atanmamis]').text()).toContain('1') // yalnız #2
    expect(sefer.text()).toContain('İptal: 1')
    expect(w.get('[data-test=kart-kirmizi]').text()).toContain('3')
    expect(w.get('[data-test=kart-kirmizi]').text()).toContain('4 atama')
    expect(w.get('[data-test=kart-belge]').text()).toContain('Süresi dolmuş: 1') // ASKIDA olan sayılmaz
    expect(w.get('[data-test=kart-belge]').text()).toContain('Süresi yaklaşan: 2')
    expect(w.get('[data-test=kart-izin]').text()).toContain('2')
    expect(w.find('[data-test=bos-durum]').exists()).toBe(false)
  })

  it('bugün sefer yoksa yönlendirici boş durum gösterir', async () => {
    vi.mocked(atamaApi.gun).mockResolvedValue(gun([]))
    const w = await kur()
    expect(w.get('[data-test=bos-durum]').text()).toContain('Bugün için sefer yok')
    expect(w.get('[data-test=bos-durum] a').attributes('href')).toBe('/seferler/yukle')
  })

  it('bir kaynak düşerse yalnız o kart hata gösterir, diğerleri görünür', async () => {
    vi.mocked(belgeApi.durum).mockRejectedValue(new ApiHata(500, 'SUNUCU_HATASI', 'Belge hatası'))
    const w = await kur()
    expect(w.get('[data-test=kart-belge]').text()).toContain('Belge hatası')
    expect(w.get('[data-test=kart-sefer]').text()).toContain('4')
    expect(w.get('[data-test=kart-kirmizi]').text()).toContain('3')
  })

  describe('Bugün yapılacaklar (H1+O2, AK-6.x)', () => {
    const kalemler = (w: Awaited<ReturnType<typeof kur>>) =>
      w.findAll('[data-test=yapilacaklar] > li')
    const bugunTarihi = () =>
      new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Istanbul' })

    it('AK-6.1: aciliyete göre sıralı <ol>; her kalemde tek eylem bağlantısı (<a>, buton değil)', async () => {
      const w = await kur()
      const l = kalemler(w)
      expect(w.get('[data-test=yapilacaklar]').element.tagName).toBe('OL')
      expect(l.map((x) => x.attributes('data-kimlik'))).toEqual([
        'kirmizi',
        'atanmamis',
        'gorulmeyen',
        'takas',
        'izin',
        'belge',
      ])
      for (const x of l) {
        expect(x.findAll('a')).toHaveLength(1)
        expect(x.findAll('button')).toHaveLength(0)
      }
      expect(l[0]!.text()).toContain('3 sefer kontrol bekliyor')
      expect(l[1]!.text()).toContain('1 sefer atanmamış') // yalnız #2 (bitmeyen)
      expect(l[1]!.text()).toContain('1 seferin kalkışı geçti') // test verisinde kalkış 2000 yılında; sefer henüz bitmedi
      expect(l[2]!.text()).toContain('2 atamayı makinist henüz görmedi')
      expect(l[3]!.text()).toContain('1 takas talebi')
      expect(l[4]!.text()).toContain('2 izin talebi')
      expect(l[5]!.text()).toContain('1 belgenin süresi doldu')
    })

    it('AK-6.2/6.4: kırmızı bağlantısı İLK KIRMIZI GÜNÜ + kirmizi süzgecini açar; atanmamış bugünü + bitmeyen (atanmamış) süzgecini', async () => {
      const w = await kur()
      const l = kalemler(w)
      expect(l[0]!.get('a').attributes('href')).toBe('/atama?tarih=2026-10-13&suzgec=kirmizi')
      expect(l[1]!.get('a').attributes('href')).toBe(
        `/atama?tarih=${bugunTarihi()}&suzgec=bitmeyen`,
      )
      expect(l[2]!.get('a').attributes('href')).toBe(
        `/atama?tarih=${bugunTarihi()}&suzgec=gormeyen`,
      )
      expect(l[3]!.get('a').attributes('href')).toBe('/takas')
      expect(l[4]!.get('a').attributes('href')).toBe('/izin')
      expect(l[5]!.get('a').attributes('href')).toBe('/belgeler')
    })

    it('B-2 yokken (ilkKirmiziGun alanı yok) kırmızı bağlantısı bugüne düşer', async () => {
      vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
        bas: '',
        bit: '',
        kirmiziSefer: 1,
        kirmiziAtama: 1,
      })
      const w = await kur()
      expect(kalemler(w)[0]!.get('a').attributes('href')).toBe(
        `/atama?tarih=${bugunTarihi()}&suzgec=kirmizi`,
      )
    })

    it('mevcut 4 sayı kartı aşağıda KALIR ve bağlantıları derin bağlantıdır', async () => {
      const w = await kur()
      expect(w.get('[data-test=kart-kirmizi] a').attributes('href')).toBe(
        '/atama?tarih=2026-10-13&suzgec=kirmizi',
      )
      expect(w.get('[data-test=kart-sefer] a').attributes('href')).toBe(
        `/atama?tarih=${bugunTarihi()}`,
      )
    })

    it('AK-6.5: bir kaynak düşerse yalnız o satır hata verir, diğer kalemler durur', async () => {
      vi.mocked(takasApi.liste).mockRejectedValue(new ApiHata(500, 'SUNUCU_HATASI', 'Takas hatası'))
      const w = await kur()
      expect(kalemler(w).map((x) => x.attributes('data-kimlik'))).not.toContain('takas')
      expect(kalemler(w).length).toBe(5)
      expect(w.get('[data-test=yapilacak-hata]').text()).toContain('Takas hatası')
      expect(w.get('[data-test=yapilacak-hata]').attributes('role')).toBe('alert')
    })

    it('AK-6.5: hiç iş yokken "bekleyen iş yok" durumu; liste çizilmez', async () => {
      vi.mocked(atamaApi.gun).mockResolvedValue(gun([satir(1, { makinistler: [asil] })]))
      vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
        bas: '',
        bit: '',
        kirmiziSefer: 0,
        kirmiziAtama: 0,
      })
      vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({ gorulmeyen: 0 } as never)
      vi.mocked(takasApi.liste).mockResolvedValue({ bekleyen: 0, talepler: [] })
      vi.mocked(raporApi.izinListe).mockResolvedValue([])
      vi.mocked(belgeApi.durum).mockResolvedValue({ ...belgeYaniti, makinistler: [] })
      const w = await kur()
      expect(w.find('[data-test=yapilacaklar]').exists()).toBe(false)
      expect(w.get('[data-test=yapilacak-yok]').text()).toContain('sizi bekleyen iş yok')
    })
  })

  describe('demo veri', () => {
    beforeEach(() => {
      kullanici.value = {
        id: 2,
        eposta: 'y@b.c',
        adSoyad: 'Yön Etici',
        rol: 'YONETICI',
        aktif: true,
      }
    })

    it('uç yoksa (üretim) ya da atamacı için bölüm görünmez', async () => {
      expect((await kur()).find('[data-test=demo]').exists()).toBe(false)
      kullanici.value = {
        id: 1,
        eposta: 'a@b.c',
        adSoyad: 'Ayşe',
        rol: 'ATAMACI',
        aktif: true,
      }
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: false })
      expect((await kur()).find('[data-test=demo]').exists()).toBe(false)
      expect(demoApi.durum).toHaveBeenCalledTimes(1) // yalnız ilk kurulumda (yönetici) soruldu
    })

    it('demo araçları katlanır <details> (varsayılan kapalı) ve yalnız dev derlemesinde çizilir', async () => {
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: false })
      const w = await kur()
      const d = w.get('details[data-test=demo]')
      expect(d.get('summary').text()).toBe('Demo araçları')
      expect(d.attributes('open')).toBeUndefined()
      vi.stubEnv('DEV', false)
      try {
        expect((await kur()).find('[data-test=demo]').exists()).toBe(false)
      } finally {
        vi.unstubAllEnvs()
      }
    })

    it('INFO: bugün sefer yoksa ve demo yüklü değilse bölüm açık gelir ve demo yüklemeyi önerir', async () => {
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: false })
      vi.mocked(atamaApi.gun).mockResolvedValue(gun([]))
      const w = await kur()
      expect(w.get('details[data-test=demo]').attributes('open')).toBeDefined()
      expect(w.get('[data-test=demo]').text()).toContain('Bugün için sefer yok: aşağıdaki düğmeyle')
    })

    it('INFO: AnaSayfa.vue demo metni ve uç adı taşımaz (üretim paketine girmesin; DemoAraclari tembel ve DEV koşullu yüklenir)', () => {
      const kaynak = readFileSync(join(process.cwd(), 'src/features/ana/AnaSayfa.vue'), 'utf8')
      for (const yasak of ['Demo araçları', '/api/demo', 'demoApi', 'geciciParola'])
        expect(kaynak).not.toContain(yasak)
      expect(kaynak).toContain('import.meta.env.DEV')
      expect(kaynak).toContain("defineAsyncComponent(() => import('./DemoAraclari.vue'))")
    })

    it('veri yokken "Demo veriyi yükle" yükler ve özeti yeniler (UX-1)', async () => {
      vi.mocked(demoApi.durum)
        .mockResolvedValueOnce({ yuklu: false })
        .mockResolvedValue({ yuklu: true })
      vi.mocked(demoApi.yukle).mockResolvedValue({
        makinist: 68,
        sefer: 3100,
        izin: 15,
      })
      const w = await kur()
      const oncekiGun = vi.mocked(atamaApi.gun).mock.calls.length

      await w.get('[data-test=demo-yukle]').trigger('click')
      await flushPromises()

      expect(demoApi.yukle).toHaveBeenCalledTimes(1)
      expect(w.get('[data-test=demo]').text()).toContain('68 makinist, 3100 sefer, 15 izin')
      expect(vi.mocked(atamaApi.gun).mock.calls.length).toBe(oncekiGun + 1) // özet yenilendi
      expect(w.find('[data-test=demo-yukle]').exists()).toBe(false)
      expect(w.find('[data-test=demo-sifirla]').exists()).toBe(true)
    })

    it('yükleme hatası gösterilir ve düğme geri gelir', async () => {
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: false })
      vi.mocked(demoApi.yukle).mockRejectedValue(
        new ApiHata(409, 'DEMO_DOLU', 'Veritabanında zaten veri var.'),
      )
      const w = await kur()
      await w.get('[data-test=demo-yukle]').trigger('click')
      await flushPromises()
      expect(w.get('[data-test=demo]').text()).toContain('Veritabanında zaten veri var.')
      expect(w.get('[data-test=demo-yukle]').attributes('disabled')).toBeUndefined()
    })

    it('F2b: demo yüklüyken "Yolculuk örneklerini ekle" çalışır; ikinci çağrının 409 hatası gösterilir', async () => {
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: true })
      vi.mocked(demoApi.yolculukOrnekleri)
        .mockResolvedValueOnce({ yolculuk: 3 })
        .mockRejectedValueOnce(
          new ApiHata(409, 'DEMO_YOLCULUK_VAR', 'Yolculuk örnekleri zaten yüklü.'),
        )
      const w = await kur()

      await w.get('[data-test=demo-yolculuk]').trigger('click')
      await flushPromises()
      expect(w.get('[data-test=demo]').text()).toContain('3 yolculuk ve bir konaklama')

      await w.get('[data-test=demo-yolculuk]').trigger('click')
      await flushPromises()
      expect(w.get('[data-test=demo]').text()).toContain('Yolculuk örnekleri zaten yüklü.')
    })

    it('F2c: demo yüklüyken "Parçalı örnek ekle" çalışır; ikinci çağrının 409 hatası gösterilir', async () => {
      vi.mocked(demoApi.durum).mockResolvedValue({ yuklu: true })
      vi.mocked(demoApi.parcaliOrnek)
        .mockResolvedValueOnce({ parca: 2 })
        .mockRejectedValueOnce(
          new ApiHata(409, 'DEMO_PARCALI_VAR', 'Parçalı tren örneği zaten var.'),
        )
      const w = await kur()

      await w.get('[data-test=demo-parcali]').trigger('click')
      await flushPromises()
      expect(w.get('[data-test=demo]').text()).toContain('2 parça')

      await w.get('[data-test=demo-parcali]').trigger('click')
      await flushPromises()
      expect(w.get('[data-test=demo]').text()).toContain('Parçalı tren örneği zaten var.')
    })

    it('sıfırlama iki adımlıdır: önce onay sorulur', async () => {
      vi.mocked(demoApi.durum)
        .mockResolvedValueOnce({ yuklu: true })
        .mockResolvedValue({ yuklu: false })
      vi.mocked(demoApi.sifirla).mockResolvedValue(undefined)
      const w = await kur()

      await w.get('[data-test=demo-sifirla]').trigger('click')
      expect(demoApi.sifirla).not.toHaveBeenCalled()
      expect(w.get('[data-test=demo]').text()).toContain('Emin misiniz?')

      await w.get('[data-test=demo-sifirla-onay]').trigger('click')
      await flushPromises()
      expect(demoApi.sifirla).toHaveBeenCalledTimes(1)
      expect(w.get('[data-test=demo]').text()).toContain('Demo veri silindi.')
      expect(w.find('[data-test=demo-yukle]').exists()).toBe(true)
    })
  })
})
