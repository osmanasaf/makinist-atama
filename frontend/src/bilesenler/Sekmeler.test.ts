import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import Sekmeler from './Sekmeler.vue'

const SEKMELER = [
  { kimlik: 'a', ad: 'Genel' },
  { kimlik: 'b', ad: 'Kurallar', sayac: 3 },
  { kimlik: 'c', ad: 'Sonuç' },
]
const acik: VueWrapper[] = []
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
})

function kur(baslangic = 'a') {
  const Ana = defineComponent({
    components: { Sekmeler },
    setup: () => ({ sekmeler: SEKMELER, etkin: ref(baslangic) }),
    template: `<Sekmeler v-model="etkin" :sekmeler="sekmeler" etiket="Örnek sekmeler">
      <template #panel-a><p>A paneli</p></template>
      <template #panel-b><p>B paneli</p></template>
      <template #panel-c><p>C paneli</p></template>
    </Sekmeler>`,
  })
  const w = mount(Ana, { attachTo: document.body })
  acik.push(w)
  return w
}
const sekme = (w: VueWrapper, i: number) => w.findAll('[role=tab]')[i]!

describe('Sekmeler', () => {
  it('tablist/tab/tabpanel rolleri, yalnız etkin panel çizilir, aria-controls panele bağlı', () => {
    const w = kur()
    expect(w.find('[role=tablist]').exists()).toBe(true)
    expect(w.findAll('[role=tab]').map((t) => t.text())).toEqual(['Genel', 'Kurallar3', 'Sonuç'])
    expect(w.findAll('[role=tabpanel]')).toHaveLength(1)
    expect(w.get('[role=tabpanel]').text()).toBe('A paneli')
    expect(sekme(w, 0).attributes('aria-controls')).toBe(w.get('[role=tabpanel]').attributes('id'))
    expect(w.get('[role=tabpanel]').attributes('aria-labelledby')).toBe(
      sekme(w, 0).attributes('id'),
    )
  })

  it('dolaşan tabindex: yalnız etkin sekme 0, ötekiler -1; aria-selected', () => {
    const w = kur('b')
    expect(w.findAll('[role=tab]').map((t) => t.attributes('tabindex'))).toEqual(['-1', '0', '-1'])
    expect(w.findAll('[role=tab]').map((t) => t.attributes('aria-selected'))).toEqual([
      'false',
      'true',
      'false',
    ])
  })

  it('tıklama etkinleştirir', async () => {
    const w = kur()
    await sekme(w, 2).trigger('click')
    expect(w.get('[role=tabpanel]').text()).toBe('C paneli')
  })

  it('ok tuşları sarar; Home/End uçlara gider; odak yeni sekmeye taşınır', async () => {
    const w = kur('a')
    await sekme(w, 0).trigger('keydown', { key: 'ArrowRight' })
    expect(w.get('[role=tabpanel]').text()).toBe('B paneli')
    expect(document.activeElement).toBe(sekme(w, 1).element)
    await sekme(w, 1).trigger('keydown', { key: 'End' })
    expect(w.get('[role=tabpanel]').text()).toBe('C paneli')
    await sekme(w, 2).trigger('keydown', { key: 'ArrowRight' })
    expect(w.get('[role=tabpanel]').text()).toBe('A paneli')
    await sekme(w, 0).trigger('keydown', { key: 'ArrowLeft' })
    expect(w.get('[role=tabpanel]').text()).toBe('C paneli')
    await sekme(w, 2).trigger('keydown', { key: 'Home' })
    expect(w.get('[role=tabpanel]').text()).toBe('A paneli')
  })

  it('ilgisiz tuş etkinleştirmez', async () => {
    const w = kur()
    await sekme(w, 0).trigger('keydown', { key: 'x' })
    expect(w.get('[role=tabpanel]').text()).toBe('A paneli')
  })

  it('QA BUG-10: sekme listesi erişilebilir ad taşır', () => {
    expect(kur().get('[role=tablist]').attributes('aria-label')).toBe('Örnek sekmeler')
  })
})
