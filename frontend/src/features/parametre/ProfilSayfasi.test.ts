// T1.10 (F2, FR-2.8/2.9, AK-2.2..2.9): hazır profil / dosya -> fark tablosu -> seçim -> neden -> onay -> uygula.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./profilApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./profilApi')>()),
  profilApi: {
    hazirListe: vi.fn(),
    hazir: vi.fn(),
    onizle: vi.fn(),
    uygula: vi.fn(),
    disaAktar: vi.fn(),
  },
}))

import { ApiHata } from '@/api/istemci'
import ProfilSayfasi from './ProfilSayfasi.vue'
import {
  profilApi,
  type ProfilOnizleme,
  type ProfilPaketi,
  type ProfilSatiri,
  type SatirDurumu,
} from './profilApi'

const ETIKET = 'Örnek başlangıç — kurum mevzuatıyla doğrulanmalı. Karar destek aracıdır.'
const paket: ProfilPaketi = {
  format: 'makinist-atama-profil',
  profilSurumu: 1,
  ad: 'AB 2005/47 referansı (örnek)',
  aciklama: 'Dinlenme sürelerini AB referansına çeker.',
  etiket: ETIKET,
}
const satir = (
  anahtar: string,
  durum: SatirDurumu,
  ekstra: Partial<ProfilSatiri> = {},
): ProfilSatiri => ({
  kimlik: `PARAMETRE:${anahtar}`,
  tur: 'PARAMETRE',
  anahtar,
  ad: `Ad ${anahtar}`,
  mevcut: '11',
  yeni: '12',
  durum,
  neden: null,
  dayanak: null,
  kaynak: 'Kaynak metni',
  guvenilirlik: 'ORTA',
  ...ekstra,
})
const onizleme = (satirlar: ProfilSatiri[], uyarilar: string[] = []): ProfilOnizleme => ({
  paket,
  satirlar,
  uyarilar,
  ozet: {
    degisir: satirlar.filter((s) => s.durum === 'DEGISIR').length,
    ayni: satirlar.filter((s) => s.durum === 'AYNI').length,
    gecersiz: satirlar.filter((s) => s.durum === 'GECERSIZ').length,
    bilinmeyen: satirlar.filter((s) => s.durum === 'BILINMEYEN').length,
    profildeYok: satirlar.filter((s) => s.durum === 'PROFILDE_YOK').length,
  },
})
const HAZIR = [
  { kod: 'varsayilan', ad: 'Proje varsayılanları', aciklama: 'Varsayılana döner.', etiket: ETIKET },
  { kod: 'ab-2005-47', ad: 'AB 2005/47 referansı (örnek)', aciklama: 'AB.', etiket: ETIKET },
]
const SATIRLAR = [
  satir('dinlenme.ev_saat', 'DEGISIR', { dayanak: 'AB 12' }),
  satir('dinlenme.dis_merkez_saat', 'DEGISIR', { yeni: '8' }),
  satir('gece.gorev_orani', 'GECERSIZ', { yeni: '1.5', neden: 'Değer 0 ile 1 arasında olmalı.' }),
  satir('yedek.oran', 'AYNI'),
  satir('yok.anahtar', 'BILINMEYEN', { mevcut: null }),
]

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profil', component: { template: '<div />' } },
      { path: '/ayarlar', component: { template: '<div />' } },
    ],
  })
  await router.push('/profil')
  const w = mount(ProfilSayfasi, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return w
}
const hazirDugme = (w: Awaited<ReturnType<typeof kur>>, kod: string) =>
  w.get(`[data-test="hazir-${kod}"] button`)

beforeEach(() => {
  document.body.innerHTML = ''
  vi.resetAllMocks()
  vi.mocked(profilApi.hazirListe).mockResolvedValue(HAZIR)
  vi.mocked(profilApi.hazir).mockResolvedValue(paket)
  vi.mocked(profilApi.onizle).mockResolvedValue(onizleme(SATIRLAR))
  vi.mocked(profilApi.uygula).mockResolvedValue({ yazilan: 2, atlanan: 0 })
})

describe('ProfilSayfasi', () => {
  it('etiket her zaman görünür; hazır profiller listelenir (yasal/uyum iddiası yok)', async () => {
    const w = await kur()
    expect(w.get('[data-test="etiket"]').text()).toBe(ETIKET)
    expect(w.findAll('[data-test^="hazir-"]')).toHaveLength(2)
    expect(w.text()).not.toMatch(/yasal|uyumlu|onaylı|mevzuata uygun/i)
  })

  it('hazır profil seç -> fark tablosu: yalnız DEGISIR/GECERSIZ görünür, etiket ve dayanak görünür', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()

    expect(profilApi.hazir).toHaveBeenCalledWith('ab-2005-47')
    expect(profilApi.onizle).toHaveBeenCalledWith(paket)
    expect(w.find('[data-test="satir-PARAMETRE:dinlenme.ev_saat"]').exists()).toBe(true)
    expect(w.find('[data-test="satir-PARAMETRE:gece.gorev_orani"]').exists()).toBe(true)
    expect(w.find('[data-test="satir-PARAMETRE:yedek.oran"]').exists()).toBe(false)
    expect(w.find('[data-test="satir-PARAMETRE:yok.anahtar"]').exists()).toBe(false)
    expect(w.get('[data-test="paket-etiketi"]').text()).toBe(ETIKET)
    expect(w.get('[data-test="satir-PARAMETRE:dinlenme.ev_saat"]').text()).toContain('AB 12')
    expect(w.get('[data-test="ozet"]').text()).toContain('Değişir: 2')
    expect(w.get('#fark-baslik').text()).toContain('AB 2005/47 referansı (örnek)')
    expect(document.activeElement).toBe(w.get('#fark-baslik').element) // odak sonuca taşınır
  })

  it('"Hepsini göster" tüm satırları açar; Aynı/Bilinmeyen satırlarda onay kutusu devre dışı', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w.get('[data-test="hepsini-goster"]').setValue(true)

    expect(w.findAll('tbody tr')).toHaveLength(5)
    const kutu = (a: string) =>
      w.get(`[data-test="satir-PARAMETRE:${a}"] input[type="checkbox"]`).element as HTMLInputElement
    expect(kutu('yedek.oran').disabled).toBe(true)
    expect(kutu('yok.anahtar').disabled).toBe(true)
    expect(kutu('gece.gorev_orani').disabled).toBe(true)
    expect(kutu('dinlenme.ev_saat').disabled).toBe(false)
    expect(kutu('dinlenme.ev_saat').checked).toBe(true) // DEGISIR satırlar işaretli gelir
  })

  it('geçersiz satırın nedeni görünür; tablo adlı (caption) ve başlıklar kapsamlı (th scope)', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()

    expect(w.get('[data-test="satir-PARAMETRE:gece.gorev_orani"]').text()).toContain(
      'Değer 0 ile 1 arasında olmalı.',
    )
    expect(w.get('caption').text()).toBe('Kural değerleri')
    expect(w.findAll('thead th').every((th) => th.attributes('scope') === 'col')).toBe(true)
    expect(w.findAll('tbody th').every((th) => th.attributes('scope') === 'row')).toBe(true)
    expect(
      w.get('[data-test="satir-PARAMETRE:dinlenme.ev_saat"] input').attributes('aria-label'),
    ).toBe('Ad dinlenme.ev_saat satırını uygula')
  })

  it('neden zorunlu: boşken uygula hata verir ve onay açılmaz; sunucu çağrılmaz', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w.get('form.uygula-formu').trigger('submit')

    expect(w.get('#profil-neden-hata').text()).toBe('Değişiklik nedeni yazılmalı.')
    expect(w.get('[data-test="neden"]').attributes('aria-invalid')).toBe('true')
    expect(w.find('dialog').exists()).toBe(false)
    expect(profilApi.uygula).not.toHaveBeenCalled()
  })

  it('seçimi azaltma: yalnız işaretli satırlar gönderilir; onay diyaloğunda etiket görünür', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w
      .get('[data-test="satir-PARAMETRE:dinlenme.dis_merkez_saat"] input[type="checkbox"]')
      .setValue(false)
    expect(w.get('[data-test="secili-sayi"]').text()).toBe('1 satır seçili')
    await w.get('[data-test="neden"]').setValue('  Kurum kararı  ')
    await w.get('form.uygula-formu').trigger('submit')
    await flushPromises()

    const dialog = w.get('dialog')
    expect(dialog.text()).toContain(ETIKET)
    expect(dialog.text()).toContain('Kurum kararı')
    await dialog.get('form').trigger('submit')
    await flushPromises()

    expect(profilApi.uygula).toHaveBeenCalledWith(
      paket,
      ['PARAMETRE:dinlenme.ev_saat'],
      'Kurum kararı',
    )
    expect(w.find('dialog').exists()).toBe(false)
    expect(w.get('[data-test="sonuc"]').text()).toContain('2 ayar yazıldı')
    expect(profilApi.onizle).toHaveBeenCalledTimes(2) // uygulamadan sonra fark yenilenir
  })

  it('O-3: uygulama başarılıyken fark tablosu yenilenemezse "hiçbir değişiklik yapılmadı" denmez; sonuç görünür', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w.get('[data-test="neden"]').setValue('Kurum kararı')
    await w.get('form.uygula-formu').trigger('submit')
    await flushPromises()
    vi.mocked(profilApi.onizle).mockRejectedValueOnce(
      new ApiHata(504, 'ZAMAN_ASIMI', 'Sunucu zaman aşımına uğradı.'),
    )
    await w.get('dialog').get('form').trigger('submit')
    await flushPromises()

    expect(profilApi.uygula).toHaveBeenCalledTimes(1)
    expect(w.text()).not.toContain('Hiçbir değişiklik yapılmadı')
    expect(w.text()).toContain('Profil uygulandı; fark tablosu yenilenemedi')
    expect(w.get('[data-test="sonuc"]').text()).toContain('2 ayar yazıldı')
  })

  it('O-6 (WCAG 2.5.3): "Farkları göster" düğmesinin erişilebilir adı görünen metinle başlar ve profili ayırt eder', async () => {
    const w = await kur()
    const adlar = w.findAll('[data-test^="hazir-"] button').map((b) => b.attributes('aria-label'))
    expect(adlar).toEqual([
      'Farkları göster: Proje varsayılanları profili',
      'Farkları göster: AB 2005/47 referansı (örnek) profili',
    ])
  })

  it('hiç satır seçili değilken Uygula devre dışı', async () => {
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w.get('[data-test="hicbirini-secme"]').trigger('click')
    expect((w.get('[data-test="uygula"]').element as HTMLButtonElement).disabled).toBe(true)
    await w.get('[data-test="tumunu-sec"]').trigger('click')
    expect((w.get('[data-test="uygula"]').element as HTMLButtonElement).disabled).toBe(false)
  })

  it('paket düzeyi uyarılar (çapraz tutarlılık) gösterilir', async () => {
    vi.mocked(profilApi.onizle).mockResolvedValue(
      onizleme(SATIRLAR, [
        'Tutarlılık: Aktarma süresi nöbet ayırma süresinin (12 dk) altında olmalı.',
      ]),
    )
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="uyarilar"]').text()).toContain('Tutarlılık')
  })

  it('sunucu uygulamayı reddederse (422) hata duyurulur ve sonuç görünmez', async () => {
    vi.mocked(profilApi.uygula).mockRejectedValue(
      new ApiHata(
        422,
        'PROFIL_GECERSIZ',
        'Seçilen satırlardan 1 tanesi uygulanamaz; hiçbir değişiklik yapılmadı.',
      ),
    )
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    await w.get('[data-test="neden"]').setValue('n')
    await w.get('form.uygula-formu').trigger('submit')
    await flushPromises()
    await w.get('dialog form').trigger('submit')
    await flushPromises()

    expect(w.get('[data-test="genel-hata"]').attributes('role')).toBe('alert')
    expect(w.get('[data-test="genel-hata"]').text()).toContain('hiçbir değişiklik yapılmadı')
    expect(w.find('[data-test="sonuc"]').exists()).toBe(false)
    expect(w.find('dialog').exists()).toBe(false)
  })

  it('profil açılamazsa hata gösterilir; hazır liste yüklenemezse "Yeniden dene"', async () => {
    vi.mocked(profilApi.hazir).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Sunucu hatası.'),
    )
    const w = await kur()
    await hazirDugme(w, 'ab-2005-47').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="genel-hata"]').text()).toBe('Sunucu hatası.')

    vi.mocked(profilApi.hazirListe).mockRejectedValueOnce(new Error('x'))
    const w2 = await kur()
    expect(w2.text()).toContain('Hazır profiller alınamadı.')
    vi.mocked(profilApi.hazirListe).mockResolvedValue(HAZIR)
    await w2
      .findAll('button')
      .find((b) => b.text() === 'Yeniden dene')!
      .trigger('click')
    await flushPromises()
    expect(w2.findAll('[data-test^="hazir-"]')).toHaveLength(2)
  })

  it('fark yoksa boş durum görünür ve "Hepsini göster" yolu açık kalır', async () => {
    vi.mocked(profilApi.onizle).mockResolvedValue(onizleme([satir('a', 'AYNI')]))
    const w = await kur()
    await hazirDugme(w, 'varsayilan').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('farklı bir şey önermiyor')
    await w.get('[data-test="hepsini-goster"]').setValue(true)
    expect(w.find('[data-test="satir-PARAMETRE:a"]').exists()).toBe(true)
  })

  describe('dosyadan yükleme', () => {
    const dosyaSec = async (w: Awaited<ReturnType<typeof kur>>, f: File) => {
      const girdi = w.get('[data-test="dosya"]').element as HTMLInputElement
      Object.defineProperty(girdi, 'files', { value: [f], configurable: true })
      await w.get('[data-test="dosya"]').trigger('change')
      await flushPromises()
    }

    it('geçerli JSON önizlenir; paket sunucuya olduğu gibi gider', async () => {
      const w = await kur()
      await dosyaSec(
        w,
        new File([JSON.stringify(paket)], 'profil.json', { type: 'application/json' }),
      )
      expect(profilApi.onizle).toHaveBeenCalledWith(paket)
      expect(w.get('#fark-baslik').text()).toContain('Dosya: profil.json')
    })

    it('bozuk JSON, dizi ve 1 MB üstü dosya istemcide reddedilir (sunucuya gitmez)', async () => {
      const w = await kur()
      await dosyaSec(w, new File(['{bozuk'], 'a.json'))
      expect(w.get('#profil-dosya-hata').text()).toContain('geçerli bir JSON')
      await dosyaSec(w, new File(['[1,2]'], 'b.json'))
      expect(w.get('#profil-dosya-hata').text()).toContain('profil dosyası değil')
      await dosyaSec(w, new File([new Uint8Array(1_048_577)], 'c.json'))
      expect(w.get('#profil-dosya-hata').text()).toContain('çok büyük')
      expect(w.get('[data-test="dosya"]').attributes('aria-invalid')).toBe('true')
      expect(profilApi.onizle).not.toHaveBeenCalled()
    })

    it('sunucu dosyayı reddederse (400 GECERSIZ_DOSYA) mesaj dosya alanında', async () => {
      vi.mocked(profilApi.onizle).mockRejectedValue(
        new ApiHata(400, 'GECERSIZ_DOSYA', 'Bu dosya bir Makinist Atama profil dosyası değil.'),
      )
      const w = await kur()
      await dosyaSec(w, new File(['{}'], 'x.json'))
      expect(w.get('#profil-dosya-hata').text()).toContain('profil dosyası değil')
    })
  })

  it('dışa aktar: sunucu dosyası indirilir; hata duyurulur', async () => {
    const tikla = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(tikla)
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    vi.mocked(profilApi.disaAktar).mockResolvedValue({ blob: new Blob(['{}']), ad: 'p.json' })
    const w = await kur()
    await w.get('[data-test="disa-aktar"]').trigger('click')
    await flushPromises()
    expect(tikla).toHaveBeenCalled()

    vi.mocked(profilApi.disaAktar).mockRejectedValue(new ApiHata(403, 'YASAK', 'Yetkiniz yok.'))
    await w.get('[data-test="disa-aktar"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-test="genel-hata"]').text()).toBe('Yetkiniz yok.')
  })
})
