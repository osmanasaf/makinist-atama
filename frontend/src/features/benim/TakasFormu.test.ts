import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./benimApi', () => ({
  benimApi: { takasHedefler: vi.fn(), takasAc: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import TakasFormu from './TakasFormu.vue'
import { benimApi, type BenimGorev, type BenimTakas, type TakasHedef } from './benimApi'

const gorev = {
  atamaId: 10,
  trenNo: 'T1',
  ozet: 'Asıl · Ankara 15.10 08:00 → Ankara 15.10 09:00',
} as BenimGorev

const hedef: TakasHedef = {
  hedefAtamaId: 20,
  sicil: 'D002',
  gorev: 'Yardımcı · Ankara 16.10 08:00 → Ankara 16.10 09:00',
  trenNo: 'T2',
  tarih: '2026-10-16',
  kalkisGar: 'Ankara',
  varisGar: 'Ankara',
  guncelKalkis: '2026-10-16T05:00:00Z',
}

const talep = { id: 5 } as BenimTakas

async function kur(tur: 'TAKAS' | 'BIRAK') {
  const w = mount(TakasFormu, { props: { gorev, tur } })
  await flushPromises()
  return w
}

describe('TakasFormu', () => {
  beforeEach(() => vi.resetAllMocks())

  it('takas: karşı görev listesi sunucudan gelir; sicil + pano bilgisi, kural ayrıntısı yok', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([hedef])
    const w = await kur('TAKAS')
    expect(benimApi.takasHedefler).toHaveBeenCalledWith(10)
    const secenek = w.findAll('#takas-hedef option').map((o) => o.text())
    expect(secenek[1]).toContain('D002')
    expect(secenek[1]).toContain('T2')
    expect(w.text()).not.toMatch(/ihlal|belge|dinlenme/i)
  })

  it('takas edilebilecek görev yoksa bildirir; yükleme hatasını gösterir', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([])
    const bos = await kur('TAKAS')
    expect(bos.text()).toContain('takas edilebilecek görev yok')
    expect(bos.get('button[type=submit]').attributes('disabled')).toBeDefined() // boş listede Gönder kapalı (UX-2)
    vi.mocked(benimApi.takasHedefler).mockRejectedValue(
      new ApiHata(404, 'BULUNAMADI', 'Görev bulunamadı.'),
    )
    expect((await kur('TAKAS')).get('[role="alert"]').text()).toContain('Görev bulunamadı.')
  })

  it('neden ve (takasta) hedef zorunlu: hata mesajı alanla ilişkili (aria-describedby), istek gitmez', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([hedef])
    const w = await kur('TAKAS')
    await w.get('form').trigger('submit')
    expect(benimApi.takasAc).not.toHaveBeenCalled()
    const neden = w.get('#takas-neden')
    expect(neden.attributes('aria-invalid')).toBe('true')
    expect(w.get('#' + neden.attributes('aria-describedby')!).text()).toContain('Neden seçin')
    expect(w.get('#takas-hedef-hata').text()).toContain('görevi seçin')
  })

  it('Diğer nedeninde not zorunlu; not tanı uyarısı ve 120 karakter sınırı', async () => {
    const w = await kur('BIRAK')
    await w.get('#takas-neden').setValue('DIGER')
    await w.get('form').trigger('submit')
    expect(w.get('#takas-not-hata').text()).toContain('açıklama')
    expect(w.get('#takas-not').attributes('maxlength')).toBe('120')
    expect(w.get('#takas-not-uyari').text()).toContain('tanısı')
    expect(w.get('#takas-not').attributes('aria-describedby')).toContain('takas-not-uyari')
  })

  it('takas gönderimi: kimlikler sayı olarak gider; başarıda olustu olayı', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([hedef])
    vi.mocked(benimApi.takasAc).mockResolvedValue(talep)
    const w = await kur('TAKAS')
    await w.get('#takas-hedef').setValue('20')
    await w.get('#takas-neden').setValue('IZIN')
    await w.get('#takas-not').setValue('düğün')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(benimApi.takasAc).toHaveBeenCalledWith({
      atamaId: 10,
      hedefAtamaId: 20,
      neden: 'IZIN',
      nedenNotu: 'düğün',
    })
    expect(w.emitted('olustu')![0]).toEqual([talep])
  })

  it('bırakma gönderimi: hedef yok; sunucu 422 tek genel mesaj gösterilir (taraf belirtilmez)', async () => {
    vi.mocked(benimApi.takasAc).mockRejectedValue(
      new ApiHata(422, 'TAKAS_KURALA_UYMUYOR', 'Bu takas kurallara uymuyor.'),
    )
    const w = await kur('BIRAK')
    await w.get('#takas-neden').setValue('HASTALIK')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(benimApi.takasAc).toHaveBeenCalledWith({
      atamaId: 10,
      hedefAtamaId: undefined,
      neden: 'HASTALIK',
      nedenNotu: undefined,
    })
    expect(w.get('[role="alert"]').text()).toBe('Bu takas kurallara uymuyor.')
    expect(w.emitted('olustu')).toBeUndefined()
  })

  it('Vazgeç kapat olayı verir', async () => {
    const w = await kur('BIRAK')
    await w
      .findAll('button')
      .find((b) => b.text() === 'Vazgeç')!
      .trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })

  it('QA BUG-6: boş gönderimde odak ilk geçersiz alana gider; hata metinleri role=alert', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([hedef])
    const w = mount(TakasFormu, { props: { gorev, tur: 'TAKAS' }, attachTo: document.body })
    await flushPromises()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(document.activeElement).toBe(w.get('#takas-hedef').element)
    expect(w.get('#takas-hedef-hata').attributes('role')).toBe('alert')
    expect(w.get('#takas-neden-hata').attributes('role')).toBe('alert')
    w.unmount()
  })

  it('W3: istek sürerken × (ve Esc) diyaloğu kapatmaz; bitince kapatılabilir', async () => {
    vi.mocked(benimApi.takasHedefler).mockResolvedValue([])
    let bitir: (t: BenimTakas) => void = () => {}
    vi.mocked(benimApi.takasAc).mockReturnValue(new Promise((c) => (bitir = c)))
    const w = await kur('BIRAK')
    await w.get('#takas-neden').setValue('IZIN')
    await w.get('form').trigger('submit')
    await flushPromises()
    await w.get('button[aria-label="Kapat"]').trigger('click')
    w.get('dialog').element.dispatchEvent(new Event('cancel', { cancelable: true }))
    expect(w.emitted('kapat')).toBeUndefined()
    bitir(talep)
    await flushPromises()
    expect(w.emitted('olustu')).toHaveLength(1)
    await w.get('button[aria-label="Kapat"]').trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })
})
