// H3 (MK-148, AK-2.1..2.6): satırda tek birincil eylem + ⋯ menüsü; İptal ayrı; İPTAL satırında kontrol yok.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: {
    gun: vi.fn(),
    adaylar: vi.fn(),
    kirmiziSayisi: vi.fn(),
    gordumOzeti: vi.fn(),
    seferGecikme: vi.fn(),
  },
}))

import AtamaMasasi from './AtamaMasasi.vue'
import { atamaApi, type GunListesi } from './atamaApi'
import { makinist, satir } from './testVerisi'

const bos = { template: '<div />' }
const GELECEK = '2099-01-01T05:00:00Z'

const liste = (satirlar = 0): GunListesi => ({
  tarih: '2099-01-01',
  uretimZamani: '2099-01-01T00:00:00Z',
  satirlar: satirlar
    ? Array.from({ length: satirlar }, (_, i) => satir(i + 1, { guncelKalkis: GELECEK }))
    : [
        satir(1, { guncelKalkis: GELECEK }), // boş
        satir(2, { guncelKalkis: GELECEK, makinistler: [makinist()] }), // atanmış, parçasız: Böl var
        satir(3, { guncelKalkis: GELECEK, parcaNo: 1, parcaSayisi: 2 }), // parçalı: Birleştir var
        satir(4, {
          guncelKalkis: GELECEK,
          durum: 'IPTAL',
          makinistler: [makinist({ atamaId: 11 })],
        }),
      ],
})

async function kur(l: GunListesi) {
  vi.mocked(atamaApi.gun).mockResolvedValue(l)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/atama', name: 'atama', component: bos },
      { path: '/yazdir/gun/:tarih', name: 'yazdir-gun', component: bos },
    ],
  })
  await router.push('/atama?tarih=2099-01-01')
  const w = mount(AtamaMasasi, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return w
}
type W = Awaited<ReturnType<typeof kur>>
const satirlar = (w: W) => w.findAll('tr.sefer-satiri')
const menuAc = async (w: W, i: number) => {
  await satirlar(w)[i]!.get('button[aria-haspopup="menu"]').trigger('click')
  return satirlar(w)[i]!.findAll('[role="menuitem"]')
}

describe('AtamaMasasi: satır eylemleri (⋯ menüsü)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(atamaApi.kirmiziSayisi).mockResolvedValue({
      bas: '2099-01-01',
      bit: '2099-01-08',
      kirmiziSefer: 0,
      kirmiziAtama: 0,
    })
    vi.mocked(atamaApi.adaylar).mockResolvedValue([])
  })
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('AK-2.1: satır başına görünür işlem kontrolü ≤ 2 (birincil + ⋯); menü kapalıyken öğe yok', async () => {
    const w = await kur(liste())
    for (const i of [0, 1, 2]) {
      const kontroller = satirlar(w)[i]!.findAll('.islem-dugmeleri button')
      expect(kontroller).toHaveLength(2)
      expect(kontroller[1]!.attributes('aria-haspopup')).toBe('menu')
      expect(satirlar(w)[i]!.findAll('[role="menuitem"]')).toHaveLength(0)
    }
  })

  it('boş satırda birincil "Ata", atanmış satırda "Yerine ata" (metinli, görünür)', async () => {
    const w = await kur(liste())
    const ata = satirlar(w)[0]!.get('.islem-dugmeleri button')
    expect(ata.text()).toBe('Ata')
    expect(ata.classes()).toContain('btn-birincil')
    expect(ata.attributes('aria-label')).toBe('T1001 seferine ata')
    const yerine = satirlar(w)[1]!.get('.islem-dugmeleri button')
    expect(yerine.text()).toBe('Yerine ata')
    expect(yerine.attributes('aria-label')).toBe('T1002 seferinde yerine ata')
  })

  it('AK-2.3: "Ata" satırı seçer ve odağı aday paneli başlığına taşır', async () => {
    const w = await kur(liste())
    await satirlar(w)[0]!.get('.islem-dugmeleri button').trigger('click')
    await flushPromises()
    expect(satirlar(w)[0]!.classes()).toContain('secili')
    expect(document.activeElement).toBe(w.get('aside h2').element)
  })

  it('AK-2.2: menü içeriği koşullu — Gecikme, Böl (parçasız), Birleştir (parçalı); İptal en sonda ve ayrı', async () => {
    const w = await kur(liste())
    const ad = (o: { text: () => string }[]) => o.map((x) => x.text())
    expect(ad(await menuAc(w, 1))).toEqual(['Gecikme', 'Böl', 'İptal'])
    expect(ad(await menuAc(w, 2))).toEqual(['Gecikme', 'Birleştir', 'İptal'])
    const menu = satirlar(w)[2]!.get('[role="menu"]')
    const cocuk = [...menu.element.children]
    const iptal = cocuk.find((c) => c.textContent?.includes('İptal'))!
    expect(cocuk[cocuk.indexOf(iptal) - 1]!.getAttribute('role')).toBe('separator') // AK-2.4: komşu değil
    expect(iptal.classList.contains('tehlike')).toBe(true)
  })

  it('AK-2.2: İPTAL satırında ne birincil ne menü var', async () => {
    const w = await kur(liste())
    expect(satirlar(w)[3]!.findAll('.islem-dugmeleri button')).toHaveLength(0)
    expect(satirlar(w)[3]!.find('button[aria-haspopup="menu"]').exists()).toBe(false)
  })

  it('menüden Gecikme/İptal ilgili diyaloğu açar; odak ⋯ düğmesine döner', async () => {
    const w = await kur(liste())
    const oge = (await menuAc(w, 1))[0]!
    await oge.trigger('click')
    expect(w.get('dialog').text()).toContain('Gecikme gir')
    expect(satirlar(w)[1]!.find('[role="menu"]').exists()).toBe(false)
  })

  it('menü açıkken ↑/↓ satır gezintisini bozmaz (menü içi tuşlar satıra geçmez)', async () => {
    const w = await kur(liste())
    const oge = (await menuAc(w, 1))[0]!
    ;(satirlar(w)[1]!.element as HTMLElement).focus()
    await oge.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).not.toBe(satirlar(w)[2]!.element)
    expect(satirlar(w)[1]!.attributes('tabindex')).toBe('0')
  })

  it('AK-2.6: 100. satırda menü alt kenara yakınsa yukarı açılır (kırpılmaz)', async () => {
    vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
    const kutu = (top: number, h: number) =>
      ({
        top,
        bottom: top + h,
        left: 1000,
        right: 1030,
        width: 30,
        height: h,
        x: 1000,
        y: top,
      }) as DOMRect
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.getAttribute('role') === 'menu' ? kutu(0, 140) : kutu(740, 28)
    })
    const w = await kur(liste(100))
    await satirlar(w)[99]!.get('button[aria-haspopup="menu"]').trigger('click')
    await w.vm.$nextTick()
    await w.vm.$nextTick()
    expect(satirlar(w)[99]!.get('[role="menu"]').attributes('data-yon')).toBe('yukari')
    vi.restoreAllMocks()
  })
  it('AK-3.5: gecikme satırdan kayda 4 etkileşim (⋯, Gecikme, 30 dk çipi, Enter/gönder); yazma ve seçim yok', async () => {
    vi.mocked(atamaApi.seferGecikme).mockResolvedValue({} as never)
    const w = await kur(liste())
    const etkilesimler: string[] = []
    await satirlar(w)[1]!.get('button[aria-haspopup="menu"]').trigger('click')
    etkilesimler.push('menü')
    await satirlar(w)[1]!.get('[role="menuitem"][aria-label$="gecikme gir"]').trigger('click')
    etkilesimler.push('gecikme')
    await w.findAll('dialog [role="group"] button')[1]!.trigger('click')
    etkilesimler.push('çip 30')
    await w.get('dialog form').trigger('submit')
    etkilesimler.push('enter')
    await flushPromises()
    expect(etkilesimler).toHaveLength(4)
    expect(atamaApi.seferGecikme).toHaveBeenCalledWith(2, {
      kalkisGecikmeDk: 30,
      varisGecikmeDk: undefined,
      neden: 'GECIKME',
      nedenNotu: undefined,
    })
  })
})
