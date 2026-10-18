import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('./takasApi', () => ({
  takasApi: { detay: vi.fn(), onay: vi.fn(), red: vi.fn(), liste: vi.fn() },
}))

import { ApiHata } from '@/api/istemci'
import { takasApi, type TakasDetay, type TakasSatiri } from './takasApi'
import TakasOnayDialog from './TakasOnayDialog.vue'
import type { TakasIhlali } from './takasTipleri'

function satir(p: Partial<TakasSatiri> = {}): TakasSatiri {
  return {
    id: 7,
    tur: 'TAKAS',
    durum: 'ATAMACI_ONAYI_BEKLIYOR',
    kayitliDurum: 'ATAMACI_ONAYI_BEKLIYOR',
    kaynakSeferId: 1,
    kaynakMakinistId: 1,
    kaynakSicil: 'D001',
    kaynakAd: 'Ali Veli',
    kaynakGorev: 'Asıl · Ankara 15.10 08:00 → Ankara 15.10 09:00',
    hedefMakinistId: 2,
    hedefSicil: 'D002',
    hedefAd: 'Ayşe Kaya',
    hedefGorev: 'Yardımcı · Ankara 16.10 08:00 → Ankara 16.10 09:00',
    neden: 'IZIN',
    nedenNotu: 'düğün',
    olusturma: '2026-10-13T07:00:00Z',
    sonKabul: '2026-10-14T07:00:00Z',
    sonKalkis: '2026-10-15T05:00:00Z',
    ...p,
  }
}

const ihlal = (sicil: string, siddet: 'ENGEL' | 'UYARI', metin: string): TakasIhlali => ({
  sicil,
  ihlal: { kod: 'K-11', siddet, olculen: 10.98, esik: 11, birim: 'saat', metin },
})

function detay(p: Partial<TakasDetay> = {}): TakasDetay {
  return { talep: satir(), ihlaller: [], oneri: null, ...p }
}

async function kur(d: TakasDetay) {
  vi.mocked(takasApi.detay).mockResolvedValue(d)
  const w = mount(TakasOnayDialog, { props: { talepId: 7 }, attachTo: document.body })
  await flushPromises()
  return w
}

const dugme = (w: ReturnType<typeof mount>, ad: string) =>
  w.findAll('button').find((b) => b.text() === ad)!

describe('TakasOnayDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('ayrıntıyı gösterir: iki taraf, neden notu, ihlalsizse ilgili not; Onayla / Reddet / Kapat', async () => {
    const w = await kur(detay())
    expect(w.text()).toContain('D001 (Ali Veli)')
    expect(w.text()).toContain('D002 (Ayşe Kaya)')
    expect(w.text()).toContain('düğün')
    expect(w.text()).toContain('ihlal yok')
    // durum satırı: simge (ATAMACI_ONAYI_BEKLIYOR = saat) + metin
    expect(w.text()).toContain('Atamacı onayı bekleniyor')
    expect(w.find('svg[data-simge="saat"]').exists()).toBe(true)
    expect(w.findAll('button:not([aria-label="Kapat"])').map((b) => b.text())).toEqual([
      'Onayla',
      'Reddet',
      'Kapat',
    ])
  })

  it('ihlal ayrıntısı hangi makinist hangi kural olarak gösterilir (ENGEL engel simgesi / UYARI uyarı simgesi, metinle)', async () => {
    const w = await kur(
      detay({
        ihlaller: [
          ihlal('D001', 'ENGEL', 'Dinlenme 10,98 saat < 11'),
          ihlal('D002', 'UYARI', 'Belge yaklaşıyor'),
        ],
      }),
    )
    const liste = w.get('ul.ihlal-listesi').text()
    expect(liste).toContain('D001')
    expect(liste).toContain('Dinlenme 10,98 saat')
    expect(liste).toContain('D002')
    expect(liste).toContain('Belge yaklaşıyor')
    expect(w.find('ul.ihlal-listesi svg[data-simge="engel"]').exists()).toBe(true)
    expect(w.find('ul.ihlal-listesi svg[data-simge="uyari"]').exists()).toBe(true)
    expect(w.text()).toContain('ENGEL varken onaylanamaz')
    expect(dugme(w, 'Onayla').attributes('disabled')).toBeDefined()
  })

  it('onay 422: ihlal listesi gösterilir, talep açık kalır (dialog kapanmaz, tamam olayı yok)', async () => {
    const w = await kur(detay())
    vi.mocked(takasApi.onay).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Kural ihlali: bu takas kurallara uymuyor.', {
        ihlaller: [ihlal('D001', 'ENGEL', 'Dinlenme yetersiz')] as never,
      }),
    )
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('talep açık kaldı')
    expect(w.get('ul.ihlal-listesi').text()).toContain('D001')
    expect(w.get('ul.ihlal-listesi').text()).toContain('Dinlenme yetersiz')
    expect(w.emitted('tamam')).toBeUndefined()
    expect(w.find('dialog').exists()).toBe(true)
  })

  it('409 bayat: sunucu mesajı gösterilir ve ayrıntı yeniden okunur', async () => {
    const w = await kur(detay())
    vi.mocked(takasApi.onay).mockRejectedValue(
      new ApiHata(409, 'TALEP_BAYAT', 'Talep bayatladı: görevlerden biri değişti.'),
    )
    vi.mocked(takasApi.detay).mockResolvedValue(detay({ talep: satir({ durum: 'BAYAT' }) }))
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('bayatladı')
    expect(takasApi.detay).toHaveBeenCalledTimes(2)
    expect(w.text()).toContain('onaylanamaz')
    expect(w.findAll('button').map((b) => b.text())).not.toContain('Onayla')
  })

  it('etkin durumu BAYAT olan ama kapanışı yazılmamış talep reddedilerek kapatılabilir (zincirleme talep)', async () => {
    const w = await kur(detay({ talep: satir({ durum: 'BAYAT' }) }))
    expect(w.findAll('button:not([aria-label="Kapat"])').map((b) => b.text())).toEqual([
      'Reddet',
      'Kapat',
    ])
    vi.mocked(takasApi.red).mockResolvedValue(satir({ durum: 'REDDEDILDI' }))
    await dugme(w, 'Reddet').trigger('click')
    await flushPromises()
    expect(takasApi.red).toHaveBeenCalledWith(7)
    expect(w.emitted('tamam')).toHaveLength(1)
  })

  it('kapanışı yazılmış bayat talepte Reddet yok', async () => {
    const w = await kur(detay({ talep: satir({ durum: 'BAYAT', kayitliDurum: 'BAYAT' }) }))
    expect(w.findAll('button:not([aria-label="Kapat"])').map((b) => b.text())).toEqual(['Kapat'])
  })

  it('UYARI varken gerekçe zorunlu: boşken istek gitmez, alanla ilişkili hata; yazılınca gider', async () => {
    const w = await kur(detay({ ihlaller: [ihlal('D001', 'UYARI', 'Dinlenme sınırda')] }))
    await dugme(w, 'Onayla').trigger('click')
    expect(takasApi.onay).not.toHaveBeenCalled()
    const alan = w.get('#takas-gerekce')
    expect(alan.attributes('aria-invalid')).toBe('true')
    expect(w.get('#' + alan.attributes('aria-describedby')!).text()).toContain('gerekçe')

    vi.mocked(takasApi.onay).mockResolvedValue(satir({ durum: 'ONAYLANDI' }))
    await alan.setValue('vardiya amiri onayladı')
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(takasApi.onay).toHaveBeenCalledWith(7, {
      yeniMakinistId: undefined,
      gerekce: 'vardiya amiri onayladı',
    })
    expect(w.emitted('tamam')).toHaveLength(1)
  })

  it('sunucu 400 gerekçe hatası alanla ilişkili gösterilir', async () => {
    const w = await kur(detay())
    vi.mocked(takasApi.onay).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Kural uyarısı var.', {
        alanlar: { gerekce: 'Kural uyarısı var; takasın onayı için gerekçe yazılmalı.' },
      }),
    )
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(w.get('#takas-gerekce-hata').text()).toContain('gerekçe yazılmalı')
  })

  it('bırakma: önerilerden biri seçilir; yeniMakinistId gider; "yalnız kaldır" seçilirse gitmez', async () => {
    const oneri = {
      oneriler: [
        {
          makinistId: 3,
          sicil: 'D003',
          adSoyad: 'Can Er',
          bagliGar: 'Ankara',
          buAySaat: 40,
          buAyKm: 900,
          uyarilar: [],
          gerekce: '',
        },
        {
          makinistId: 4,
          sicil: 'D004',
          adSoyad: 'Deniz Su',
          bagliGar: 'Ankara',
          buAySaat: 42,
          buAyKm: 950,
          uyarilar: [],
          gerekce: '',
        },
      ],
      uygunAdayYok: false,
      mesaj: null,
    }
    const w = await kur(
      detay({
        talep: satir({
          tur: 'BIRAK',
          hedefSicil: null,
          hedefAd: null,
          hedefGorev: null,
          hedefMakinistId: null,
        }),
        oneri,
      }),
    )
    expect(w.text()).toContain('D003 (Can Er)')
    vi.mocked(takasApi.onay).mockResolvedValue(satir({ durum: 'ONAYLANDI' }))
    await w.findAll('input[type="radio"]')[1]!.setValue(true)
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(takasApi.onay).toHaveBeenLastCalledWith(7, { yeniMakinistId: 4, gerekce: undefined })

    const k = await kur(
      detay({
        talep: satir({ tur: 'BIRAK', hedefSicil: null }),
        oneri: { oneriler: [], uygunAdayYok: true, mesaj: 'Uygun aday yok. 0/60 uygun' },
      }),
    )
    expect(k.text()).toContain('Uygun aday yok')
    await dugme(k, 'Onayla').trigger('click')
    await flushPromises()
    expect(takasApi.onay).toHaveBeenLastCalledWith(7, {
      yeniMakinistId: undefined,
      gerekce: undefined,
    })
  })

  it('kondüktör bırakma: sunucunun kondüktör önerisi varsayılan seçilir ve seçimsiz Onayla onu gönderir', async () => {
    const w = await kur(
      detay({
        talep: satir({
          tur: 'BIRAK',
          kaynakSicil: 'K001',
          hedefSicil: null,
          hedefAd: null,
          hedefGorev: null,
          hedefMakinistId: null,
        }),
        oneri: {
          oneriler: [
            {
              makinistId: 9,
              sicil: 'K002',
              adSoyad: 'Demo Kondüktör 02',
              bagliGar: 'Ankara',
              buAySaat: 10,
              buAyKm: 300,
              uyarilar: [],
              gerekce: '',
            },
          ],
          uygunAdayYok: false,
          mesaj: null,
        },
      }),
    )
    expect(w.text()).toContain('K002 (Demo Kondüktör 02)')
    vi.mocked(takasApi.onay).mockResolvedValue(satir({ durum: 'ONAYLANDI' }))
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    expect(takasApi.onay).toHaveBeenLastCalledWith(7, { yeniMakinistId: 9, gerekce: undefined })
  })
  it('reddet: red çağrılır, tamam olayı', async () => {
    const w = await kur(detay())
    vi.mocked(takasApi.red).mockResolvedValue(satir({ durum: 'REDDEDILDI' }))
    await dugme(w, 'Reddet').trigger('click')
    await flushPromises()
    expect(takasApi.red).toHaveBeenCalledWith(7)
    expect(w.emitted('tamam')).toHaveLength(1)
  })

  it('yükleme hatası gösterilir; Kapat çalışır', async () => {
    vi.mocked(takasApi.detay).mockRejectedValue(new ApiHata(404, 'BULUNAMADI', 'Talep bulunamadı.'))
    const w = mount(TakasOnayDialog, { props: { talepId: 7 }, attachTo: document.body })
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('Talep bulunamadı.')
    await dugme(w, 'Kapat').trigger('click')
    expect(w.emitted('kapat')).toHaveLength(1)
  })

  it('W3: onay isteği sürerken × (ve Esc) diyaloğu kapatmaz; sökülen bileşenden tamam yayılmaz', async () => {
    let bitir: () => void = () => {}
    vi.mocked(takasApi.onay).mockReturnValue(new Promise<void>((c) => (bitir = c)) as never)
    const w = await kur(detay())
    await dugme(w, 'Onayla').trigger('click')
    await flushPromises()
    await w.get('button[aria-label="Kapat"]').trigger('click')
    w.get('dialog').element.dispatchEvent(new Event('cancel', { cancelable: true }))
    expect(w.emitted('kapat')).toBeUndefined()
    bitir()
    await flushPromises()
    expect(w.emitted('tamam')).toHaveLength(1)
  })
})
