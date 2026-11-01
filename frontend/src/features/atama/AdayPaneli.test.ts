import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdayPaneli from './AdayPaneli.vue'
import type { Aday } from './atamaApi'

const aday = (ek: Partial<Aday>): Aday => ({
  makinistId: 1,
  sicil: 'D001',
  adSoyad: 'Ali Veli',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 120,
  buAyKm: 8000,
  buSeferdeRol: null,
  ...ek,
})

const engelli = aday({
  makinistId: 2,
  sicil: 'D002',
  adSoyad: 'Engelli Kişi',
  uygun: false,
  ihlaller: [
    {
      kod: 'K-02',
      siddet: 'ENGEL',
      olculen: 0,
      esik: 1,
      birim: '',
      metin: 'Hat yetkisi yok: Ankara–Eskişehir',
    },
    {
      kod: 'K-11',
      siddet: 'ENGEL',
      olculen: 8,
      esik: 11,
      birim: 's',
      metin: 'Dinlenme 8 s < 11 s',
    },
  ],
})
const uyarili = aday({
  makinistId: 3,
  sicil: 'D003',
  adSoyad: 'Uyarılı Kişi',
  ihlaller: [
    { kod: 'K-20', siddet: 'UYARI', olculen: 9, esik: 8, birim: 's', metin: 'Günlük 9 s > 8 s' },
  ],
})
const seferde = aday({ makinistId: 4, sicil: 'D004', adSoyad: 'Zaten Var', buSeferdeRol: 'ASIL' })

function kur(adaylar: Aday[]) {
  return mount(AdayPaneli, { props: { adaylar, rol: 'ASIL' } })
}
const satir = (w: ReturnType<typeof kur>, i: number) => w.findAll('tr[data-aday]')[i]!

describe('AdayPaneli', () => {
  it('uygun olmayan satır soluk: aria-disabled, engel simgesi; ilk ihlal açık, kalanı kapalı ayrıntıda (DOM içinde); seçilemez', async () => {
    const w = kur([aday({}), engelli])
    const s = satir(w, 1)
    expect(s.attributes('aria-disabled')).toBe('true')
    expect(s.classes()).toContain('soluk')
    expect(s.find('svg[data-simge="engel"]').exists()).toBe(true)
    expect(s.find('.rozet-engel').text()).toBe('Uygun değil')
    // GÖRÜNÜR olan: ilk ihlal ayrıntı dışında; ikincisi kapalı <details> içinde (DOM'da var, kullanıcı açana dek görünmez)
    const acikMetin = s.findAll('.ihlal-listesi:not(details *) li').map((li) => li.text())
    expect(acikMetin).toEqual(['Hat yetkisi yok: Ankara–Eskişehir'])
    const ayrinti = s.get('details.ihlal-daha')
    expect((ayrinti.element as HTMLDetailsElement).open).toBe(false)
    expect(ayrinti.text()).toContain('Dinlenme 8 s < 11 s')
    expect(s.text()).toContain('Dinlenme 8 s < 11 s') // jsdom kapalı details içeriğini de verir: bu satır yalnız "DOM'da" demektir
    await s.trigger('click')
    await s.trigger('keydown', { key: 'Enter' })
    expect(w.emitted('sec')).toBeUndefined()
  })

  it('uygun satır seçilebilir: tıklama ve Enter sec olayını verir', async () => {
    const a = aday({})
    const w = kur([a])
    const s = satir(w, 0)
    expect(s.attributes('aria-disabled')).toBeUndefined()
    expect(s.text()).toContain('Ali Veli')
    expect(s.text()).toContain('120')
    expect(s.text()).toContain('8000')
    await s.trigger('click')
    await s.trigger('keydown', { key: 'Enter' })
    expect(w.emitted('sec')).toEqual([[a], [a]])
  })

  it('birden çok ihlalde ilki açık, kalanlar "+N ihlal daha" ayrıntısında (hepsi DOM içinde); ayrıntıyı açmak satırı seçmez', async () => {
    const w = kur([engelli])
    const s = satir(w, 0)
    expect(s.find('.ihlal-listesi li').text()).toContain('Hat yetkisi yok')
    const d = s.get('details.ihlal-daha')
    expect(d.get('summary').text()).toBe('+1 ihlal daha')
    expect(d.text()).toContain('Dinlenme 8 s < 11 s')
    await d.get('summary').trigger('click')
    expect(w.emitted('sec')).toBeUndefined()
    expect(satir(w, 0).find('details').exists()).toBe(true)
  })

  it('W2: "+N ihlal daha" özetinde Enter/Boşluk ayrıntıyı açar, aday SEÇMEZ (olay satıra kabarsa da)', async () => {
    const w = kur([{ ...engelli, uygun: true }]) // seçilebilir olsun: hata varsa sec yayılırdı
    const ozet = satir(w, 0).get('summary')
    const olay = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    ozet.element.dispatchEvent(olay)
    expect(olay.defaultPrevented).toBe(false) // tarayıcının varsayılan eylemi (özeti aç/kapat) engellenmez
    expect(w.emitted('sec')).toBeUndefined()
    const bosluk = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true })
    ozet.element.dispatchEvent(bosluk)
    expect(bosluk.defaultPrevented).toBe(false)
  })

  it('W2: satırın kendisinde Enter seçer; satır içindeki Yolculuk öner düğmesinde Enter satırı seçmez', async () => {
    const konum = aday({
      makinistId: 9,
      adSoyad: 'Konum Uyarılı',
      ihlaller: [{ kod: 'K-20', siddet: 'UYARI', olculen: 0, esik: 0, birim: '', metin: 'Konum' }],
    })
    const w = kur([konum])
    const dugme = satir(w, 0).get('button')
    const olay = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    dugme.element.dispatchEvent(olay)
    expect(olay.defaultPrevented).toBe(false)
    expect(w.emitted('sec')).toBeUndefined()
    await satir(w, 0).trigger('keydown', { key: 'Enter' })
    expect(w.emitted('sec')).toEqual([[konum]])
  })

  it('tek ihlalde "daha" ayrıntısı çizilmez', () => {
    expect(
      satir(kur([uyarili]), 0)
        .find('details')
        .exists(),
    ).toBe(false)
  })

  it('yalnız UYARI içeren aday seçilebilir ve uyarı simgesi ile uyarı metni görünür', async () => {
    const w = kur([uyarili])
    const s = satir(w, 0)
    expect(s.attributes('aria-disabled')).toBeUndefined()
    expect(s.find('svg[data-simge="uyari"]').exists()).toBe(true)
    expect(s.find('.rozet-uyari').text()).toBe('Uyarı var')
    expect(s.text()).toContain('Günlük 9 s > 8 s')
    await s.trigger('click')
    expect(w.emitted('sec')).toEqual([[uyarili]])
  })

  it('bu seferde zaten görevli aday etiketlenir ve seçilemez', async () => {
    const w = kur([seferde])
    const s = satir(w, 0)
    expect(s.text()).toContain('Bu seferde: Asıl')
    expect(s.attributes('aria-disabled')).toBe('true')
    await s.trigger('click')
    expect(w.emitted('sec')).toBeUndefined()
  })

  it('bu seferdeki kondüktör rolü okunur etiketle gösterilir (ham enum değil)', () => {
    const w = kur([aday({ buSeferdeRol: 'KONDUKTOR' })])
    expect(satir(w, 0).text()).toContain('Bu seferde: Kondüktör')
    expect(satir(w, 0).text()).not.toContain('KONDUKTOR')
  })

  it('aday yokken kondüktör rolünde kart açma ipucu çıkar; makinist rolünde çıkmaz', () => {
    const k = mount(AdayPaneli, { props: { adaylar: [], rol: 'KONDUKTOR' } })
    expect(k.text()).toContain('Aday personel yok. Önce Personel sayfasından kondüktör kartı açın.')
    expect(kur([]).text()).not.toContain('kondüktör kartı')
  })

  it('↑/↓ satırlar arasında odak gezdirir ve satırlar tabindex alır', async () => {
    const w = mount(AdayPaneli, {
      props: { adaylar: [aday({}), engelli, uyarili], rol: 'ASIL' },
      attachTo: document.body,
    })
    const satirlar = w.findAll('tr[data-aday]')
    expect(satirlar.map((s) => s.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
    ;(satirlar[0]!.element as HTMLElement).focus()
    await satirlar[0]!.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(satirlar[1]!.element)
    expect(satirlar[1]!.attributes('tabindex')).toBe('0')
    await satirlar[1]!.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(satirlar[0]!.element)
    w.unmount()
  })

  it('rol seçici ASIL/YARDIMCI/KONDUKTOR/YOLCU radyosudur (F2b: Yolcu, F3b: Kondüktör eklendi) ve update:rol yayar', async () => {
    const w = kur([])
    const radyolar = w.findAll('input[type="radio"]')
    expect(radyolar).toHaveLength(4)
    expect((radyolar[0]!.element as HTMLInputElement).checked).toBe(true)
    await radyolar[1]!.setValue(true)
    await radyolar[2]!.setValue(true)
    await radyolar[3]!.setValue(true)
    expect(w.emitted('update:rol')).toEqual([['YARDIMCI'], ['KONDUKTOR'], ['YOLCU']])
  })
})

describe('AdayPaneli: nöbet notu', () => {
  const nobetli = aday({
    makinistId: 9,
    ihlaller: [
      {
        kod: 'K-30',
        siddet: 'ENGEL',
        olculen: 10.95,
        esik: 7.5,
        birim: 's',
        metin: 'Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk',
      },
    ],
    uygun: false,
  })

  it('ihlal metni nöbet içeriyorsa tek açıklama notu görünür', () => {
    const w = mount(AdayPaneli, {
      props: { adaylar: [nobetli, aday({ makinistId: 10 }), nobetli], rol: 'ASIL' },
    })
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
    expect(w.get('[data-test="nobet-notu"]').text()).toContain('4 saatten az')
  })

  it('nöbet geçmiyorsa not görünmez', () => {
    const w = mount(AdayPaneli, { props: { adaylar: [engelli, uyarili], rol: 'ASIL' } })
    expect(w.find('[data-test="nobet-notu"]').exists()).toBe(false)
  })
})

describe('AdayPaneli: Önerilen aday ve ortalamaya fark (O1, OK-35)', () => {
  const metin = (w: ReturnType<typeof kur>) => w.findAll('tr[data-aday]').map((r) => r.text())

  it('"Önerilen" rozeti yalnız ilk temiz adayda (metinli); uyarılı/engelli adayda yok', () => {
    const temiz1 = aday({ makinistId: 10, adSoyad: 'Temiz Bir' })
    const temiz2 = aday({ makinistId: 11, adSoyad: 'Temiz İki' })
    const w = kur([uyarili, engelli, temiz1, temiz2])
    const t = metin(w)
    expect(t.map((x) => x.includes('Önerilen'))).toEqual([false, false, true, false])
    expect(w.findAll('.rozet-basari').filter((r) => r.text() === 'Önerilen')).toHaveLength(1)
  })

  it('temiz aday yoksa hiçbir satırda "Önerilen" yok', () => {
    expect(metin(kur([uyarili, engelli, seferde])).some((x) => x.includes('Önerilen'))).toBe(false)
  })

  it('ASIL: "Fark" sütunu işaretli yüzde; açıklama "aday havuzu ortalamasına göre"', () => {
    const w = kur([
      aday({ makinistId: 1, buAySaat: 90 }),
      aday({ makinistId: 2, buAySaat: 110 }),
      aday({ makinistId: 3, buAySaat: 100 }),
    ])
    expect(w.get('th.kol-fark').attributes('title')).toContain('aday havuzu')
    expect(w.text()).toContain('aday havuzu ortalamasına göre')
    expect(w.findAll('td.aday-fark').map((c) => c.text())).toEqual(['-%10', '+%10', '%0'])
  })

  it('ortalama 0 ise "—"; YOLCU rolünde sütun hiç yok', () => {
    const sifir = kur([aday({ buAySaat: 0 }), aday({ makinistId: 2, buAySaat: 0 })])
    expect(sifir.findAll('td.aday-fark').map((c) => c.text())).toEqual(['—', '—'])
    const yolcu = mount(AdayPaneli, { props: { adaylar: [aday({})], rol: 'YOLCU' } })
    expect(yolcu.find('th.kol-fark').exists()).toBe(false)
    expect(yolcu.find('td.aday-fark').exists()).toBe(false)
    expect(yolcu.text()).not.toContain('ortalaması')
  })

  it('odakla(): Önerilen satırına, yoksa ilk satıra odak verir', () => {
    const temiz = aday({ makinistId: 10, adSoyad: 'Temiz' })
    const w = mount(AdayPaneli, {
      props: { adaylar: [uyarili, temiz], rol: 'ASIL' },
      attachTo: document.body,
    })
    w.vm.odakla()
    expect(document.activeElement).toBe(w.findAll('tr[data-aday]')[1]!.element)
    w.unmount()
    const w2 = mount(AdayPaneli, {
      props: { adaylar: [uyarili, engelli], rol: 'ASIL' },
      attachTo: document.body,
    })
    w2.vm.odakla()
    expect(document.activeElement).toBe(w2.findAll('tr[data-aday]')[0]!.element)
    w2.unmount()
  })

  it('D1/B-01: yükleniyorken (aria-busy) satırlar seçilemez: tık ve Enter sec yaymaz, satır aria-disabled', async () => {
    const w = mount(AdayPaneli, { props: { adaylar: [aday({})], rol: 'ASIL', yukleniyor: true } })
    expect(satir(w, 0).attributes('aria-disabled')).toBe('true')
    await satir(w, 0).trigger('click')
    await satir(w, 0).trigger('keydown', { key: 'Enter' })
    expect(w.emitted('sec')).toBeUndefined()
    await w.setProps({ yukleniyor: false })
    expect(satir(w, 0).attributes('aria-disabled')).toBeUndefined()
    await satir(w, 0).trigger('click')
    expect(w.emitted('sec')).toHaveLength(1)
  })
})
