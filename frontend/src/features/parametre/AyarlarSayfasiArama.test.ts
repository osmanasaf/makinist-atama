import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
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

import { ApiHata } from '@/api/istemci'
import { kullanici } from '@/composables/oturum'
import AyarlarSayfasi from './AyarlarSayfasi.vue'
import { parametreApi, type AyarDto, type KuralSiddetDto, type ParametreDto } from './parametreApi'

const kural: KuralSiddetDto = {
  kod: 'K-01',
  ad: 'Azami sürüş',
  siddet: 'ENGEL',
  aciklama: 'Günlük azami sürüş',
  kaynak: 'İç yönerge',
  guvenilirlik: 'DUSUK',
  varsayilan: 'ENGEL',
  grup: 'CALISMA',
  sira: 10,
}
const parametre: ParametreDto = {
  anahtar: 'dinlenme.min',
  ad: 'Asgari dinlenme süresi',
  deger: '11',
  tip: 'SAYI',
  altSinir: 8,
  ustSinir: 14,
  birim: 'saat',
  aciklama: 'Asgari dinlenme',
  kaynak: 'Yönerge 4.2',
  guvenilirlik: 'YUKSEK',
  varsayilan: '11',
  grup: 'CALISMA',
  sira: 20,
}
const ayar: AyarDto = {
  anahtar: 'gordum.kalem_gun',
  ad: 'Gördüm kalemi süresi',
  deger: 7,
  birim: 'gün',
  alt: 1,
  ust: 60,
  aciklama: 'Gördüm kalemleri kaç gün listede kalır',
  varsayilan: 7,
  grup: 'MAKINIST',
  sira: 30,
}

const kurulan: VueWrapper[] = []
afterEach(() => kurulan.splice(0).forEach((w) => w.unmount()))

async function kur() {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe', rol: 'YONETICI', aktif: true }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ozel-kurallar', name: 'ozel-kurallar', component: { template: '<div />' } }],
  })
  const w = mount(AyarlarSayfasi, { attachTo: document.body, global: { plugins: [router] } })
  kurulan.push(w)
  await flushPromises()
  return w
}
type Sayfa = Awaited<ReturnType<typeof kur>>
const arama = (w: Sayfa) => w.get<HTMLInputElement>('input[type="search"]')
const adlar = (w: Sayfa) => w.findAll('td.ad-hucre strong').map((s) => s.text())
const durum = (w: Sayfa) => w.get('[data-test="arama-sonuc"]')

describe('AyarlarSayfasi: ayar arama (H6, AK-8.x)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(parametreApi.kurallar).mockResolvedValue([kural])
    vi.mocked(parametreApi.parametreler).mockResolvedValue([parametre])
    vi.mocked(parametreApi.ayarlar).mockResolvedValue([ayar])
  })

  it('AK-8.1: "Ayar ara" etiketli type=search kutusu var', async () => {
    const w = await kur()
    expect(arama(w).attributes('aria-label') ?? w.find('label[for]').text()).toMatch(/Ayar ara/)
    expect(durum(w).attributes('role')).toBe('status')
    expect(durum(w).text()).toBe('') // süzgeç yokken sessiz
  })

  it('AK-8.1: ad, açıklama, anahtar ve K-numarası üzerinde; Türkçe büyük/küçük ve aksan duyarsız', async () => {
    const w = await kur()
    await arama(w).setValue('dinlenme')
    expect(adlar(w)).toEqual(['Asgari dinlenme süresi'])
    await arama(w).setValue('GORDUM') // Gördüm
    expect(adlar(w)).toEqual(['Gördüm kalemi süresi'])
    await arama(w).setValue('k-01') // kural numarası
    expect(adlar(w)).toEqual(['Azami sürüş'])
    await arama(w).setValue('gunluk surus') // açıklama, çok sözcük
    expect(adlar(w)).toEqual(['Azami sürüş'])
    await arama(w).setValue('dinlenme.min') // anahtar
    expect(adlar(w)).toEqual(['Asgari dinlenme süresi'])
    await arama(w).setValue('SÜRESİ')
    expect(adlar(w)).toEqual(['Asgari dinlenme süresi', 'Gördüm kalemi süresi'])
  })

  it('AK-8.1: sonuç sayısı canlı bölgede; sonuç yoksa boş durum + "Aramayı temizle"', async () => {
    const w = await kur()
    await arama(w).setValue('süresi')
    expect(durum(w).text()).toBe('2 sonuç')
    await arama(w).setValue('zzz')
    expect(durum(w).text()).toBe('Sonuç yok')
    expect(w.text()).toContain('“zzz” için ayar bulunamadı.')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Aramayı temizle')!
      .trigger('click')
    expect(arama(w).element.value).toBe('')
    expect(adlar(w).length).toBe(3)
    expect(durum(w).text()).toBe('')
  })

  it('D1/QA-6 (WCAG 2.4.3): "Aramayı temizle" düğmesi kalkınca odak arama kutusuna döner', async () => {
    const w = await kur()
    await arama(w).setValue('zzz')
    const dugme = w.findAll('button').find((b) => b.text() === 'Aramayı temizle')!
    ;(dugme.element as HTMLElement).focus()
    expect(document.activeElement).toBe(dugme.element)
    await dugme.trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(arama(w).element)
  })

  it('AK-8.4: grup gezgini arama sonucuna göre sayar; boşalan grup gezginden kalkar', async () => {
    const w = await kur()
    await arama(w).setValue('dinlenme')
    const nav = w.get('nav[aria-label="Ayar grupları"]')
    expect(nav.findAll('a').map((a) => a.attributes('href'))).toEqual([
      '#grup-CALISMA',
      '#sistem-baslik',
    ])
    expect(nav.get('a').text()).toContain('1')
  })

  it('AK-8.3: "Varsayılandan farklı" ile kesişir', async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([{ ...parametre, deger: '12' }])
    const w = await kur()
    await w.get('.suzgec button').trigger('click') // yalnız farklı
    await arama(w).setValue('azami')
    expect(adlar(w)).toEqual([])
    await arama(w).setValue('dinlenme')
    expect(adlar(w)).toEqual(['Asgari dinlenme süresi'])
  })

  it('AK-8.3 (veri kaybı yok): düzenlenmiş kaydedilmemiş taslak arama dışında kalsa da gizlenmez', async () => {
    const w = await kur()
    await w.get('[aria-label="Asgari dinlenme süresi değeri"]').setValue('12')
    await arama(w).setValue('azami')
    expect(adlar(w)).toEqual(['Azami sürüş', 'Asgari dinlenme süresi'])
    expect(w.find('[aria-label="Asgari dinlenme süresi değişiklik nedeni"]').exists()).toBe(true)
  })

  it('AK-8.3: yalnız neden yazılmış (değer aynı) satır da korunur; "Varsayılandan farklı" süzgeci de taslağı gizlemez', async () => {
    const w = await kur()
    await w.get('[aria-label="Gördüm kalemi süresi değeri"]').setValue('9')
    await w.get('.suzgec button').trigger('click') // hiçbiri farklı değil ama taslak var
    expect(adlar(w)).toEqual(['Gördüm kalemi süresi'])
  })

  it('AK-8.3: hata ya da "Kaydedildi" bilgisi taşıyan satır arama dışında kalsa da görünür', async () => {
    vi.mocked(parametreApi.parametreKaydet).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Kaydedilemedi.'),
    )
    const w = await kur()
    await w.get('[aria-label="Asgari dinlenme süresi değeri"]').setValue('12')
    await w.get('[aria-label="Asgari dinlenme süresi değişiklik nedeni"]').setValue('deneme')
    await w.get('form').trigger('submit')
    await flushPromises()
    await arama(w).setValue('azami')
    expect(adlar(w)).toContain('Asgari dinlenme süresi')
    expect(w.text()).toContain('Kaydedilemedi.')
  })

  it('AK-8.2: 1 yazma + Enter → ilk sonucun değer alanına odak; Esc aramayı temizler', async () => {
    const w = await kur()
    await arama(w).setValue('dinlenme')
    await arama(w).trigger('keydown', { key: 'Enter' })
    expect(document.activeElement).toBe(
      w.get('[aria-label="Asgari dinlenme süresi değeri"]').element,
    )
    await arama(w).trigger('keydown', { key: 'Escape' })
    expect(arama(w).element.value).toBe('')
  })
})
