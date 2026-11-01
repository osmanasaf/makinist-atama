import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiHata } from '@/api/istemci'

vi.mock('./ozetApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./ozetApi')>()),
  ozetApi: { getir: vi.fn(), denetimPaketi: vi.fn() },
}))

import YonetimOzeti from './YonetimOzeti.vue'
import { ozetApi, type YonetimOzeti as Ozet } from './ozetApi'
import { ornek } from './testVerisi'

// OK-50 (O-5, BUG-6): işlem sürerken düğme "disabled" olup odağı body'ye düşürmez (aria-disabled); form yerel doğrulama baloncuğu yerine kendi mesajını gösterir.
const bos = { template: '<div />' }
async function kur() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/rapor/ozet', name: 'rapor-ozet', component: bos },
      { path: '/yazdir/ozet', name: 'yazdir-ozet', component: bos },
    ],
  })
  await router.push('/rapor/ozet')
  const w = mount(YonetimOzeti, { attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('YonetimOzeti düzeltmeleri (OK-50)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
    vi.mocked(ozetApi.getir).mockResolvedValue(ornek())
  })

  it('"Özeti getir" sürerken disabled değil aria-disabled (odak düğmede kalır); ikinci gönderim yok sayılır', async () => {
    const w = await kur()
    let bitir!: (o: Ozet) => void
    vi.mocked(ozetApi.getir).mockReturnValueOnce(new Promise((c) => (bitir = c)))
    const dugme = w.get('button[type="submit"]')
    ;(dugme.element as HTMLButtonElement).focus()
    await w.get('form').trigger('submit')
    expect(dugme.attributes('disabled')).toBeUndefined()
    expect(dugme.attributes('aria-disabled')).toBe('true')
    expect(document.activeElement).toBe(dugme.element)
    await w.get('form').trigger('submit') // sürerken ikinci gönderim
    expect(ozetApi.getir).toHaveBeenCalledTimes(2) // açılış + tek istek
    bitir(ornek())
    await flushPromises()
    expect(dugme.attributes('aria-disabled')).toBeUndefined()
  })

  it('Denetim paketi indirilirken düğme aria-disabled; ikinci tıklama ikinci indirme başlatmaz', async () => {
    const w = await kur()
    let bitir!: (v: { blob: Blob; ad: string | null }) => void
    vi.mocked(ozetApi.denetimPaketi).mockReturnValueOnce(new Promise((c) => (bitir = c)))
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() })
    const dugme = w.findAll('button').find((b) => b.text().startsWith('Denetim paketi'))!
    await dugme.trigger('click')
    expect(dugme.attributes('disabled')).toBeUndefined()
    expect(dugme.attributes('aria-disabled')).toBe('true')
    await dugme.trigger('click')
    expect(ozetApi.denetimPaketi).toHaveBeenCalledTimes(1)
    bitir({ blob: new Blob(['x']), ad: null })
    await flushPromises()
    expect(dugme.attributes('aria-disabled')).toBeUndefined()
    vi.unstubAllGlobals()
  })

  it('BUG-6: form novalidate: ay boşken tarayıcı baloncuğu yerine kendi mesajı çıkar ve önceki hata metni temizlenir', async () => {
    const w = await kur()
    expect(w.get('form').attributes('novalidate')).toBeDefined()
    vi.mocked(ozetApi.getir).mockRejectedValueOnce(
      new ApiHata(400, 'DOGRULAMA', 'Ay, bugünden en çok 36 ay önce olabilir.'),
    )
    await w.get('#ozet-ay').setValue('2020-01')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).toContain('en çok 36 ay')
    await w.get('#ozet-ay').setValue('')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.text()).not.toContain('en çok 36 ay')
    expect(w.get('#ozet-ay-hata').text()).toBe('Ay seçin.')
  })
})
