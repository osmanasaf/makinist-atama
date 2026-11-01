import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { seferIptal: vi.fn(), seferGecikme: vi.fn() },
}))

import SeferDegisiklikDialog from './SeferDegisiklikDialog.vue'
import { atamaApi } from './atamaApi'

type Kip = 'iptal' | 'gecikme'
const kur = (kip: Kip, ek: Record<string, unknown> = {}) =>
  mount(SeferDegisiklikDialog, {
    props: { kip, seferId: 11, seferEtiketi: 'T1001 Ankara – Eskişehir 08:30', ...ek },
  })
const onay = (w: ReturnType<typeof kur>) => w.get('button[type="submit"]')
const sefer = {} as Awaited<ReturnType<typeof atamaApi.seferIptal>>

describe('SeferDegisiklikDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  describe('iptal', () => {
    it('geri alınamaz uyarısı; neden seçilmeden onay kapalı; doğru gövde gönderilir', async () => {
      vi.mocked(atamaApi.seferIptal).mockResolvedValue(sefer)
      const w = kur('iptal')
      expect(w.text()).toContain('Seferi iptal et')
      expect(w.text()).toContain('Bu işlem geri alınamaz')
      expect(w.find('svg[data-simge="uyari"]').exists()).toBe(true)
      expect(w.find('#sefer-kalkis-gecikme').exists()).toBe(false)
      expect(onay(w).attributes('disabled')).toBeDefined()
      expect(w.get('.diyalog-neden').text()).toBe('Önce neden seçin.')
      await w.get('#sefer-neden').setValue('HASTALIK')
      expect(onay(w).attributes('disabled')).toBeUndefined()
      expect(w.find('.diyalog-neden').exists()).toBe(false)
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferIptal).toHaveBeenCalledWith(11, {
        neden: 'HASTALIK',
        nedenNotu: undefined,
      })
      expect(w.emitted('tamam')).toEqual([['Sefer iptal edildi.']])
    })

    it('DİĞER seçilince neden notu zorunlu', async () => {
      vi.mocked(atamaApi.seferIptal).mockResolvedValue(sefer)
      const w = kur('iptal')
      await w.get('#sefer-neden').setValue('DIGER')
      expect(onay(w).attributes('disabled')).toBeDefined()
      await w.get('#sefer-neden-notu').setValue('   ')
      expect(onay(w).attributes('disabled')).toBeDefined()
      await w.get('#sefer-neden-notu').setValue('Hat kapandı')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferIptal).toHaveBeenCalledWith(11, {
        neden: 'DIGER',
        nedenNotu: 'Hat kapandı',
      })
    })

    it('409 SEFER_IPTAL: detail diyalogda gösterilir, tamam yayınlanmaz, vazgeçince yenile=true', async () => {
      vi.mocked(atamaApi.seferIptal).mockRejectedValue(
        new ApiHata(409, 'SEFER_IPTAL', 'Sefer zaten iptal edilmiş.'),
      )
      const w = kur('iptal')
      await w.get('#sefer-neden').setValue('IPTAL')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(w.get('[role="alert"]').text()).toBe('Sefer zaten iptal edilmiş.')
      expect(w.emitted('tamam')).toBeUndefined()
      await w.get('button[type="button"]:not([aria-label="Kapat"])').trigger('click')
      expect(w.emitted('kapat')).toEqual([[true]])
    })
  })

  describe('gecikme', () => {
    it('kalkış gecikmesi mevcut değerle gelir; neden zorunlu; gövde doğru (varış boşsa gönderilmez)', async () => {
      vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
      const w = kur('gecikme', { kalkisGecikmeDk: 20, varisGecikmeDk: 0 })
      expect(w.text()).toContain('Gecikme gir')
      expect(w.text()).not.toContain('geri alınamaz')
      expect((w.get('#sefer-kalkis-gecikme').element as HTMLInputElement).value).toBe('20')
      // H4 (AK-3.3): gecikme kipinde neden varsayılan "Gecikme"; hâlâ zorunlu (boş seçilince onay kapanır)
      expect((w.get('#sefer-neden').element as HTMLSelectElement).value).toBe('GECIKME')
      await w.get('#sefer-neden').setValue('')
      expect(onay(w).attributes('disabled')).toBeDefined() // neden yok
      await w.get('#sefer-kalkis-gecikme').setValue('90')
      await w.get('#sefer-neden').setValue('GECIKME')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferGecikme).toHaveBeenCalledWith(11, {
        kalkisGecikmeDk: 90,
        varisGecikmeDk: undefined,
        neden: 'GECIKME',
        nedenNotu: undefined,
      })
      expect(w.emitted('tamam')).toEqual([['Gecikme kaydedildi.']])
    })

    it('BUG-11: varış kalkışla aynıysa kutu boş gelir (kalkışı değiştirmek varışı eski değerde bırakmaz); farklıysa gerçek değer görünür', async () => {
      vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
      const w = kur('gecikme', { kalkisGecikmeDk: 30, varisGecikmeDk: 30 })
      expect((w.get('#sefer-varis-gecikme').element as HTMLInputElement).value).toBe('')
      await w.get('#sefer-kalkis-gecikme').setValue('150')
      await w.get('#sefer-neden').setValue('GECIKME')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferGecikme).toHaveBeenCalledWith(
        11,
        expect.objectContaining({ kalkisGecikmeDk: 150, varisGecikmeDk: undefined }),
      )
      const farkli = kur('gecikme', { kalkisGecikmeDk: 150, varisGecikmeDk: 30 })
      expect((farkli.get('#sefer-varis-gecikme').element as HTMLInputElement).value).toBe('30')
    })

    it('varış gecikmesi girilirse gövdeye girer; DİĞER notu zorunlu', async () => {
      vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
      const w = kur('gecikme')
      await w.get('#sefer-kalkis-gecikme').setValue('30')
      await w.get('#sefer-varis-gecikme').setValue('45')
      await w.get('#sefer-neden').setValue('DIGER')
      expect(onay(w).attributes('disabled')).toBeDefined()
      await w.get('#sefer-neden-notu').setValue('Sinyal arızası')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(atamaApi.seferGecikme).toHaveBeenCalledWith(11, {
        kalkisGecikmeDk: 30,
        varisGecikmeDk: 45,
        neden: 'DIGER',
        nedenNotu: 'Sinyal arızası',
      })
    })

    it('dakika 0..1440 tam sayı olmalı; boş ya da aralık dışı değerde onay kapalı', async () => {
      const w = kur('gecikme')
      await w.get('#sefer-neden').setValue('GECIKME')
      for (const kotu of ['', '-1', '1441', '1.5']) {
        await w.get('#sefer-kalkis-gecikme').setValue(kotu)
        expect(onay(w).attributes('disabled')).toBeDefined()
      }
      for (const iyi of ['0', '1440']) {
        await w.get('#sefer-kalkis-gecikme').setValue(iyi)
        expect(onay(w).attributes('disabled')).toBeUndefined()
      }
      await w.get('#sefer-varis-gecikme').setValue('2000')
      expect(onay(w).attributes('disabled')).toBeDefined()
    })

    it('400 DOGRULAMA: alan hataları alanın altında görünür', async () => {
      vi.mocked(atamaApi.seferGecikme).mockRejectedValue(
        new ApiHata(400, 'DOGRULAMA', 'Girdi geçersiz.', {
          alanlar: { neden: 'Neden zorunludur.' },
        }),
      )
      const w = kur('gecikme')
      await w.get('#sefer-neden').setValue('GECIKME')
      await w.get('form').trigger('submit')
      await flushPromises()
      expect(w.get('#sefer-neden-hata').text()).toBe('Neden zorunludur.')
    })
  })

  it('Vazgeç hatasız kapatır (yenileme istemez)', async () => {
    const w = kur('iptal')
    await w.get('button[type="button"]:not([aria-label="Kapat"])').trigger('click')
    expect(w.emitted('kapat')).toEqual([[false]])
  })
})

describe('SeferDegisiklikDialog: hızlı gecikme (H4, AK-3.1..3.5)', () => {
  const cipler = (w: ReturnType<typeof kur>) => w.findAll('[role="group"] button')

  it('dk çipleri 15/30/60/90/120; tıklayınca değer yazılır, çip basılı olur, odak dk alanına döner', async () => {
    const w = mount(SeferDegisiklikDialog, {
      props: { kip: 'gecikme', seferId: 11, seferEtiketi: 'T1', kalkisGecikmeDk: 0 },
      attachTo: document.body,
    })
    expect(cipler(w).map((c) => c.text())).toEqual(['15 dk', '30 dk', '60 dk', '90 dk', '120 dk'])
    expect(cipler(w).every((c) => c.attributes('aria-pressed') === 'false')).toBe(true)
    await cipler(w)[1]!.trigger('click')
    const alan = w.get('#sefer-kalkis-gecikme')
    expect((alan.element as HTMLInputElement).value).toBe('30')
    expect(cipler(w)[1]!.attributes('aria-pressed')).toBe('true')
    expect(document.activeElement).toBe(alan.element)
    w.unmount()
  })

  it('açılışta odak dk alanında ve değer seçili (yazınca ezilir)', () => {
    const sec = vi.spyOn(HTMLInputElement.prototype, 'select') // jsdom number girdisinde seçim aralığı vermez
    const w = mount(SeferDegisiklikDialog, {
      props: { kip: 'gecikme', seferId: 11, seferEtiketi: 'T1', kalkisGecikmeDk: 20 },
      attachTo: document.body,
    })
    const alan = w.get('#sefer-kalkis-gecikme').element as HTMLInputElement
    expect(document.activeElement).toBe(alan)
    expect(sec.mock.instances[0]).toBe(alan)
    expect(alan.hasAttribute('autofocus')).toBe(true)
    sec.mockRestore()
    w.unmount()
  })

  it('AK-3.5/3.6: çip + Enter (form gönderimi) → gövde {kalkisGecikmeDk, neden:GECIKME}, varış yok, yazma/seçim yok', async () => {
    vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
    const w = kur('gecikme', { kalkisGecikmeDk: 0 })
    await cipler(w)[1]!.trigger('click')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.seferGecikme).toHaveBeenCalledWith(11, {
      kalkisGecikmeDk: 30,
      varisGecikmeDk: undefined,
      neden: 'GECIKME',
      nedenNotu: undefined,
    })
  })

  it('AK-3.4: varış alanı "Varış farklıysa" ayrıntısında; kayıtlı varış ≠ kalkış ise açık başlar', () => {
    const kapali = kur('gecikme', { kalkisGecikmeDk: 30, varisGecikmeDk: 30 })
    expect(kapali.get('details summary').text()).toBe('Varış farklıysa')
    expect(kapali.get('details').element.hasAttribute('open')).toBe(false)
    expect(kapali.find('details #sefer-varis-gecikme').exists()).toBe(true)
    const acik = kur('gecikme', { kalkisGecikmeDk: 30, varisGecikmeDk: 45 })
    expect(acik.get('details').element.hasAttribute('open')).toBe(true)
  })

  it('iptal kipinde çip ve varsayılan neden yok; "Önce neden seçin." korunur', () => {
    const w = kur('iptal')
    expect(w.find('[role="group"]').exists()).toBe(false)
    expect((w.get('#sefer-neden').element as HTMLSelectElement).value).toBe('')
    expect(w.get('.diyalog-neden').text()).toBe('Önce neden seçin.')
  })

  it('mevcut gecikme varsa "Şu an: N dk" ipucu görünür', () => {
    expect(kur('gecikme', { kalkisGecikmeDk: 20 }).text()).toContain('Şu an: 20 dk')
    expect(kur('gecikme', { kalkisGecikmeDk: 0 }).text()).not.toContain('Şu an')
  })
})

describe('SeferDegisiklikDialog: neden listesi akışa göre süzülür', () => {
  const secenekler = (w: ReturnType<typeof kur>) =>
    w.findAll('#sefer-neden option').map((o) => o.text())

  it('gecikme akışında "Sefer iptali" yok', () => {
    expect(secenekler(kur('gecikme'))).toEqual([
      'Seçin…',
      'Gecikme',
      'Hastalık',
      'İzin',
      'Belge sorunu',
      'Diğer',
    ])
  })

  it('iptal akışında "Sefer iptali" kalır', () => {
    expect(secenekler(kur('iptal'))).toContain('Sefer iptali')
  })
})

describe('SeferDegisiklikDialog: parçalı tren (F2c, devam kutusu)', () => {
  const parcali = { parcaNo: 1, parcaSayisi: 2 }

  it('parçasızda ya da son parçada kutu yok ve gövde eskisiyle aynı', async () => {
    vi.mocked(atamaApi.seferIptal).mockResolvedValue(sefer)
    expect(kur('iptal').find('#sefer-devam').exists()).toBe(false)
    expect(kur('gecikme', { parcaNo: 2, parcaSayisi: 2 }).find('#sefer-devam').exists()).toBe(false)
    const w = kur('iptal', { parcaNo: 2, parcaSayisi: 2 })
    await w.get('#sefer-neden').setValue('HASTALIK')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.seferIptal).toHaveBeenCalledWith(11, {
      neden: 'HASTALIK',
      nedenNotu: undefined,
    })
  })

  it('iptal: sonraki parçası olan seferde kutu varsayılan işaretli; gövdeye devam gider', async () => {
    vi.mocked(atamaApi.seferIptal).mockResolvedValue(sefer)
    const w = kur('iptal', parcali)
    const kutu = w.get('#sefer-devam')
    expect((kutu.element as HTMLInputElement).checked).toBe(true)
    expect(w.text()).toContain('Bu ve sonraki parçaları da iptal et')
    await w.get('#sefer-neden').setValue('HASTALIK')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.seferIptal).toHaveBeenCalledWith(11, {
      neden: 'HASTALIK',
      nedenNotu: undefined,
      devam: true,
    })
  })

  it('gecikme: kutu kapatılınca devam=false gider', async () => {
    vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
    const w = kur('gecikme', { ...parcali, kalkisGecikmeDk: 0 })
    expect(w.text()).toContain('Sonraki parçalara da yansıt (yalnız artırır)')
    await w.get('#sefer-devam').setValue(false)
    await w.get('#sefer-kalkis-gecikme').setValue(40)
    await w.get('#sefer-neden').setValue('GECIKME')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.seferGecikme).toHaveBeenCalledWith(11, {
      kalkisGecikmeDk: 40,
      varisGecikmeDk: undefined,
      neden: 'GECIKME',
      nedenNotu: undefined,
      devam: false,
    })
  })
  it('UX-2: gecikme AZALTILINCA (40 → 10, devam açık) bilgi mesajı; artırınca ya da devam kapalıyken yok', async () => {
    vi.mocked(atamaApi.seferGecikme).mockResolvedValue(sefer)
    const gonder = async (w: ReturnType<typeof kur>, dk: number) => {
      await w.get('#sefer-kalkis-gecikme').setValue(dk)
      await w.get('#sefer-neden').setValue('GECIKME')
      await w.get('form').trigger('submit')
      await flushPromises()
    }
    const mevcut = { ...parcali, kalkisGecikmeDk: 40, varisGecikmeDk: 40 }

    const azalt = kur('gecikme', mevcut)
    await gonder(azalt, 10)
    expect(azalt.emitted('tamam')![0]![0]).toContain('Sonraki parçalar değişmedi')

    const artir = kur('gecikme', mevcut)
    await gonder(artir, 60)
    expect(artir.emitted('tamam')).toEqual([['Gecikme kaydedildi.']])

    const kapali = kur('gecikme', mevcut)
    await kapali.get('#sefer-devam').setValue(false)
    await gonder(kapali, 10)
    expect(kapali.emitted('tamam')).toEqual([['Gecikme kaydedildi.']])

    const parcasiz = kur('gecikme', { kalkisGecikmeDk: 40, varisGecikmeDk: 40 })
    await gonder(parcasiz, 10)
    expect(parcasiz.emitted('tamam')).toEqual([['Gecikme kaydedildi.']])
  })
})
