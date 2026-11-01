import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { ApiHata } from '@/api/istemci'

vi.mock('./atamaApi', async (orijinal) => ({
  ...(await orijinal<typeof import('./atamaApi')>()),
  atamaApi: { kaldir: vi.fn() },
}))

import { atamaApi, kaldirMesaji } from './atamaApi'
import { GERI_AL_SURESI_MS, useGeriAl } from './useGeriAl'

const bekleyen = {
  atamaId: 77,
  seferId: 5,
  rol: 'ASIL' as const,
  mesaj: 'Atandı: Ali Veli (D001).',
}
const kur = () => effectScope().run(() => useGeriAl())!

describe('useGeriAl (MK-152, OK-37)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
  })
  afterEach(() => vi.useRealTimers())

  it('baslat: bekleyen görünür; 10 sn sonra kapanır', () => {
    const g = kur()
    g.baslat(bekleyen)
    expect(g.bekleyen.value).toEqual(bekleyen)
    vi.advanceTimersByTime(GERI_AL_SURESI_MS - 1)
    expect(g.bekleyen.value).not.toBeNull()
    vi.advanceTimersByTime(1)
    expect(g.bekleyen.value).toBeNull()
    expect(GERI_AL_SURESI_MS).toBe(10_000)
  })

  it('duraklat zamanlayıcıyı durdurur, surdur 10 sn yeniden başlatır', () => {
    const g = kur()
    g.baslat(bekleyen)
    vi.advanceTimersByTime(9_000)
    g.duraklat()
    vi.advanceTimersByTime(60_000)
    expect(g.bekleyen.value).not.toBeNull()
    g.surdur()
    vi.advanceTimersByTime(9_999)
    expect(g.bekleyen.value).not.toBeNull()
    vi.advanceTimersByTime(1)
    expect(g.bekleyen.value).toBeNull()
  })

  it('yeni atama öncekini ezer ve süreyi yeniden başlatır; temizle bekleyeni siler', () => {
    const g = kur()
    g.baslat(bekleyen)
    vi.advanceTimersByTime(8_000)
    g.baslat({ ...bekleyen, atamaId: 78 })
    vi.advanceTimersByTime(8_000)
    expect(g.bekleyen.value?.atamaId).toBe(78)
    g.temizle()
    expect(g.bekleyen.value).toBeNull()
  })

  it('geriAl: kaldır ucu atamaId ile çağrılır (yarış kapısı), bekleyen temizlenir, mesaj "Atama kaldırıldı."', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined)
    const g = kur()
    g.baslat(bekleyen)
    const sonuc = await g.geriAl()
    expect(atamaApi.kaldir).toHaveBeenCalledWith(5, {
      rol: 'ASIL',
      neden: 'DIGER',
      nedenNotu: 'Atama geri alındı (hızlı atama sonrası)',
      atamaId: 77,
    })
    expect(sonuc).toEqual({ tur: 'tamam', mesaj: 'Atama kaldırıldı.' })
    expect(g.bekleyen.value).toBeNull()
  })

  it('geriAl: başka atamayı kırmızı yaptıysa kaldirMesaji metni korunur', async () => {
    const k = {
      yeniKirmizi: [{ atamaId: 9, seferId: 2, trenNo: 'T9', nedenler: ['dinlenme'] }],
    }
    vi.mocked(atamaApi.kaldir).mockResolvedValue(k)
    const g = kur()
    g.baslat(bekleyen)
    const sonuc = await g.geriAl()
    expect(sonuc?.mesaj).toBe(kaldirMesaji(k))
    expect(sonuc?.mesaj).toContain('T9')
  })

  it('YARIŞ: araya başka atama girdi (409 ATAMA_DEGISTI) → "Atama bu arada değişti; geri alınmadı."', async () => {
    vi.mocked(atamaApi.kaldir).mockRejectedValue(new ApiHata(409, 'ATAMA_DEGISTI', 'sunucu metni'))
    const g = kur()
    g.baslat(bekleyen)
    const sonuc = await g.geriAl()
    expect(sonuc).toEqual({ tur: 'degisti', mesaj: 'Atama bu arada değişti; geri alınmadı.' })
    expect(g.bekleyen.value).toBeNull()
  })

  it('diğer hatalar: sunucu detail metni, tur=hata; ağ hatasında genel metin', async () => {
    vi.mocked(atamaApi.kaldir).mockRejectedValueOnce(new ApiHata(404, 'BULUNAMADI', 'Kayıt yok.'))
    const g = kur()
    g.baslat(bekleyen)
    expect(await g.geriAl()).toEqual({ tur: 'hata', mesaj: 'Kayıt yok.' })
    vi.mocked(atamaApi.kaldir).mockRejectedValueOnce(new TypeError('ağ'))
    g.baslat(bekleyen)
    expect(await g.geriAl()).toEqual({ tur: 'hata', mesaj: 'Atama geri alınamadı.' })
  })

  it('bekleyen yokken geriAl hiçbir şey çağırmaz', async () => {
    const g = kur()
    expect(await g.geriAl()).toBeNull()
    expect(atamaApi.kaldir).not.toHaveBeenCalled()
  })

  it('çift tetik: işlem sürerken ikinci geriAl çağrısı kaldır ucunu tekrar çağırmaz', async () => {
    vi.mocked(atamaApi.kaldir).mockResolvedValue(undefined)
    const g = kur()
    g.baslat(bekleyen)
    const [a, b] = await Promise.all([g.geriAl(), g.geriAl()])
    expect(atamaApi.kaldir).toHaveBeenCalledTimes(1)
    expect([a, b].filter(Boolean)).toHaveLength(1)
  })

  it('D1/B-04: geri al uçuştayken başlayan YENİ atamanın şeridi, eski işlem bitince silinmez', async () => {
    let bitir!: () => void
    vi.mocked(atamaApi.kaldir).mockImplementation(
      () => new Promise((coz) => (bitir = () => coz({} as never))),
    )
    const g = kur()
    g.baslat(bekleyen)
    const is = g.geriAl()
    g.baslat({ ...bekleyen, atamaId: 78, seferId: 6 })
    bitir()
    await is
    expect(g.bekleyen.value?.atamaId).toBe(78)
    vi.advanceTimersByTime(GERI_AL_SURESI_MS - 1)
    expect(g.bekleyen.value).not.toBeNull() // zamanlayıcı da yeni atamanın
    vi.advanceTimersByTime(1)
    expect(g.bekleyen.value).toBeNull()
  })
})
