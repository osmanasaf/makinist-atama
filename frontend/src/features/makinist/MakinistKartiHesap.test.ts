import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./makinistApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./makinistApi')>()),
  makinistApi: {
    hesap: vi.fn(),
    hesapAc: vi.fn(),
    hesapParolaSifirla: vi.fn(),
    hesapKilitAc: vi.fn(),
  },
}))

import MakinistHesap from './MakinistHesap.vue'
import { makinistApi, type HesapDurumu } from './makinistApi'

const yok: HesapDurumu = {
  var: false,
  girisAdi: null,
  aktif: false,
  parolaDegistirmeli: false,
  kilitli: false,
}
const var1: HesapDurumu = {
  var: true,
  girisAdi: 'd001',
  aktif: true,
  parolaDegistirmeli: true,
  kilitli: false,
}

async function kur(durum: 'AKTIF' | 'PASIF' = 'AKTIF') {
  const w = mount(MakinistHesap, { props: { makinistId: 5, durum } })
  await flushPromises()
  return w
}

const dugmeler = (w: Awaited<ReturnType<typeof kur>>) => w.findAll('button').map((b) => b.text())

describe('MakinistHesap (makinist kartı: Hesap bölümü)', () => {
  beforeEach(() => vi.resetAllMocks())

  it('hesap yoksa "Hesap aç"; geçici parola yalnız açma yanıtından sonra DOM da görünür', async () => {
    vi.mocked(makinistApi.hesap).mockResolvedValue(yok)
    vi.mocked(makinistApi.hesapAc).mockResolvedValue({ hesap: var1, geciciParola: 'Abcd2345Efgh' })
    const w = await kur()
    expect(w.text()).toContain('henüz hesabı yok')
    expect(w.find('[data-test=gecici-parola]').exists()).toBe(false)

    await w
      .findAll('button')
      .find((b) => b.text() === 'Hesap aç')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[data-test=gecici-parola]').text()).toBe('Abcd2345Efgh')
    expect(w.text()).toContain('Giriş adı (sicil): d001')
    expect(dugmeler(w)).toEqual(expect.arrayContaining(['Parola sıfırla', 'Kilidi aç', 'Kopyala']))
  })

  it('yeniden yüklenince (bileşen yeniden kurulunca) geçici parola bir daha çıkmaz', async () => {
    vi.mocked(makinistApi.hesap).mockResolvedValue(var1)
    const w = await kur()
    expect(w.find('[data-test=gecici-parola]').exists()).toBe(false)
    expect(w.text()).not.toContain('Abcd2345Efgh')
    expect(w.text()).toContain('ilk giriş bekleniyor')
  })

  it('parola sıfırla yeni geçici parolayı gösterir; kilit yoksa "Kilidi aç" kapalıdır, kilitliyse açılır', async () => {
    vi.mocked(makinistApi.hesap).mockResolvedValue(var1)
    vi.mocked(makinistApi.hesapParolaSifirla).mockResolvedValue({
      hesap: { ...var1, kilitli: true },
      geciciParola: 'Zyxw9876Vuts',
    })
    const w = await kur()
    const kilit = () => w.findAll('button').find((b) => b.text() === 'Kilidi aç')!
    expect(kilit().attributes('disabled')).toBeDefined()
    await w
      .findAll('button')
      .find((b) => b.text() === 'Parola sıfırla')!
      .trigger('click')
    await flushPromises()
    expect(w.get('[data-test=gecici-parola]').text()).toBe('Zyxw9876Vuts')
    expect(w.text()).toContain('hesap kilitli')
    expect(kilit().attributes('disabled')).toBeUndefined()
    vi.mocked(makinistApi.hesapKilitAc).mockResolvedValue({ ...var1, kilitli: false })
    await kilit().trigger('click')
    await flushPromises()
    expect(w.text()).toContain('Kilit açıldı')
  })

  it('pasif makinistte hesap açma düğmesi kapalıdır', async () => {
    vi.mocked(makinistApi.hesap).mockResolvedValue(yok)
    const w = await kur('PASIF')
    const ac = w.findAll('button').find((b) => b.text() === 'Hesap aç')!
    expect(ac.attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('Pasif personel için hesap açılamaz')
  })
})
