import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./raporApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./raporApi')>()),
  raporApi: { kadro: vi.fn(), kadroExcel: vi.fn() },
}))
vi.mock('@/features/tanim/tanimApi', () => ({
  tanimApi: { liste: vi.fn(async () => [{ id: 1, tip: 'X', ad: 'Ankara' }]) },
}))

import KadroTahmini from './KadroTahmini.vue'
import { raporApi, type KadroRaporu } from './raporApi'

/** Yalnız ekran durumları için asgari yanıt (davranış ayrıntısı KadroTahmini.test.ts'te). */
const rapor = (): KadroRaporu =>
  ({
    varsayim: {
      bas: '2026-10-12',
      bit: '2026-11-08',
      hafta: 4,
      devamsizlikYuzde: 13,
      kisiBasinaSaat: 47.85,
      yedekOran: 0.1,
      yedekMin: 1,
      haftalikTalepSaat: 2201.3,
    },
    talep: [],
    garlar: [],
    analitik: {
      gerekliAltSinir: 47,
      tekBroveliGerekli: 49,
      mevcutAsil: 66,
      eksikAltSinir: 0,
      siniflar: [],
    },
    kumeler: [],
    kumeKaynagi: 'PLAN',
    yedek: null,
    senaryo: null,
    simulasyon: {
      mevcutAsil: 66,
      mevcut: { ad: 'm', etiket: 'm', seferSayisi: 2800, bos: 122, dolulukYuzde: 95.64, sureMs: 1 },
      konumsuz: null,
      iyilestirmeli: null,
      yolculuk: null,
      egri: [
        { ek: 0, bos: 122, dolulukYuzde: 95.64, marjinal: null },
        { ek: 7, bos: 44, dolulukYuzde: 98.43, marjinal: 11.14 },
        { ek: 64, bos: 3, dolulukYuzde: 99.89, marjinal: 0.48 },
      ],
      hedefler: [
        { yuzde: 98, ulasildi: true, ek: 7, bos: 44, dolulukYuzde: 98.43 },
        { yuzde: 99, ulasildi: true, ek: 0, bos: 28, dolulukYuzde: 99 },
        { yuzde: 100, ulasildi: false, ek: null, bos: 3, dolulukYuzde: 99.89 },
      ],
      egriDurma: 'EK_SINIRI',
      egriKosuSayisi: 14,
      dagilimEk: 7,
      dagilimEtiketi: 'x',
      dagilim: [],
      kalanBoslar: [],
      kalanBosToplam: 0,
      kalanNedenler: [],
      atlananlar: [],
      tamamlandi: true,
      sureMs: 1000,
    },
  }) as unknown as KadroRaporu

describe('KadroTahmini: ekran durumları ve form kuralları (E2)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('ilk açılış: tek h1 ve boş durum; sonuç yok', () => {
    const w = mount(KadroTahmini)
    expect(w.findAll('h1')).toHaveLength(1)
    expect(w.get('[data-test="kadro-bos"]').classes()).toContain('veri-durumu-bos')
    expect(w.find('[data-test="baslik"]').exists()).toBe(false)
  })

  it('zorunlu alanlar .alan-zorunlu işaretli', () => {
    const w = mount(KadroTahmini)
    for (const id of ['kt-bas', 'kt-hafta', 'kt-devamsizlik', 'kt-sure']) {
      expect(w.get(`#${id}`).element.closest('.alan')!.classList).toContain('alan-zorunlu')
    }
  })

  it('hesaplanıyor: VeriDurumu yükleniyor (role=status) + geçen süre; bitince kalkar', async () => {
    let bitir: (r: KadroRaporu) => void = () => {}
    vi.mocked(raporApi.kadro).mockReturnValue(new Promise((coz) => (bitir = coz)))
    const w = mount(KadroTahmini)
    await w.get('[data-test=hesapla]').trigger('click')
    const durum = w.get('[data-test=hesaplaniyor]')
    expect(durum.classes()).toContain('veri-durumu-yukleniyor')
    expect(durum.attributes('role')).toBe('status')
    expect(w.get('[data-test=gecen-sure]').text()).toMatch(/^Geçen süre: \d+ sn$/)
    expect(w.find('[data-test="kadro-bos"]').exists()).toBe(false)
    bitir(rapor())
    await flushPromises()
    expect(w.find('[data-test=hesaplaniyor]').exists()).toBe(false)
  })

  it('sonuç: 4 özet kartı (mevcut + %98/%99/%100), eğri kabında SVG grafik + tablo, ayrıntılar katlanır', async () => {
    vi.mocked(raporApi.kadro).mockResolvedValue(rapor())
    const w = mount(KadroTahmini)
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    const kartlar = w.findAll('[data-test="baslik"] article.ozet-kart')
    expect(kartlar).toHaveLength(4)
    expect(kartlar[0]!.get('.ozet-deger').text()).toBe('%95,64')
    expect(kartlar[1]!.get('.ozet-deger').text()).toBe('+7')
    expect(kartlar[2]!.text()).toContain('ek gerekmiyor')
    expect(kartlar[3]!.classes()).toContain('ozet-uyari')
    expect(kartlar[3]!.find('svg[data-simge="uyari"]').exists()).toBe(true)
    expect(kartlar[3]!.text()).toContain('Ulaşılamadı')
    const egri = w.get('[data-test=egri]')
    expect(egri.find('svg[role="img"] path').exists()).toBe(true)
    expect(egri.findAll('details table tbody tr')).toHaveLength(3)
    const ayrintilar = w.findAll('details.ayrinti')
    expect(ayrintilar.length).toBeGreaterThanOrEqual(4)
    expect(w.get('[data-test=analitik]').attributes('open')).toBeUndefined()
  })

  it('hesap hatası VeriDurumu hata (role=alert); sonuç yok', async () => {
    vi.mocked(raporApi.kadro).mockRejectedValue(new ApiHata(500, 'SUNUCU', 'Hesap yapılamadı.'))
    const w = mount(KadroTahmini)
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    const h = w.get('.veri-durumu-hata')
    expect(h.attributes('role')).toBe('alert')
    expect(h.text()).toBe('Hesap yapılamadı.')
    expect(w.find('[data-test="kadro-bos"]').exists()).toBe(false)
  })

  it('sanal sefer hatası satırın altında (.alan-hata), geçersiz alanlar aria-invalid + aria-describedby, odak ilk geçersize', async () => {
    const w = mount(KadroTahmini, { attachTo: document.body })
    await flushPromises()
    await w.get('[data-test=satir-ekle]').trigger('click')
    const sec = w.findAll('[data-test=sanal-satir] select')
    await sec[0]!.setValue(1) // hat dolu, diğerleri boş
    await w.get('[data-test=hesapla]').trigger('click')
    await flushPromises()
    const hata = w.get('[data-test=satir-hata]')
    expect(hata.classes()).toContain('alan-hata')
    expect(hata.attributes('role')).toBe('alert')
    const hatId = hata.attributes('id')!
    expect(w.get('select[aria-label="Hat 1"]').attributes('aria-invalid')).toBeUndefined()
    const arac = w.get('select[aria-label="Araç tipi 1"]')
    expect(arac.attributes('aria-invalid')).toBe('true')
    expect(arac.attributes('aria-describedby')).toBe(hatId)
    expect(w.get('input[aria-label="Kalkış saatleri 1"]').attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(arac.element)
    expect(raporApi.kadro).not.toHaveBeenCalled()
    w.unmount()
  })
})
