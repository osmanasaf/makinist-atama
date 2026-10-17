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
  it('uygun olmayan satır soluk: aria-disabled, ⛔ simgesi ve TÜM ihlal metinleri görünür; seçilemez', async () => {
    const w = kur([aday({}), engelli])
    const s = satir(w, 1)
    expect(s.attributes('aria-disabled')).toBe('true')
    expect(s.classes()).toContain('soluk')
    expect(s.text()).toContain('⛔')
    expect(s.text()).toContain('Hat yetkisi yok: Ankara–Eskişehir')
    expect(s.text()).toContain('Dinlenme 8 s < 11 s')
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

  it('yalnız UYARI içeren aday seçilebilir ve ⚠ simgesi ile uyarı metni görünür', async () => {
    const w = kur([uyarili])
    const s = satir(w, 0)
    expect(s.attributes('aria-disabled')).toBeUndefined()
    expect(s.text()).toContain('⚠')
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
