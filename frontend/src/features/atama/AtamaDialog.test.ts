import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { ata: vi.fn(), kaldir: vi.fn() },
}))

import AtamaDialog from './AtamaDialog.vue'
import { atamaApi, type Aday } from './atamaApi'

const temiz: Aday = {
  makinistId: 7,
  sicil: 'D007',
  adSoyad: 'Ali Veli',
  bagliGar: 'Ankara',
  uygun: true,
  ihlaller: [],
  buAySaat: 100,
  buAyKm: 5000,
  buSeferdeRol: null,
}
const uyarili: Aday = {
  ...temiz,
  ihlaller: [
    {
      kod: 'K-20',
      siddet: 'UYARI',
      olculen: 9,
      esik: 8,
      birim: 's',
      metin: 'Günlük 9 s > 8 s',
    },
  ],
}
const mevcut = {
  atamaId: 5,
  rol: 'ASIL' as const,
  makinistId: 9,
  sicil: 'D009',
  ad: 'Eski Kişi',
}

function kur(ozellik: Record<string, unknown> = {}) {
  return mount(AtamaDialog, {
    props: {
      seferId: 11,
      seferEtiketi: 'T1001 Ankara – Eskişehir 08:30',
      rol: 'ASIL',
      ...ozellik,
    },
  })
}
const onay = (w: ReturnType<typeof kur>) => w.get('button[type="submit"]')
const sonuc = {} as Awaited<ReturnType<typeof atamaApi.ata>>

describe('AtamaDialog', () => {
  beforeEach(() => vi.resetAllMocks())

  it('uyarı yoksa ve rol boşsa doğrudan onaylanır; gövdede gerekçe/neden yoktur', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue(sonuc)
    const w = kur({ aday: temiz })
    expect(onay(w).attributes('disabled')).toBeUndefined()
    expect(w.find('#atama-gerekce').exists()).toBe(false)
    expect(w.find('#atama-neden').exists()).toBe(false)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledWith(11, {
      makinistId: 7,
      rol: 'ASIL',
      gerekce: undefined,
    })
    expect(w.emitted('tamam')).toEqual([['Atama yapıldı.']])
  })

  it('UYARI varken gerekçe boşsa Onayla kapalı; yazınca açılır ve gerekçe gönderilir', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue(sonuc)
    const w = kur({ aday: uyarili })
    expect(w.text()).toContain('Günlük 9 s > 8 s')
    expect(onay(w).attributes('disabled')).toBeDefined()
    await w.get('#atama-gerekce').setValue('   ')
    expect(onay(w).attributes('disabled')).toBeDefined()
    await w.get('#atama-gerekce').setValue('Vardiya amiri onayladı')
    expect(onay(w).attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledWith(11, {
      makinistId: 7,
      rol: 'ASIL',
      gerekce: 'Vardiya amiri onayladı',
    })
  })

  it('değiştirmede neden zorunlu; DİĞER seçilince neden notu da zorunlu', async () => {
    vi.mocked(atamaApi.ata).mockResolvedValue(sonuc)
    const w = kur({ aday: temiz, mevcut })
    expect(w.text()).toContain('Eski Kişi (D009)')
    expect(onay(w).attributes('disabled')).toBeDefined()
    await w.get('#atama-neden').setValue('HASTALIK')
    expect(onay(w).attributes('disabled')).toBeUndefined()
    await w.get('#atama-neden').setValue('DIGER')
    expect(onay(w).attributes('disabled')).toBeDefined()
    await w.get('#atama-neden-notu').setValue('Aile acili')
    expect(onay(w).attributes('disabled')).toBeUndefined()
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.ata).toHaveBeenCalledWith(11, {
      makinistId: 7,
      rol: 'ASIL',
      gerekce: undefined,
      neden: 'DIGER',
      nedenNotu: 'Aile acili',
    })
  })

  it('neden seçenekleri Türkçe etiketlidir', () => {
    const w = kur({ aday: temiz, mevcut })
    const etiketler = w.findAll('#atama-neden option').map((o) => o.text())
    expect(etiketler).toEqual([
      'Seçin…',
      'Sefer iptali',
      'Gecikme',
      'Hastalık',
      'İzin',
      'Belge sorunu',
      'Diğer',
    ])
  })

  it('422 KURAL_IHLALI: diyalog açık kalır, ihlaller listelenir, tamam yayınlanmaz', async () => {
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Atama kuralı engeli var.', {
        ihlaller: [
          {
            kod: 'K-11',
            siddet: 'ENGEL',
            olculen: 8,
            esik: 11,
            birim: 's',
            metin: 'Dinlenme 8 s < 11 s',
          },
          {
            kod: 'K-02',
            siddet: 'ENGEL',
            olculen: 0,
            esik: 1,
            birim: '',
            metin: 'Hat yetkisi yok',
          },
        ],
      }),
    )
    const w = kur({ aday: temiz })
    await w.get('form').trigger('submit')
    await flushPromises()
    const uyari = w.get('[role="alert"]').text()
    expect(uyari).toContain('Dinlenme 8 s < 11 s')
    expect(uyari).toContain('Hat yetkisi yok')
    expect(w.emitted('tamam')).toBeUndefined()
    expect(w.get('dialog').attributes('open')).toBeDefined()
    // Vazgeçince masa sunucu durumunu yeniden okusun diye yenile=true yayılır.
    await w.get('button[type="button"]').trigger('click')
    expect(w.emitted('kapat')).toEqual([[true]])
  })

  it('422: ihlal metni başlıkta (detail) zaten geçiyorsa listede tekrarlanmaz', async () => {
    const ih = (kod: string, metin: string) => ({
      kod,
      siddet: 'ENGEL' as const,
      olculen: 0,
      esik: 1,
      birim: '',
      metin,
    })
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Kural ihlali: D010 bu sefere atanamaz.', {
        ihlaller: [ih('K-01', 'D010 bu sefere atanamaz.'), ih('K-11', 'Dinlenme 8 s < 11 s')],
      }),
    )
    const w = kur({ aday: temiz })
    await w.get('form').trigger('submit')
    await flushPromises()
    const uyari = w.get('[role="alert"]').text()
    expect(uyari.split('D010 bu sefere atanamaz.')).toHaveLength(2) // tam bir kez
    expect(uyari).toContain('Kural ihlali:')
    expect(uyari).toContain('Dinlenme 8 s < 11 s')
  })

  it('400 DOGRULAMA: alan hataları ilgili alanın altında görünür', async () => {
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(400, 'DOGRULAMA', 'Girdi geçersiz.', {
        alanlar: { gerekce: 'Gerekçe zorunludur.' },
      }),
    )
    const w = kur({ aday: uyarili })
    await w.get('#atama-gerekce').setValue('x')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('#atama-gerekce-hata').text()).toBe('Gerekçe zorunludur.')
    expect(w.get('#atama-gerekce').attributes('aria-invalid')).toBe('true')
  })

  it('409: detail metni gösterilir', async () => {
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(409, 'ZATEN_ATANMIS', 'Bu sefer zaten atanmış.'),
    )
    const w = kur({ aday: temiz })
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.get('[role="alert"]').text()).toBe('Bu sefer zaten atanmış.')
  })

  it('çift gönderimde API bir kez çağrılır (meşgul koruması)', async () => {
    let bitir!: () => void
    vi.mocked(atamaApi.ata).mockReturnValue(new Promise((cozum) => (bitir = () => cozum(sonuc))))
    const w = kur({ aday: temiz })
    await w.get('form').trigger('submit')
    await w.get('form').trigger('submit')
    expect(atamaApi.ata).toHaveBeenCalledTimes(1)
    expect(onay(w).attributes('disabled')).toBeDefined()
    bitir()
    await flushPromises()
  })

  it('kaldırma kipi: neden zorunlu, DELETE neden parametreleriyle çağrılır', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined)
    const w = kur({ mevcut })
    expect(w.text()).toContain('Atamayı kaldır')
    expect(onay(w).attributes('disabled')).toBeDefined()
    await w.get('#atama-neden').setValue('IZIN')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(atamaApi.kaldir).toHaveBeenCalledWith(11, {
      rol: 'ASIL',
      neden: 'IZIN',
      nedenNotu: undefined,
    })
    expect(w.emitted('tamam')).toEqual([['Atama kaldırıldı.']])
  })

  it('kaldırma başka atamayı kırmızı yaptıysa mesaj bunu söyler (MK-5 kaldırma etkisi)', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue({
      yeniKirmizi: [
        {
          atamaId: 9,
          seferId: 21,
          trenNo: 'AE-016',
          nedenler: ['2. ardışık gece haftası > 1'],
        },
      ],
    })
    const w = kur({ mevcut })
    await w.get('#atama-neden').setValue('IZIN')
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.emitted('tamam')![0]![0]).toContain(
      'Dikkat: bu makinistin 1 başka görevi kırmızı oldu',
    )
    expect(w.emitted('tamam')![0]![0]).toContain('AE-016 (2. ardışık gece haftası > 1)')
  })

  it('kaldırma aynı makinistin intikalini kırmızı yaptıysa mesaj bunu da söyler (review W2)', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue({
      yeniKirmizi: [],
      yeniKirmiziIntikal: [
        {
          intikalId: 3,
          ozet: 'İntikal · Eskişehir 10:00 → Ankara 11:30 · Servis',
          nedenler: ['Konum uyuşmazlığı'],
        },
      ],
    })
    const w = kur({ mevcut })
    await w.get('#atama-neden').setValue('IZIN')
    await w.get('form').trigger('submit')
    await flushPromises()
    const mesaj = w.emitted('tamam')![0]![0] as string
    expect(mesaj).toContain('Dikkat: bu makinistin 1 başka görevi kırmızı oldu')
    expect(mesaj).toContain('İntikal · Eskişehir 10:00 → Ankara 11:30 · Servis (Konum uyuşmazlığı)')
  })

  it('Vazgeç hatasız kapatır (yenileme istemez)', async () => {
    const w = kur({ aday: temiz })
    await w.get('button[type="button"]').trigger('click')
    expect(w.emitted('kapat')).toEqual([[false]])
  })
})

describe('AtamaDialog: nöbet notu ve yön etiketleri', () => {
  beforeEach(() => vi.resetAllMocks())
  const nobetIhlali = {
    kod: 'K-30',
    siddet: 'ENGEL' as const,
    olculen: 10.95,
    esik: 7.5,
    birim: 's',
    metin: 'Gece nöbeti süresi 10 s 57 dk > 7 s 30 dk',
  }

  it('422 ihlalinde nöbet geçiyorsa not görünür', async () => {
    vi.mocked(atamaApi.ata).mockRejectedValue(
      new ApiHata(422, 'KURAL_IHLALI', 'Atama kuralı engeli var.', {
        ihlaller: [nobetIhlali],
      }),
    )
    const w = kur({ aday: temiz })
    expect(w.find('[data-test="nobet-notu"]').exists()).toBe(false)
    await w.get('form').trigger('submit')
    await flushPromises()
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })

  it('aday uyarısında nöbet geçiyorsa not görünür', () => {
    const w = kur({
      aday: { ...temiz, ihlaller: [{ ...nobetIhlali, siddet: 'UYARI' }] },
    })
    expect(w.findAll('[data-test="nobet-notu"]')).toHaveLength(1)
  })

  it('değiştirme: "Göreve gelecek" ve "Görevden çıkacak" etiketleri', () => {
    const w = kur({ aday: temiz, mevcut })
    expect(w.text()).toContain('Göreve gelecek: Ali Veli (D007)')
    expect(w.text()).toContain('Görevden çıkacak: Eski Kişi (D009)')
  })

  it('kaldırma: yalnız "Kaldırılacak"', () => {
    const w = kur({ mevcut })
    expect(w.text()).toContain('Kaldırılacak: Eski Kişi (D009)')
    expect(w.text()).not.toContain('Göreve gelecek')
  })
})
