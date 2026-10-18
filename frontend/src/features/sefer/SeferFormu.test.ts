import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./seferApi', () => ({ seferApi: { olustur: vi.fn(), guncelle: vi.fn() } }))

import SeferFormu from './SeferFormu.vue'
import { seferApi } from './seferApi'

const tanim = (id: number, ad: string) => ({ id, tip: 'GAR' as const, ad })
function kur() {
  return mount(SeferFormu, {
    attachTo: document.body,
    props: {
      sefer: null,
      gun: '2026-10-12',
      garlar: [tanim(1, 'Ankara'), tanim(2, 'İzmir')],
      hatlar: [tanim(1, 'Ankara-İzmir')],
      aracTipleri: [tanim(1, 'E68000')],
    },
  })
}

describe('SeferFormu', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    document.body.innerHTML = ''
  })

  it('zorunlu alanlar işaretli; boş gönderimde hatalar alanın altında, aria-invalid + aria-describedby; odak ilk hatalı alanda', async () => {
    const w = kur()
    expect(w.findAll('.alan-zorunlu')).toHaveLength(8) // varış tarihi ve km isteğe bağlı
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(seferApi.olustur).not.toHaveBeenCalled()
    const tren = w.get('#trenNo')
    expect(tren.attributes('aria-invalid')).toBe('true')
    expect(tren.attributes('aria-describedby')).toBe('trenNo-hata')
    expect(w.get('#trenNo-hata').text()).toBe('Tren no zorunludur.')
    expect(tren.element.parentElement!.contains(w.get('#trenNo-hata').element)).toBe(true)
    expect(document.activeElement).toBe(tren.element)
    expect(w.get('#kalkisGar-hata').text()).toBe('Kalkış garı seçin.')
    expect(w.get('#km').attributes('aria-invalid')).toBe('false')
    w.unmount()
  })

  it('sunucu alan hatası alanın altında gösterilir ve odak o alana gider', async () => {
    vi.mocked(seferApi.olustur).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Geçersiz sefer.', { alanlar: { km: 'Km negatif olamaz.' } }),
    )
    const w = kur()
    await w.get('#trenNo').setValue('1001')
    await w.get('#kalkisGar').setValue(1)
    await w.get('#kalkisSaat').setValue('08:00')
    await w.get('#varisGar').setValue(2)
    await w.get('#varisSaat').setValue('12:00')
    await w.get('#aracTipi').setValue(1)
    await w.get('#hat').setValue(1)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(seferApi.olustur).toHaveBeenCalledTimes(1)
    expect(w.get('[role="alert"]').text()).toBe('Geçersiz sefer.')
    expect(w.get('#km-hata').text()).toBe('Km negatif olamaz.')
    expect(document.activeElement).toBe(w.get('#km').element)
    w.unmount()
  })
})
