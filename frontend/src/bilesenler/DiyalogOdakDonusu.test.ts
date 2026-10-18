/**
 * QA BUG-1: modal diyalog kapanınca odak açan öğeye dönmeli. Ortak `Diyalog` bileşeninde düzeltilir; 12 diyaloğun
 * hepsi bu sözleşmeye gerçek bileşenleriyle bağlanır (test kurulumu modalın "inert" davranışını taklit eder).
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, type Component } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AtamaDialog from '@/features/atama/AtamaDialog.vue'
import OtomatikAtamaDialog from '@/features/atama/OtomatikAtamaDialog.vue'
import SeferBirlestirDialog from '@/features/atama/SeferBirlestirDialog.vue'
import SeferBolDialog from '@/features/atama/SeferBolDialog.vue'
import SeferDegisiklikDialog from '@/features/atama/SeferDegisiklikDialog.vue'
import UygunSeferlerDialog from '@/features/atama/UygunSeferlerDialog.vue'
import YerineAtaDialog from '@/features/atama/YerineAtaDialog.vue'
import YolculukOneriDialog from '@/features/atama/YolculukOneriDialog.vue'
import TakasFormu from '@/features/benim/TakasFormu.vue'
import IntikalDialog from '@/features/intikal/IntikalDialog.vue'
import KuralKurucuDialog from '@/features/parametre/KuralKurucuDialog.vue'
import TakasOnayDialog from '@/features/takas/TakasOnayDialog.vue'

const aday = { makinistId: 1, ad: 'Ada', sicil: 'D001', uygun: true, ihlaller: [] } as never
const gorev = {
  atamaId: 10,
  trenNo: 'T1',
  ozet: 'Asıl',
  tarih: '2026-10-15',
  kalkisGar: 'A',
} as never

const DIYALOGLAR: [string, Component, Record<string, unknown>][] = [
  ['AtamaDialog', AtamaDialog, { seferId: 1, seferEtiketi: 'T1', rol: 'ASIL', aday }],
  ['OtomatikAtamaDialog', OtomatikAtamaDialog, { varsayilanBas: '2026-10-15' }],
  ['SeferBirlestirDialog', SeferBirlestirDialog, { seferId: 1, seferEtiketi: 'T1' }],
  ['SeferBolDialog', SeferBolDialog, { seferId: 1, seferEtiketi: 'T1' }],
  [
    'SeferDegisiklikDialog (iptal)',
    SeferDegisiklikDialog,
    { kip: 'iptal', seferId: 1, seferEtiketi: 'T1' },
  ],
  [
    'UygunSeferlerDialog',
    UygunSeferlerDialog,
    { makinistId: 1, makinistAdi: 'Ada', gun: '2026-10-15' },
  ],
  ['YerineAtaDialog', YerineAtaDialog, { seferId: 1, seferEtiketi: 'T1' }],
  ['YolculukOneriDialog', YolculukOneriDialog, { seferId: 1, seferEtiketi: 'T1', aday }],
  ['TakasFormu', TakasFormu, { gorev, tur: 'TAKAS' }],
  ['IntikalDialog', IntikalDialog, { kip: 'ekle', gun: '2026-10-15' }],
  ['KuralKurucuDialog', KuralKurucuDialog, { katalog: [] }],
  ['TakasOnayDialog', TakasOnayDialog, { talepId: 5 }],
]

const acik: VueWrapper[] = []
beforeEach(() => {
  vi.stubGlobal('fetch', () => Promise.reject(new TypeError('ağ yok'))) // yüklemeler hata ile biter: diyalog meşgul kalmaz
})
afterEach(() => {
  acik.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

function kur(bilesen: Component, props: Record<string, unknown>) {
  const Ev = defineComponent({
    data: () => ({ goster: false }),
    render() {
      return h('div', [
        h('button', { id: 'ac', onClick: () => (this.goster = true) }, 'Aç'),
        this.goster ? h(bilesen, { ...props, onKapat: () => (this.goster = false) }) : null,
      ])
    },
  })
  const w = mount(Ev, { attachTo: document.body })
  acik.push(w)
  return w
}

describe.each(DIYALOGLAR)('%s: kapanınca odak açan öğeye döner', (_ad, bilesen, props) => {
  it('Kapat × ile kapanınca', async () => {
    const w = kur(bilesen, props)
    const ac = w.get('#ac').element as HTMLElement
    ac.focus()
    await w.get('#ac').trigger('click')
    await flushPromises()
    expect(w.find('dialog[open]').exists()).toBe(true)
    expect(document.activeElement).not.toBe(ac) // odak diyalogun içine alındı
    await w.get('dialog button[aria-label="Kapat"]').trigger('click')
    await flushPromises()
    expect(w.find('dialog').exists()).toBe(false)
    expect(document.activeElement).toBe(ac)
  })
})
