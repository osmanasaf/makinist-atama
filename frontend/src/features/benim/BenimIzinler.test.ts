import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./benimApi', () => ({ benimApi: { izinler: vi.fn(), izinTalepEt: vi.fn() } }))

import { ApiHata } from '@/api/istemci'
import BenimIzinler from './BenimIzinler.vue'
import { benimApi } from './benimApi'

async function kur() {
  const w = mount(BenimIzinler)
  await flushPromises()
  return w
}

describe('BenimIzinler', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(benimApi.izinler).mockResolvedValue([])
  })

  it('boş durum; tarih alanları type=date, tür seçilebilir', async () => {
    const w = await kur()
    expect(w.text()).toContain('Henüz izin talebiniz yok')
    expect(w.get('#izin-baslangic').attributes('type')).toBe('date')
    expect(w.get('#izin-bitis').attributes('type')).toBe('date')
    expect(w.findAll('#izin-tur option').map((o) => o.text())).toEqual(['Yıllık', 'Rapor', 'Diğer'])
  })

  it('eksik alan hata mesajını alanla ilişkilendirir (aria-describedby) ve istek göndermez', async () => {
    const w = await kur()
    await w.get('form').trigger('submit')
    expect(benimApi.izinTalepEt).not.toHaveBeenCalled()
    const baslangic = w.get('#izin-baslangic')
    expect(baslangic.attributes('aria-invalid')).toBe('true')
    const hataId = baslangic.attributes('aria-describedby')!
    expect(w.get(`#${hataId}`).text()).toBe('Başlangıç tarihi zorunludur.')
  })

  it('bitiş başlangıçtan önceyse istemci doğrulaması reddeder', async () => {
    const w = await kur()
    await w.get('#izin-baslangic').setValue('2030-05-10')
    await w.get('#izin-bitis').setValue('2030-05-09')
    await w.get('form').trigger('submit')
    expect(benimApi.izinTalepEt).not.toHaveBeenCalled()
    expect(w.get('#izin-bitis-hata').text()).toContain('başlangıçtan önce olamaz')
  })

  it('başarılı talep listeye eklenir ve atamacıya iletildiği söylenir', async () => {
    vi.mocked(benimApi.izinTalepEt).mockResolvedValue({
      id: 9,
      baslangic: '2030-05-10',
      bitis: '2030-05-12',
      tur: 'YILLIK',
      durum: 'TALEP',
    })
    const w = await kur()
    await w.get('#izin-baslangic').setValue('2030-05-10')
    await w.get('#izin-bitis').setValue('2030-05-12')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(benimApi.izinTalepEt).toHaveBeenCalledWith({
      baslangic: '2030-05-10',
      bitis: '2030-05-12',
      tur: 'YILLIK',
    })
    expect(w.get('[role=status]').text()).toContain('atamacıya iletildi')
    expect(w.get('.benim-liste').text()).toContain('10.05.2030 – 12.05.2030')
    expect(w.get('.benim-liste').text()).toContain('Onay bekliyor')
    // durum yalnız renkle değil: rozet ton + simge + metin
    const rozet = w.get('.benim-liste .rozet')
    expect(rozet.classes()).toContain('rozet-uyari')
    expect(rozet.get('svg').attributes('data-simge')).toBe('saat')
  })

  it('sunucu alan hatası (geçmiş tarih) alanın altında, çakışma genel hata olarak görünür', async () => {
    const w = await kur()
    await w.get('#izin-baslangic').setValue('2030-05-10')
    await w.get('#izin-bitis').setValue('2030-05-12')
    vi.mocked(benimApi.izinTalepEt).mockRejectedValueOnce(
      new ApiHata(400, 'DOGRULAMA', 'Girilen bilgilerde hata var.', {
        alanlar: { baslangic: 'Başlangıç tarihi geçmişte olamaz.' },
      }),
    )
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('#izin-baslangic-hata').text()).toContain('geçmişte olamaz')
    vi.mocked(benimApi.izinTalepEt).mockRejectedValueOnce(
      new ApiHata(409, 'IZIN_CAKISMASI', 'Çakışan bir izniniz var.'),
    )
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role=alert]').text()).toContain('Çakışan bir izniniz var.')
  })
})
