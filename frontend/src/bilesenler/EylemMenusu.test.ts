import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import EylemMenusu from './EylemMenusu.vue'

const OGELER = [
  {
    kimlik: 'gecikme',
    ad: 'Gecikme',
    etiket: 'T1 seferine gecikme gir',
    simge: 'gecikme' as const,
  },
  { kimlik: 'bol', ad: 'Böl', simge: 'bol' as const, gizli: true },
  { kimlik: 'birlestir', ad: 'Birleştir', simge: 'birlestir' as const },
  {
    kimlik: 'iptal',
    ad: 'İptal',
    etiket: 'T1 seferini iptal et',
    simge: 'iptal' as const,
    tehlike: true,
  },
]

const acik: VueWrapper[] = []
function kur(props: Record<string, unknown> = {}) {
  const w = mount(EylemMenusu, {
    props: { etiket: 'T1 için diğer işlemler', ogeler: OGELER, ...props },
    attachTo: document.body,
  })
  acik.push(w)
  return w
}
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

const dugme = (w: VueWrapper) => w.get('button[aria-haspopup="menu"]')
const ogeler = (w: VueWrapper) => w.findAll('[role="menuitem"]')

describe('EylemMenusu', () => {
  it('kapalıyken öğeler DOMda yok; düğme aria-haspopup/expanded taşır', () => {
    const w = kur()
    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(w.findAll('[role="menuitem"]')).toHaveLength(0)
    expect(dugme(w).attributes('aria-label')).toBe('T1 için diğer işlemler')
    expect(dugme(w).attributes('aria-expanded')).toBe('false')
    expect(dugme(w).attributes('aria-controls')).toBeUndefined()
  })

  it('tıkla açılır: role=menu, gizli öğe yok, aria-controls menü kimliğiyle eşleşir', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    const menu = w.get('[role="menu"]')
    expect(dugme(w).attributes('aria-expanded')).toBe('true')
    expect(dugme(w).attributes('aria-controls')).toBe(menu.attributes('id'))
    expect(ogeler(w).map((o) => o.text())).toEqual(['Gecikme', 'Birleştir', 'İptal'])
    expect(ogeler(w)[0]!.attributes('aria-label')).toBe('T1 seferine gecikme gir')
  })

  it('tehlikeli öğe ayırıcıdan sonra ve tehlike sınıfında', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    const sep = w.get('[role="separator"]')
    const cocuklar = [...w.get('[role="menu"]').element.children]
    expect(cocuklar[cocuklar.indexOf(sep.element) + 1]!.textContent).toContain('İptal')
    expect(ogeler(w)[2]!.classes()).toContain('tehlike')
    expect(ogeler(w)[0]!.classes()).not.toContain('tehlike')
  })

  it('ilk öğeye odak; ↓/↑ gezinir ve sarar; Home/End uçlara gider', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    const o = ogeler(w).map((x) => x.element)
    expect(document.activeElement).toBe(o[0])
    await w.get('[role="menu"]').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(o[1])
    await w.get('[role="menu"]').trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(o[2])
    await w.get('[role="menu"]').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(o[0])
    await w.get('[role="menu"]').trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(o[2])
    await w.get('[role="menu"]').trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(o[0])
  })

  it('düğmede ↓ açıp ilk öğeye, ↑ açıp son öğeye odaklar', async () => {
    const w = kur()
    await dugme(w).trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(ogeler(w)[0]!.element)
    await w.get('[role="menu"]').trigger('keydown', { key: 'Escape' })
    await dugme(w).trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(ogeler(w).at(-1)!.element)
  })

  it('Esc kapatır ve odağı ⋯ düğmesine verir', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    await w.get('[role="menu"]').trigger('keydown', { key: 'Escape' })
    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(document.activeElement).toBe(dugme(w).element)
    expect(dugme(w).attributes('aria-expanded')).toBe('false')
  })

  it('Tab menüyü kapatır (odak ⋯ düğmesinde)', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    await w.get('[role="menu"]').trigger('keydown', { key: 'Tab' })
    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(document.activeElement).toBe(dugme(w).element)
  })

  it('N-2 (MK-182): Tab ve Shift+Tab doğal gezintiyi iptal eder; menü kapanır, odak ⋯ düğmesinde kalır (body değil)', async () => {
    for (const shiftKey of [false, true]) {
      const w = kur()
      await dugme(w).trigger('click')
      const olay = new KeyboardEvent('keydown', {
        key: 'Tab',
        shiftKey,
        bubbles: true,
        cancelable: true,
      })
      w.get('[role="menu"]').element.dispatchEvent(olay)
      await w.vm.$nextTick()
      expect(olay.defaultPrevented).toBe(true) // sekme sırasının son öğesinde doğal Tab odağı belgeden çıkarıp body'ye düşürürdü
      expect(w.find('[role="menu"]').exists()).toBe(false)
      expect(document.activeElement).toBe(dugme(w).element)
      expect(document.activeElement).not.toBe(document.body)
      w.unmount()
      acik.pop()
    }
  })

  it('öğe seçimi sec(kimlik) yayar, menüyü kapatır, odağı ⋯ düğmesine verir', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    await ogeler(w)[2]!.trigger('click')
    expect(w.emitted('sec')).toEqual([['iptal']])
    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(document.activeElement).toBe(dugme(w).element)
  })

  it('dışa tık, pencere yeniden boyutu ve kaydırma kapatır', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await w.vm.$nextTick()
    expect(w.find('[role="menu"]').exists()).toBe(false)

    await dugme(w).trigger('click')
    window.dispatchEvent(new Event('resize'))
    await w.vm.$nextTick()
    expect(w.find('[role="menu"]').exists()).toBe(false)

    await dugme(w).trigger('click')
    document.body.dispatchEvent(new Event('scroll'))
    await w.vm.$nextTick()
    expect(w.find('[role="menu"]').exists()).toBe(false)
  })

  it('menünün içine pointerdown kapatmaz', async () => {
    const w = kur()
    await dugme(w).trigger('click')
    ogeler(w)[0]!.element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await w.vm.$nextTick()
    expect(w.find('[role="menu"]').exists()).toBe(true)
  })

  it('tek açık menü: ikincisi açılınca birincisi kapanır', async () => {
    const a = kur()
    const b = kur()
    await dugme(a).trigger('click')
    await dugme(b).trigger('click')
    expect(a.find('[role="menu"]').exists()).toBe(false)
    expect(b.find('[role="menu"]').exists()).toBe(true)
  })

  it('devreDisi: düğme kapalı ve açılmaz; sekmeSirasi düğmeye uygulanır', async () => {
    const w = kur({ devreDisi: true, sekmeSirasi: -1 })
    expect(dugme(w).attributes('disabled')).toBeDefined()
    expect(dugme(w).attributes('tabindex')).toBe('-1')
    await dugme(w).trigger('click')
    expect(w.find('[role="menu"]').exists()).toBe(false)
  })

  it('konum fixed; alt kenara yakınsa yukarı açılır, değilse aşağı', async () => {
    vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
    const kutu = (top: number, h: number): DOMRect =>
      ({
        top,
        bottom: top + h,
        left: 900,
        right: 930,
        width: 30,
        height: h,
        x: 900,
        y: top,
        toJSON: () => ({}),
      }) as DOMRect
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect')

    rect.mockImplementation(function (this: HTMLElement) {
      return this.getAttribute('role') === 'menu' ? kutu(0, 150) : kutu(100, 28)
    })
    const ust = kur()
    await dugme(ust).trigger('click')
    await ust.vm.$nextTick()
    expect(ust.get('[role="menu"]').attributes('data-yon')).toBe('asagi')
    expect(ust.get('[role="menu"]').attributes('style')).toContain('top:')
    await ust.get('[role="menu"]').trigger('keydown', { key: 'Escape' })

    rect.mockImplementation(function (this: HTMLElement) {
      return this.getAttribute('role') === 'menu' ? kutu(0, 150) : kutu(735, 28)
    })
    const alt = kur()
    await dugme(alt).trigger('click')
    await alt.vm.$nextTick()
    await alt.vm.$nextTick()
    expect(alt.get('[role="menu"]').attributes('data-yon')).toBe('yukari')
    expect(alt.get('[role="menu"]').attributes('style')).toContain('bottom:')
  })
})
