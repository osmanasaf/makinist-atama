import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { otomatik: vi.fn() },
}))

import OtomatikAtamaDialog from './OtomatikAtamaDialog.vue'
import { atamaApi, type OtomatikAtamaSonucu } from './atamaApi'

const sefer = (id: number) => ({
  seferId: id,
  trenNo: `T${id}`,
  tarih: '2026-10-12',
  kalkis: '2026-10-12T08:30:00+03:00',
  varis: '2026-10-12T10:00:00+03:00',
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
})
const sonuc = (uygulandi: boolean): OtomatikAtamaSonucu => ({
  bas: '2026-10-12',
  bit: '2026-10-18',
  uygulandi,
  atananSayisi: 2,
  bosSayisi: 1,
  gecmisSayisi: 0,
  atananlar: [
    { ...sefer(1), makinistId: 7, sicil: 'D007', ad: 'Aday Yedi' },
    { ...sefer(2), makinistId: 8, sicil: 'D008', ad: 'Aday Sekiz' },
  ],
  bosKalanlar: [
    {
      ...sefer(3),
      neden: '0/3 uygun: hat yetkisi 1, izin 1, dinlenme 1',
      ornekler: ['Ali Veli: Dinlenme 9 s < 11 s'],
    },
  ],
})

function kur() {
  return mount(OtomatikAtamaDialog, { props: { varsayilanBas: '2026-10-12' } })
}
const dugme = (w: ReturnType<typeof kur>, metin: string) =>
  w.findAll('button').find((b) => b.text() === metin)!

describe('OtomatikAtamaDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('varsayılan aralık: seçili gün ve +6 gün; önizleme alınmadan Uygula kapalı', () => {
    const w = kur()
    expect((w.get('#oto-bas').element as HTMLInputElement).value).toBe('2026-10-12')
    expect((w.get('#oto-bit').element as HTMLInputElement).value).toBe('2026-10-18')
    expect(dugme(w, 'Önizle').attributes('disabled')).toBeUndefined()
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
  })

  it('Önizle: uygula=false ile çağırır; özet, atanacaklar ve boş kalacaklar (BOŞ rozeti, neden, örnekler) gösterilir', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false))
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: false,
    })
    expect(w.get('[role="status"]').text()).toBe('2 sefer atanacak, 1 sefer boş kalacak')

    const atanan = w.findAll('table[aria-label="Atanacak seferler"] tbody tr')
    expect(atanan).toHaveLength(2)
    expect(atanan[0]!.text()).toContain('12.10.2026')
    expect(atanan[0]!.text()).toContain('08:30–10:00')
    expect(atanan[0]!.text()).toContain('T1')
    expect(atanan[0]!.text()).toContain('Ankara – Eskişehir')
    expect(atanan[0]!.text()).toContain('Aday Yedi (D007)')

    const bos = w.findAll('table[aria-label="Boş kalacak seferler"] tbody tr')
    expect(bos).toHaveLength(1)
    expect(bos[0]!.text()).toContain('BOŞ')
    expect(bos[0]!.find('.rozet-uyari').exists()).toBe(true)
    expect(bos[0]!.find('svg[data-simge="daire"]').exists()).toBe(true)
    expect(bos[0]!.get('.bos-neden').text()).toBe('0/3 uygun: hat yetkisi 1, izin 1, dinlenme 1')
    expect(bos[0]!.text()).toContain('Ali Veli: Dinlenme 9 s < 11 s')

    // uzun listeler kaydırılabilir kapsayıcıda
    expect(w.findAll('.otomatik-kaydir')).toHaveLength(2)
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeUndefined()
  })

  it('UX-11: geçmiş seferler boş sayılmaz; özet ayrı sayıyla bildirir', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue({ ...sonuc(false), gecmisSayisi: 58 })
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toBe(
      '2 sefer atanacak, 1 sefer boş kalacak (58 geçmiş sefer atlandı)',
    )
  })

  it('tarih değişince önizleme geçersiz olur: sonuç kalkar, Uygula kapanır', async () => {
    vi.mocked(atamaApi.otomatik).mockResolvedValue(sonuc(false))
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await w.get('#oto-bit').setValue('2026-10-20')
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
    expect(w.find('table').exists()).toBe(false)
    expect(w.text()).not.toContain('sefer atanacak')
  })

  it('bitiş başlangıçtan önceyse Önizle kapalı', async () => {
    const w = kur()
    await w.get('#oto-bit').setValue('2026-10-01')
    expect(dugme(w, 'Önizle').attributes('disabled')).toBeDefined()
  })

  it('Uygula: uygula=true ile çağırır, "N atandı, M boş kaldı" gösterir, masa yenilensin diye uygulandi yayınlar', async () => {
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false))
      .mockResolvedValueOnce(sonuc(true))
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenLastCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: true,
    })
    expect(w.get('[role="status"]').text()).toBe('2 atandı, 1 boş kaldı')
    expect(w.emitted('uygulandi')).toHaveLength(1)
    // bir kez uygulanınca ikinci kez uygulanamaz
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
  })

  // QA BUG-5: yük altında yanıt gelmedi; sunucu atamaları yazmış olabilir. Ekran gerçek durumu yeniden yükleyip söyler.
  it('Uygula yanıtsız kalırsa (AG_HATASI): masa yenilenir, yeniden önizlenir ve atamalar uygulanmış görünüyorsa bu söylenir', async () => {
    const agHatasi = new ApiHata(0, 'AG_HATASI', 'Sunucu zamanında yanıt vermedi.')
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false))
      .mockRejectedValueOnce(agHatasi)
      .mockResolvedValueOnce({ ...sonuc(false), atananSayisi: 0, atananlar: [] })
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenCalledTimes(3)
    expect(atamaApi.otomatik).toHaveBeenLastCalledWith({
      bas: '2026-10-12',
      bit: '2026-10-18',
      uygula: false,
    })
    expect(w.emitted('uygulandi')).toHaveLength(1)
    expect(w.get('[role="status"]').text()).toContain('atamalar uygulanmış görünüyor')
    expect(w.get('[role="alert"]').text()).toBe('')
  })

  it('Uygula yanıtsız kalır ve atamalar yazılmamışsa: kalan sayı gösterilir, tekrar Uygula açıktır', async () => {
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false))
      .mockRejectedValueOnce(new ApiHata(0, 'AG_HATASI', 'Sunucu zamanında yanıt vermedi.'))
      .mockResolvedValueOnce(sonuc(false))
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()
    expect(w.get('[role="status"]').text()).toContain('2 sefer hâlâ atanabilir')
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeUndefined()
  })

  it('Uygula yanıtsız kalır ve yeniden okuma da başarısızsa: atamalar yazılmış olabilir uyarısı', async () => {
    const ag = new ApiHata(0, 'AG_HATASI', 'Sunucu zamanında yanıt vermedi.')
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false))
      .mockRejectedValueOnce(ag)
      .mockRejectedValueOnce(ag)
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('Atamalar yazılmış olabilir')
  })

  it('Önizle yanıtsız kalırsa yalnız hata gösterilir (yazma yok, yeniden okuma yok)', async () => {
    vi.mocked(atamaApi.otomatik).mockRejectedValue(
      new ApiHata(0, 'AG_HATASI', 'Sunucu zamanında yanıt vermedi.'),
    )
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(atamaApi.otomatik).toHaveBeenCalledTimes(1)
    expect(w.emitted('uygulandi')).toBeUndefined()
    expect(w.get('[role="alert"]').text()).toContain('zamanında yanıt vermedi')
  })

  it('400 hatası diyalogda görünür ve Uygula açılmaz', async () => {
    vi.mocked(atamaApi.otomatik).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Aralık en çok 63 gün olabilir.'),
    )
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toBe('Aralık en çok 63 gün olabilir.')
    expect(dugme(w, 'Uygula').attributes('disabled')).toBeDefined()
  })

  it('Kapat kapat yayınlar', async () => {
    const w = kur()
    await dugme(w, 'Kapat').trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })
})

describe('OtomatikAtamaDialog: uygulama sonrası başlıklar ve nöbet notu', () => {
  beforeEach(() => vi.resetAllMocks())

  it('önizlemede "Atanacaklar"/"Boş kalacaklar", uygulandıktan sonra "Atananlar"/"Boş kalanlar"', async () => {
    vi.mocked(atamaApi.otomatik)
      .mockResolvedValueOnce(sonuc(false))
      .mockResolvedValueOnce(sonuc(true))
    const w = kur()
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    const basliklar = () => w.findAll('h3').map((h) => h.text())
    expect(basliklar()).toEqual(['Atanacaklar (2)', 'Boş kalacaklar (1)'])
    await dugme(w, 'Uygula').trigger('click')
    await flushPromises()
    expect(basliklar()).toEqual(['Atananlar (2)', 'Boş kalanlar (1)'])
  })

  it('boş kalma nedeni/örnekleri nöbet içeriyorsa tek not görünür', async () => {
    const s = sonuc(false)
    s.bosKalanlar[0]!.ornekler = ['Ali Veli: Gece nöbeti süresi 10 s > 7 s 30 dk']
    vi.mocked(atamaApi.otomatik).mockResolvedValue(s)
    const w = kur()
    expect(w.find('[data-test="nobet-notu"]').exists()).toBe(false)
    await dugme(w, 'Önizle').trigger('click')
    await flushPromises()
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })
})
