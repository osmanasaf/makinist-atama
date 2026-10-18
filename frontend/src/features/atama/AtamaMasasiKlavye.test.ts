/** QA BUG-2: masada seçili satırdan aday paneline kısa yol; satır başına tek Tab durağı. BUG-11: kaydırma hareket tercihine uyar. */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { gun: vi.fn(), adaylar: vi.fn(), kirmiziSayisi: vi.fn(), gordumOzeti: vi.fn() },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const SATIR_SAYISI = 100

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2026-10-12')
  const w = mount(AtamaMasasi, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return w
}

/** Sekme sırasına giren (tabindex >= 0, devre dışı olmayan) etkileşimli öğeler. */
const sekmeDuraklari = (kok: Element) =>
  [
    ...kok.querySelectorAll<HTMLElement>(
      'button, a[href], input, select, tr[tabindex], [tabindex]',
    ),
  ].filter((e) => e.tabIndex >= 0 && !(e as HTMLButtonElement).disabled)

describe('AtamaMasasi: klavyeyle gezinme', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(atamaApi.gun).mockResolvedValue({
      tarih: '2026-10-12',
      uretimZamani: '2026-10-12T07:00:00Z',
      satirlar: Array.from({ length: SATIR_SAYISI }, (_, i) =>
        satir(i + 1, { makinistler: [makinist({ atamaId: i + 1, makinistId: i + 1 })] }),
      ),
    })
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.gordumOzeti).mockResolvedValue({
      bas: '2026-10-12',
      bit: '2026-10-19',
      toplam: 0,
      gorulen: 0,
      gorulmeyen: 0,
      hesapsiz: 0,
      uyari: 0,
    })
  })

  it('tablo tek sekme durağıdır: 100 satırda yalnız etkin satır ve eylemleri sıraya girer (önce 300+)', async () => {
    const w = await kur()
    const tablo = w.get('table.masa-tablo').element
    const duraklar = sekmeDuraklari(tablo)
    expect(w.findAll('tr.sefer-satiri')).toHaveLength(SATIR_SAYISI)
    // etkin satır + onun Kaldır/Gecikme/İptal/Böl/Yerine ata düğmeleri
    expect(duraklar.length).toBeLessThanOrEqual(8)
    expect(duraklar[0]!.tagName).toBe('TR')
    // eylemler fareyle erişilebilir kalır: tabindex -1 olsa da düğme var ve tıklanır
    expect(
      w.findAll('tr.sefer-satiri')[50]!.find('button[aria-label$="seferine gecikme gir"]').exists(),
    ).toBe(true)
  })

  it('↓ etkin satırı taşır: yeni satır tabindex 0, eskisi -1', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')
    ;(satirlar[0]!.element as HTMLElement).focus()
    await satirlar[0]!.trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    expect(document.activeElement).toBe(satirlar[1]!.element)
    expect(satirlar[1]!.attributes('tabindex')).toBe('0')
    expect(satirlar[0]!.attributes('tabindex')).toBe('-1')
    expect(satirlar[1]!.find('button').attributes('tabindex')).toBe('0')
  })

  it('seçili satırda → : aday panelinin başlığına odak (tek tuş, ~300 Tab yerine)', async () => {
    const w = await kur()
    const satirlar = w.findAll('tr.sefer-satiri')
    ;(satirlar[40]!.element as HTMLElement).focus()
    await satirlar[40]!.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    const baslik = w.get('aside h2')
    expect(baslik.text()).toContain('Adaylar:')
    expect(baslik.attributes('tabindex')).toBe('-1')
    expect(document.activeElement).toBe(baslik.element)
    expect(satirlar[40]!.classes()).toContain('secili')
  })

  it('"Adaylara geç" düğmesi yalnız sefer seçiliyken görünür ve panel başlığına odak verir', async () => {
    const w = await kur()
    expect(w.find('[data-test="adaylara-gec"]').exists()).toBe(false)
    await w.findAll('tr.sefer-satiri')[2]!.trigger('click')
    const dugme = w.get('[data-test="adaylara-gec"]')
    expect(dugme.text()).toBe('Adaylara geç')
    await dugme.trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(w.get('aside h2').element)
  })

  it('YENİ-4: → kısayolu keşfedilebilir: satırda aria-keyshortcuts, görünür ipucu metni', async () => {
    const w = await kur()
    expect(w.findAll('tr.sefer-satiri')[0]!.attributes('aria-keyshortcuts')).toBe('ArrowRight')
    expect(w.get('.ipucu').text()).toContain('→')
    await w.findAll('tr.sefer-satiri')[2]!.trigger('click')
    expect(w.get('[data-test="sag-ok-ipucu"]').text()).toContain('→')
  })

  it('satırın içindeki düğmede → paneli açmaz (yalnız satırın kendisinde)', async () => {
    const w = await kur()
    const dugme = w.findAll('tr.sefer-satiri')[0]!.get('button[aria-label$="seferine gecikme gir"]')
    await dugme.trigger('keydown', { key: 'ArrowRight' })
    expect(w.find('aside h2').exists()).toBe(false)
  })

  it('BUG-11: kaydırma hareket azaltma tercihine uyar (smooth değil auto)', async () => {
    const kaydir = vi.fn()
    Element.prototype.scrollIntoView = kaydir
    vi.stubGlobal('matchMedia', (q: string) => ({
      matches: q.includes('reduce') || q.includes('max-width'),
      addEventListener() {},
      removeEventListener() {},
    }))
    const w = await kur()
    await w.findAll('tr.sefer-satiri')[0]!.trigger('click') // dar ekran: panele kaydırır
    await flushPromises()
    expect(kaydir).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'auto' }))
    vi.unstubAllGlobals()
  })
})
