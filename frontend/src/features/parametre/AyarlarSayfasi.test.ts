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

import { ApiHata } from '@/api/istemci'
import type { Rol } from '@/api/tipler'
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
const bayrak: ParametreDto = {
  ...parametre,
  anahtar: 'nobet.bekleme_sayilir',
  ad: 'Bekleme sayılır',
  deger: '0',
  varsayilan: '0',
  birim: '0/1',
  altSinir: 0,
  ustSinir: 1,
  aciklama: 'Bekleme çalışma sayılır mı',
  sira: 30,
}

function girisYap(rol: Rol) {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
}

async function kur(rol: Rol = 'YONETICI') {
  girisYap(rol)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ozel-kurallar', name: 'ozel-kurallar', component: { template: '<div />' } }],
  })
  const w = mount(AyarlarSayfasi, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

const neden = (w: Awaited<ReturnType<typeof kur>>, ad: string) =>
  w.get(`[aria-label="${ad} değişiklik nedeni"]`)

describe('AyarlarSayfasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(parametreApi.kurallar).mockResolvedValue([kural])
    vi.mocked(parametreApi.parametreler).mockResolvedValue([parametre])
    vi.mocked(parametreApi.ayarlar).mockResolvedValue([ayar])
  })

  it('karar destek notu ve güvenilirlik etiketi görünür', async () => {
    const w = await kur()
    expect(w.text()).toContain(
      'Bu uygulama karar destek aracıdır; mevzuata uyum garantisi vermez. Eşikler ve şiddetler sizin sorumluluğunuzdadır.',
    )
    expect(w.text()).toContain('Güvenilirlik: Düşük')
    expect(w.text()).toContain('Güvenilirlik: Yüksek')
  })

  it('özel kurallar bu sayfada değil; üstte kendi sayfasına bağlantı var', async () => {
    const w = await kur()
    expect(w.find('#ozel-kural-baslik').exists()).toBe(false)
    expect(w.get('a[href="/ozel-kurallar"]').text()).toBe('Özel kurallar')
  })

  it('gruplar sırayla başlıklanır; her grupta önce şiddetler sonra değerler gelir', async () => {
    vi.mocked(parametreApi.kurallar).mockResolvedValue([
      kural,
      { ...kural, kod: 'K-99', grup: 'GRUP_X', sira: 1 },
    ])
    const w = await kur()
    expect(w.findAll('h2').map((h) => h.text())).toEqual([
      'Çalışma ve dinlenme',
      'Makinist görünümü',
      'GRUP_X',
      'Sistem',
    ])
    const ilkTablo = w.findAll('table')[0]!
    expect(ilkTablo.findAll('tbody tr strong').map((c) => c.text())).toEqual([
      'Azami sürüş',
      'Asgari dinlenme süresi',
    ])
  })

  it('satırda ad, açıklama, birim, varsayılan, sınır, kaynak ve uygulama ayarı için "Uygulama ayarı" görünür', async () => {
    const w = await kur()
    const t = w.text()
    expect(t).toContain('Asgari dinlenme')
    expect(t).toContain('Sınır: 8 – 14')
    expect(t).toContain('Yönerge 4.2')
    expect(t).toContain('11 saat')
    expect(t).toContain('Gördüm kalemleri kaç gün listede kalır')
    expect(t).toContain('Uygulama ayarı')
    expect(t).not.toContain('dinlenme.min') // ham anahtar görünmez
    expect(t).toContain('Kural K-01')
    expect(t).toContain('7 gün')
    expect(w.findAll('.guven')).toHaveLength(2) // uygulama ayarında güvenilirlik rozeti yok
  })

  it('varsayılandan farklı satır metinle de işaretlenir', async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([{ ...parametre, deger: '12' }])
    const w = await kur()
    expect(w.findAll('.farkli-etiket')).toHaveLength(1)
    expect(w.get('.farkli-etiket').text()).toBe('Varsayılandan farklı')
    expect(w.get('tr.farkli').text()).toContain('Asgari dinlenme süresi')
  })

  it('Kaydet pasifken nedeni söylenir; neden girilince ipucu kalkar', async () => {
    const w = await kur()
    await w.get('[aria-label="Azami sürüş şiddeti"]').setValue('UYARI')
    expect(w.get('.pasif-neden').text()).toBe('Kaydetmek için değişiklik nedenini yazın.')
    expect(neden(w, 'Azami sürüş').attributes('placeholder')).toContain('zorunlu')
    await neden(w, 'Azami sürüş').setValue('Denetim')
    expect(w.find('.pasif-neden').exists()).toBe(false)
  })

  it('sayısal eşitlik: tohum "0.10", sunucu "0.1" yazsa da "Varsayılandan farklı" çıkmaz', async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([
      { ...parametre, anahtar: 'yedek.oran', ad: 'Yedek oranı', deger: '0.1', varsayilan: '0.10' },
    ])
    const w = await kur()
    expect(w.find('.farkli-etiket').exists()).toBe(false)
    expect(w.get('[aria-label="Yedek oranı varsayılana dön"]').attributes('disabled')).toBeDefined()
    await w.get('[aria-label="Yedek oranı değeri"]').setValue('0.10')
    expect(w.find('form').exists()).toBe(false) // aynı sayı = değişiklik değil
    await w.get('[aria-label="Yedek oranı değeri"]').setValue('0.2')
    expect(w.find('form').exists()).toBe(true)
  })

  it('K-23 ve K-14 şiddet satırları kendi ayarlarının hemen üstünde durur', async () => {
    vi.mocked(parametreApi.kurallar).mockResolvedValue([
      kural,
      { ...kural, kod: 'K-23', ad: 'Aktarma kuralı', sira: 30 },
      { ...kural, kod: 'K-14', ad: 'Haftalık kural', sira: 50 },
    ])
    vi.mocked(parametreApi.parametreler).mockResolvedValue([
      parametre,
      { ...parametre, anahtar: 'nobet.asgari_aktarma_dk', ad: 'Aktarma süresi', sira: 50 },
      { ...parametre, anahtar: 'haftalik.normal_saat', ad: 'Haftalık normal', sira: 70 },
    ])
    const w = await kur()
    const adlar = w
      .findAll('table')[0]!
      .findAll('tbody tr strong')
      .map((c) => c.text())
    expect(adlar).toEqual([
      'Azami sürüş',
      'Asgari dinlenme süresi',
      'Aktarma kuralı',
      'Aktarma süresi',
      'Haftalık kural',
      'Haftalık normal',
    ])
  })

  it('kaynak kısaltmaları açık yazılır; iç kod göndermesi yok', async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([
      { ...parametre, kaynak: '[Y] 4857 md. 63; [V] kaynaksız varsayım' },
    ])
    const w = await kur()
    expect(w.text()).toContain('Yasa/yönetmelik: 4857 md. 63; Kaynaksız varsayım')
    expect(w.text()).not.toMatch(/\[[YTİEV]\]|MK-|SON-QA|429/)
    expect(w.text()).toContain('Güvenilirlik, kaynağın ne kadar kesin olduğunu gösterir')
  })

  it('neden boşken Kaydet kapalıdır, neden girilince açılır', async () => {
    const w = await kur()
    await w.get('[aria-label="Azami sürüş şiddeti"]').setValue('UYARI')
    expect(w.get('form button').attributes('disabled')).toBeDefined()
    await neden(w, 'Azami sürüş').setValue('  Denetim sonucu ')
    expect(w.get('form button').attributes('disabled')).toBeUndefined()
  })

  it('kural kaydı doğru gövdeyle gider, satır güncellenir ve geçerlilik notu görünür', async () => {
    vi.mocked(parametreApi.kuralKaydet).mockResolvedValue({ ...kural, siddet: 'UYARI' })
    const w = await kur()
    await w.get('[aria-label="Azami sürüş şiddeti"]').setValue('UYARI')
    await neden(w, 'Azami sürüş').setValue(' Denetim sonucu ')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.kuralKaydet).toHaveBeenCalledWith('K-01', {
      siddet: 'UYARI',
      neden: 'Denetim sonucu',
    })
    expect(parametreApi.parametreKaydet).not.toHaveBeenCalled()
    expect(parametreApi.ayarKaydet).not.toHaveBeenCalled()
    expect(w.text()).toContain('Kaydedildi.')
    expect(w.text()).toContain(
      'Geçerli: hemen. Mevcut atamalar yeni kurallara göre yeniden değerlendirilir; kırmızı olanlar Atama masasında görünür.',
    )
    expect(w.find('form').exists()).toBe(false)
    expect(w.get('tr.farkli').text()).toContain('Azami sürüş')
  })

  it('parametre kaydı doğru gövdeyle gider', async () => {
    vi.mocked(parametreApi.parametreKaydet).mockResolvedValue({ ...parametre, deger: '12' })
    const w = await kur()
    await w.get('[aria-label="Asgari dinlenme süresi değeri"]').setValue('12')
    await neden(w, 'Asgari dinlenme süresi').setValue('Yeni yönerge')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.parametreKaydet).toHaveBeenCalledWith('dinlenme.min', {
      deger: '12',
      neden: 'Yeni yönerge',
    })
    expect(parametreApi.kuralKaydet).not.toHaveBeenCalled()
    expect(w.text()).toContain('Kaydedildi.')
    expect(
      (w.get('[aria-label="Asgari dinlenme süresi değeri"]').element as HTMLInputElement).value,
    ).toBe('12')
  })

  it('uygulama ayarı /api/ayar ucuna sayı olarak gider', async () => {
    vi.mocked(parametreApi.ayarKaydet).mockResolvedValue({ ...ayar, deger: 10 })
    const w = await kur()
    await w.get('[aria-label="Gördüm kalemi süresi değeri"]').setValue('10')
    await neden(w, 'Gördüm kalemi süresi').setValue('Daha uzun görünsün')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.ayarKaydet).toHaveBeenCalledWith('gordum.kalem_gun', {
      deger: 10,
      neden: 'Daha uzun görünsün',
    })
    expect(parametreApi.parametreKaydet).not.toHaveBeenCalled()
    expect(w.text()).toContain('Kaydedildi.')
  })

  it('uygulama ayarına tam sayı olmayan değer istemciden de reddedilir', async () => {
    const w = await kur()
    await w.get('[aria-label="Gördüm kalemi süresi değeri"]').setValue('5.5')
    await neden(w, 'Gördüm kalemi süresi').setValue('Deneme')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.ayarKaydet).not.toHaveBeenCalled()
    expect(w.text()).toContain('Değer tam sayı olmalı.')
  })

  it("birim '0/1' olan satır açma/kapama onay kutusudur ve '1'/'0' yazar", async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([bayrak])
    vi.mocked(parametreApi.parametreKaydet).mockResolvedValue({ ...bayrak, deger: '1' })
    const w = await kur()
    const kutu = w.get('input[type="checkbox"][aria-label="Bekleme sayılır değeri"]')
    expect((kutu.element as HTMLInputElement).checked).toBe(false)
    await kutu.setValue(true)
    await neden(w, 'Bekleme sayılır').setValue('Bekleme sayılsın')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.parametreKaydet).toHaveBeenCalledWith('nobet.bekleme_sayilir', {
      deger: '1',
      neden: 'Bekleme sayılsın',
    })
  })

  it('varsayılana dön: değer zaten varsayılansa pasif; değilse taslak ve neden dolar, kayıt mevcut PUT ile gider', async () => {
    vi.mocked(parametreApi.parametreler).mockResolvedValue([{ ...parametre, deger: '12' }])
    vi.mocked(parametreApi.parametreKaydet).mockResolvedValue(parametre)
    const w = await kur()
    // varsayılan olan satır (K-01, uygulama ayarı) pasif
    expect(w.get('[aria-label="Azami sürüş varsayılana dön"]').attributes('disabled')).toBeDefined()
    expect(
      w.get('[aria-label="Gördüm kalemi süresi varsayılana dön"]').attributes('disabled'),
    ).toBeDefined()
    const dugme = w.get('[aria-label="Asgari dinlenme süresi varsayılana dön"]')
    expect(dugme.attributes('disabled')).toBeUndefined()

    await dugme.trigger('click')
    expect(
      (w.get('[aria-label="Asgari dinlenme süresi değeri"]').element as HTMLInputElement).value,
    ).toBe('11')
    const nedenAlani = neden(w, 'Asgari dinlenme süresi')
    expect((nedenAlani.element as HTMLInputElement).value).toBe('Varsayılana döndürüldü')
    await nedenAlani.setValue('Varsayılana döndürüldü: yönerge değişti') // düzenlenebilir
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.parametreKaydet).toHaveBeenCalledWith('dinlenme.min', {
      deger: '11',
      neden: 'Varsayılana döndürüldü: yönerge değişti',
    })
    expect(w.find('.farkli-etiket').exists()).toBe(false)
    expect(
      w.get('[aria-label="Asgari dinlenme süresi varsayılana dön"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('varsayılana dön şiddet satırında da çalışır', async () => {
    vi.mocked(parametreApi.kurallar).mockResolvedValue([{ ...kural, siddet: 'KAPALI' }])
    vi.mocked(parametreApi.kuralKaydet).mockResolvedValue(kural)
    const w = await kur()
    await w.get('[aria-label="Azami sürüş varsayılana dön"]').trigger('click')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(parametreApi.kuralKaydet).toHaveBeenCalledWith('K-01', {
      siddet: 'ENGEL',
      neden: 'Varsayılana döndürüldü',
    })
  })

  it('sunucu alan hatası satırın altında gösterilir', async () => {
    vi.mocked(parametreApi.parametreKaydet).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Girilen bilgilerde hata var.', {
        alanlar: { deger: 'Değer 8 ile 14 arasında olmalı.' },
      }),
    )
    const w = await kur()
    await w.get('[aria-label="Asgari dinlenme süresi değeri"]').setValue('99')
    await neden(w, 'Asgari dinlenme süresi').setValue('Deneme')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Değer 8 ile 14 arasında olmalı.')
    expect(w.text()).not.toContain('Girilen bilgilerde hata var.') // genel mesaj, alan mesajı varken gösterilmez
    // hata sonrası düzenleme sürer: Kaydet yeniden açık
    expect(w.get('form button').attributes('disabled')).toBeUndefined()
  })

  it('sunucu neden hatası da satırda gösterilir', async () => {
    vi.mocked(parametreApi.ayarKaydet).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Girilen bilgilerde hata var.', {
        alanlar: { neden: 'Değişiklik nedeni yazılmalı.' },
      }),
    )
    const w = await kur()
    await w.get('[aria-label="Gördüm kalemi süresi değeri"]').setValue('10')
    await neden(w, 'Gördüm kalemi süresi').setValue('x')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('Değişiklik nedeni yazılmalı.')
  })

  it('çapraz ayar uyarıları gösterilir ve kaydı engellemez', async () => {
    vi.mocked(parametreApi.kurallar).mockResolvedValue([
      kural,
      { ...kural, kod: 'K-14', siddet: 'KAPALI', varsayilan: 'KAPALI', sira: 40 },
    ])
    vi.mocked(parametreApi.parametreler).mockResolvedValue([
      { ...parametre, anahtar: 'haftalik.normal_saat', deger: '48', varsayilan: '48', sira: 50 },
      { ...parametre, anahtar: 'haftalik.max_saat', deger: '45', varsayilan: '45', sira: 60 },
    ])
    const w = await kur()
    const bilgi = w.get('[data-uyari="HAFTALIK_ASGARI"]')
    expect(bilgi.text()).toContain('Bilgi:')
    expect(bilgi.text()).toContain('K-14 kapalıyken kullanılmaz')
    expect(w.find('[data-uyari="PUANTAJ_GECE_BITIS"]').exists()).toBe(false)
  })

  it('uyarı yoksa uyarı bloğu çizilmez', async () => {
    const w = await kur()
    expect(w.find('[data-uyari]').exists()).toBe(false)
  })

  it('Sistem bloğu salt okunur sınırları gösterir (iç kod adı yok)', async () => {
    const w = await kur()
    const sistem = w.get('[aria-labelledby="sistem-baslik"]')
    const t = sistem.text()
    expect(t).toContain('Güvenlik ve performans içindir; değiştirmek için geliştirici gerekir.')
    for (const s of [
      '5 hatalı denemede 15 dakika',
      'En az 10 karakter',
      '8 saat',
      'En çok 5 MB ve 20.000 satır',
      'En çok 62 gün',
      'En çok 50 aktif kural',
    ]) {
      expect(t).toContain(s)
    }
    for (const kod of [
      'KullaniciServisi',
      'application.yml',
      'SeferExcelOkuyucu',
      'Koddaki yeri',
    ]) {
      expect(t).not.toContain(kod) // iç kod adları yönetici ekranında yok
    }
    expect(sistem.find('input, select, button').exists()).toBe(false)
  })

  it('yönetici değilse alanlar salt okunurdur, uygulama ayarları istenmez, düğme ve form yoktur', async () => {
    const w = await kur('ATAMACI')
    expect(parametreApi.ayarlar).not.toHaveBeenCalled()
    expect(w.get('[aria-label="Azami sürüş şiddeti"]').attributes('disabled')).toBeDefined()
    expect(
      w.get('[aria-label="Asgari dinlenme süresi değeri"]').attributes('readonly'),
    ).toBeDefined()
    expect(w.find('form').exists()).toBe(false)
    expect(w.text()).not.toContain('Varsayılana dön')
  })

  it('yükleme hatası uyarı olarak gösterilir', async () => {
    vi.mocked(parametreApi.ayarlar).mockRejectedValue(
      new ApiHata(500, 'SUNUCU_HATASI', 'Beklenmeyen bir hata oluştu. Tekrar deneyin.'),
    )
    const w = await kur()
    expect(w.get('[role="alert"]').text()).toContain('Beklenmeyen bir hata oluştu')
  })
})
