import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./tanimApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./tanimApi')>()),
  tanimApi: {
    liste: vi.fn(),
    ekle: vi.fn(),
    adlandir: vi.fn(),
    intikalSureleri: vi.fn(),
    intikalSuresiYaz: vi.fn(),
    intikalSuresiSil: vi.fn(),
  },
}))

import { ApiHata } from '@/api/istemci'
import { kullanici } from '@/composables/oturum'
import type { Rol } from '@/api/tipler'
import TanimSayfasi from './TanimSayfasi.vue'
import { tanimApi } from './tanimApi'

function girisYap(rol: Rol) {
  kullanici.value = { id: 1, eposta: 'a@b.c', adSoyad: 'Ayşe Kaya', rol, aktif: true }
}

async function kur() {
  const w = mount(TanimSayfasi)
  await flushPromises()
  return w
}

describe('TanimSayfasi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(tanimApi.liste).mockResolvedValue([
      { id: 1, tip: 'GAR', ad: 'Ankara' },
      { id: 2, tip: 'GAR', ad: 'İzmir' },
    ])
    vi.mocked(tanimApi.intikalSureleri).mockResolvedValue([])
  })

  it('tanım listesini gösterir', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    expect(tanimApi.liste).toHaveBeenCalledWith('GAR')
    expect(w.text()).toContain('Ankara')
    expect(w.text()).toContain('İzmir')
  })

  it('ATAMACI için ekle ve yeniden adlandır düğmesi yoktur', async () => {
    girisYap('ATAMACI')
    const w = await kur()
    expect(w.text()).not.toContain('Ekle')
    expect(w.text()).not.toContain('Yeniden adlandır')
  })

  it('YONETICI ekleyebilir', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.ekle).mockResolvedValue({ id: 3, tip: 'GAR', ad: 'Konya' })
    const w = await kur()
    await w.get('#yeni-ad').setValue(' Konya ')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(tanimApi.ekle).toHaveBeenCalledWith('GAR', 'Konya')
  })

  it('sekme değişince o tipin listesi istenir', async () => {
    girisYap('YONETICI')
    const w = await kur()
    await w.findAll('[role=tab]')[1]!.trigger('click')
    await flushPromises()
    expect(tanimApi.liste).toHaveBeenLastCalledWith('HAT')
  })

  it('sekmeler ARIA tabs: tek h1, seçili sekme, dolaşan tabindex ve panel bağı', async () => {
    girisYap('YONETICI')
    const w = await kur()
    expect(w.findAll('h1')).toHaveLength(1)
    const sekmeler = w.findAll('[role=tab]')
    expect(sekmeler.map((t) => t.text())).toEqual(['Garlar', 'Hatlar', 'Araç tipleri'])
    expect(sekmeler.map((t) => t.attributes('aria-selected'))).toEqual(['true', 'false', 'false'])
    expect(sekmeler.map((t) => t.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
    const panel = w.get('[role=tabpanel]')
    expect(panel.attributes('aria-labelledby')).toBe(sekmeler[0]!.attributes('id'))
    expect(sekmeler[0]!.attributes('aria-controls')).toBe(panel.attributes('id'))
  })

  it('ok tuşları, Home ve End sekme değiştirir ve o tipin listesini ister', async () => {
    girisYap('YONETICI')
    const w = mount(TanimSayfasi, { attachTo: document.body })
    await flushPromises()
    const tab = (i: number) => w.findAll('[role=tab]')[i]!
    const secili = () =>
      w.findAll('[role=tab]').findIndex((t) => t.attributes('aria-selected') === 'true')
    await tab(0).trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(secili()).toBe(1)
    expect(tanimApi.liste).toHaveBeenLastCalledWith('HAT')
    expect(document.activeElement).toBe(tab(1).element)
    await tab(1).trigger('keydown', { key: 'End' })
    await flushPromises()
    expect(secili()).toBe(2)
    expect(tanimApi.liste).toHaveBeenLastCalledWith('ARAC_TIPI')
    await tab(2).trigger('keydown', { key: 'ArrowRight' }) // sondan başa
    await flushPromises()
    expect(secili()).toBe(0)
    await tab(0).trigger('keydown', { key: 'ArrowLeft' }) // baştan sona
    await flushPromises()
    expect(secili()).toBe(2)
    await tab(2).trigger('keydown', { key: 'Home' })
    await flushPromises()
    expect(secili()).toBe(0)
    w.unmount()
  })

  it('liste boşsa boş durum; yükleme hatasında hata durumu ve "Yeniden dene"', async () => {
    girisYap('ATAMACI')
    vi.mocked(tanimApi.liste).mockResolvedValue([])
    const bos = await kur()
    expect(bos.text()).toContain('Kayıt yok.')
    expect(bos.find('table').exists()).toBe(false)

    vi.mocked(tanimApi.liste).mockRejectedValueOnce(
      new ApiHata(500, 'HATA', 'Sunucu yanıt vermedi.'),
    )
    const hatali = await kur()
    expect(hatali.get('[role="alert"]').text()).toContain('Sunucu yanıt vermedi.')
    vi.mocked(tanimApi.liste).mockResolvedValue([{ id: 1, tip: 'GAR', ad: 'Ankara' }])
    await hatali
      .findAll('button')
      .find((b) => b.text() === 'Yeniden dene')!
      .trigger('click')
    await flushPromises()
    expect(hatali.text()).toContain('Ankara')
  })

  it('ekleme hatası alanın altında, aria-invalid + aria-describedby ile bağlı; odak alana döner', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.ekle).mockRejectedValue(new ApiHata(409, 'AD_TEKRARI', 'Bu ad zaten var.'))
    const w = mount(TanimSayfasi, { attachTo: document.body })
    await flushPromises()
    await w.get('#yeni-ad').setValue('Ankara')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('#yeni-ad').attributes('aria-invalid')).toBe('true')
    expect(w.get('#yeni-ad').attributes('aria-describedby')).toBe('yeni-ad-hata')
    expect(w.get('#yeni-ad-hata').text()).toBe('Bu ad zaten var.')
    expect(document.activeElement).toBe(w.get('#yeni-ad').element)
    expect(w.get('label[for="yeni-ad"]').element.closest('.alan')!.classList).toContain(
      'alan-zorunlu',
    )
    w.unmount()
  })

  it('yeniden adlandırma: açılınca odak girişe, vazgeçince düğmeye döner; hata satırda gösterilir', async () => {
    girisYap('YONETICI')
    vi.mocked(tanimApi.adlandir).mockRejectedValue(
      new ApiHata(409, 'AD_TEKRARI', 'Bu ad zaten var.'),
    )
    const w = mount(TanimSayfasi, { attachTo: document.body })
    await flushPromises()
    await w.get('#tanim-yeniden-1').trigger('click')
    await flushPromises()
    const girdi = w.get('#tanim-duzen-1')
    expect(document.activeElement).toBe(girdi.element)
    await girdi.setValue('İzmir')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Kaydet')!
      .trigger('click')
    await flushPromises()
    expect(girdi.attributes('aria-invalid')).toBe('true')
    expect(w.get('#tanim-duzen-hata-1').text()).toBe('Bu ad zaten var.')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Vazgeç')!
      .trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(w.get('#tanim-yeniden-1').element)
    w.unmount()
  })
})
