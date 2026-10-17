import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('./benimApi', () => ({ benimApi: { ozet: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import BenimOzet from './BenimOzet.vue'
import { benimApi, type BenimOzet as Ozet } from './benimApi'

const ozet: Ozet = {
  ay: '2026-10',
  adaleteDahil: true,
  planlananDahil: true,
  seferSayisi: 14,
  gorevSaat: 112.5,
  km: 4380,
  kmBilinmeyenSefer: 2,
  geceSaat: 31,
  izinGunu: 3,
  ortalamaSaat: 98,
  farkYuzde: 14.8,
}

async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/benim/parola', component: { template: '<div />' } }],
  })
  const w = mount(BenimOzet, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('BenimOzet', () => {
  beforeEach(() => vi.resetAllMocks())

  it('özeti gösterir: sefer, saat, km, gece, izin, ortalamaya fark; planlanan dahil notu', async () => {
    vi.mocked(benimApi.ozet).mockResolvedValue(ozet)
    const w = await kur()
    const metin = w.text()
    expect(metin).toContain('14')
    expect(metin).toContain('112,5')
    expect(metin).toContain('4.380')
    expect(metin).toContain('+14,8 %')
    expect(metin).toContain('Planlanan seferler dahildir')
    expect(metin).toContain('2 seferin km bilgisi girilmediği')
    expect(w.findAll('#ozet-ay option')).toHaveLength(14) // son 12 ay + bu ay + gelecek ay
  })

  it('yolculuk varsa sefer sayısının yanında ve açıklamada gösterilir; yoksa hiçbir iz yok (UX-5)', async () => {
    vi.mocked(benimApi.ozet).mockResolvedValue({ ...ozet, yolculukSayisi: 2 })
    const w = await kur()
    expect(w.get('[data-test="yolculuk-sayisi"]').text()).toBe('(+2 yolculuk)')
    expect(w.text()).toContain('süresi görev saatinize dahildir, km toplamına eklenmez')

    vi.mocked(benimApi.ozet).mockResolvedValue({ ...ozet, yolculukSayisi: 0 })
    const w2 = await kur()
    expect(w2.find('[data-test="yolculuk-sayisi"]').exists()).toBe(false)
    expect(w2.text()).not.toContain('yolculuk')
  })

  it('ay değişince o ayın özetini ister', async () => {
    vi.mocked(benimApi.ozet).mockResolvedValue(ozet)
    const w = await kur()
    const secenekler = w.findAll('#ozet-ay option')
    await w.get('#ozet-ay').setValue(secenekler[3]!.element.getAttribute('value'))
    await flushPromises()
    expect(benimApi.ozet).toHaveBeenCalledTimes(2)
    expect(vi.mocked(benimApi.ozet).mock.calls[1]![0]).toBe(
      secenekler[3]!.element.getAttribute('value'),
    )
  })

  it('adalet dışı makinist (askıda) ve ortalama yok durumları; hata', async () => {
    vi.mocked(benimApi.ozet).mockResolvedValueOnce({
      ...ozet,
      adaleteDahil: false,
      farkYuzde: null,
      seferSayisi: 0,
    })
    const w = await kur()
    expect(w.text()).toContain('özet dışındasınız')
    expect(w.text()).toContain('Ortalama yok')
    vi.mocked(benimApi.ozet).mockRejectedValueOnce(new ApiHata(400, 'DOGRULAMA', 'Ay aralık dışı.'))
    expect((await kur()).get('[role=alert]').text()).toContain('Ay aralık dışı.')
  })
})
