import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import Diyalog from './Diyalog.vue'

const acik: VueWrapper[] = []
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
})
function kur(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  const w = mount(Diyalog, {
    props: { baslik: 'Sefer değişikliği', ...props },
    slots: { default: '<p>içerik</p>', ...slots },
    attachTo: document.body,
  })
  acik.push(w)
  return w
}

describe('Diyalog', () => {
  it('açılışta showModal çağrılır (open) ve başlığa aria-labelledby ile bağlanır', () => {
    const w = kur()
    const d = w.get('dialog')
    expect(d.attributes('open')).toBeDefined()
    const baslik = w.get('h2')
    expect(d.attributes('aria-labelledby')).toBe(baslik.attributes('id'))
    expect(baslik.text()).toBe('Sefer değişikliği')
    expect(w.text()).toContain('içerik')
  })

  it('boyut sınıfı', () => {
    expect(kur({ boyut: 'genis' }).get('dialog').classes()).toContain('diyalog-genis')
  })

  it('Esc (cancel olayı) kapat ister ve diyaloğu kendisi kapatmaz', async () => {
    const w = kur()
    const olay = new Event('cancel', { cancelable: true })
    w.get('dialog').element.dispatchEvent(olay)
    expect(olay.defaultPrevented).toBe(true)
    expect(w.emitted('kapat')).toHaveLength(1)
    expect(w.get('dialog').attributes('open')).toBeDefined()
  })

  it('kapat × düğmesi erişilebilir adlıdır ve kapat ister', async () => {
    const w = kur()
    const dugme = w.get('button[aria-label="Kapat"]')
    await dugme.trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })

  it('arka plan tıklaması KAPATMAZ', async () => {
    const w = kur()
    await w.get('dialog').trigger('click')
    expect(w.emitted('kapat')).toBeUndefined()
  })

  it('kapatilabilir=false: × yok, Esc kapat istemez ama olay engellenir', () => {
    const w = kur({ kapatilabilir: false })
    expect(w.find('button[aria-label="Kapat"]').exists()).toBe(false)
    const olay = new Event('cancel', { cancelable: true })
    w.get('dialog').element.dispatchEvent(olay)
    expect(olay.defaultPrevented).toBe(true)
    expect(w.emitted('kapat')).toBeUndefined()
  })

  it('eylemler ve neden slotları: neden aria-describedby ile bağlı', () => {
    const w = kur({}, { eylemler: '<button>Kaydet</button>', neden: 'Önce bir sefer seçin.' })
    const neden = w.get('.diyalog-neden')
    expect(neden.text()).toBe('Önce bir sefer seçin.')
    expect(w.get('dialog').attributes('aria-describedby')).toBe(neden.attributes('id'))
    expect(w.get('.diyalog-eylemler button').text()).toBe('Kaydet')
  })

  it('neden yoksa aria-describedby ve alt çubuk yok', () => {
    const w = kur()
    expect(w.get('dialog').attributes('aria-describedby')).toBeUndefined()
    expect(w.find('.diyalog-alt').exists()).toBe(false)
  })

  it('sökülünce odak açan öğeye döner', () => {
    const tetikleyici = document.createElement('button')
    document.body.appendChild(tetikleyici)
    tetikleyici.focus()
    const w = kur()
    const kapat = w.get('button[aria-label="Kapat"]').element as HTMLElement
    kapat.focus()
    w.unmount()
    acik.pop()
    expect(document.activeElement).toBe(tetikleyici)
  })

  it('form: gövde ve eylemler tek form içinde; submit gonder olayı verir', async () => {
    const w = kur({ form: true }, { eylemler: '<button type="submit">Tamam</button>' })
    expect(w.find('form .diyalog-govde').exists()).toBe(true)
    await w.get('button[type="submit"]').trigger('submit')
    expect(w.emitted('gonder')).toHaveLength(1)
  })

  it('YENİ-2: odaktaki düğme devre dışı kalınca odak diyalog içinde kalır (BODY değil)', async () => {
    const w = mount(
      defineComponent({
        components: { Diyalog },
        setup: () => ({ kilit: ref(false) }),
        template: `<Diyalog baslik="T"><template #eylemler>
          <button id="vazgec" type="button">Vazgeç</button>
          <button id="uygula" type="button" :disabled="kilit">Uygula</button></template></Diyalog>`,
      }),
      { attachTo: document.body },
    )
    acik.push(w)
    const uygula = w.get('#uygula').element as HTMLButtonElement
    uygula.focus()
    expect(document.activeElement).toBe(uygula)
    ;(w.vm as unknown as { kilit: boolean }).kilit = true
    await flushPromises()
    expect(uygula.disabled).toBe(true)
    expect(w.get('dialog').element.contains(document.activeElement)).toBe(true)
    expect(document.activeElement).not.toBe(uygula)
  })

  it('YENİ-1: açan öğe kapanışta DOM dışındaysa odak #icerik başlığına gider', async () => {
    document.body.innerHTML =
      '<main id="icerik" tabindex="-1"><h1>Sayfa</h1><div id="kok"></div></main>'
    const ac = document.createElement('button')
    document.body.appendChild(ac)
    ac.focus()
    const w = mount(Diyalog, { props: { baslik: 'T' }, attachTo: '#kok' })
    ac.remove() // açan öğe diyalog açıkken kalktı
    w.unmount()
    await nextTick()
    expect(document.activeElement?.tagName).toBe('H1')
  })

  it('form yoksa <form> üretilmez', () => {
    expect(kur().find('form').exists()).toBe(false)
  })
})
