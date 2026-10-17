import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn(), post: vi.fn() } }))

import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'
import {
  cikisYap,
  girisYap,
  kullanici,
  makinistMi,
  oturumYukle,
  yoneticiMi,
  yuklendi,
} from './oturum'

const yonetici: KullaniciDto = {
  id: 1,
  eposta: 'a@b.c',
  adSoyad: 'Ayşe Yönetici',
  rol: 'YONETICI',
  aktif: true,
}

describe('oturum', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })

  it('oturumYukle: oturum varsa kullanıcıyı koyar', async () => {
    vi.mocked(api.get).mockResolvedValue(yonetici)
    await oturumYukle()
    expect(kullanici.value).toEqual(yonetici)
    expect(yoneticiMi.value).toBe(true)
    expect(yuklendi.value).toBe(true)
  })

  it('oturumYukle: hata (401 ya da ağ) kullanıcıyı boşaltır, yuklendi yine true', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('401'))
    await oturumYukle()
    expect(kullanici.value).toBeNull()
    expect(yoneticiMi.value).toBe(false)
    expect(yuklendi.value).toBe(true)
  })

  it('girisYap: sunucunun döndürdüğü kullanıcıyı saklar, hatayı dışarı fırlatır', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ ...yonetici, rol: 'ATAMACI' })
    await girisYap('a@b.c', 'parola-uzun-123')
    expect(api.post).toHaveBeenCalledWith('/api/auth/giris', {
      eposta: 'a@b.c',
      parola: 'parola-uzun-123',
    })
    expect(kullanici.value?.rol).toBe('ATAMACI')
    expect(yoneticiMi.value).toBe(false)

    kullanici.value = null
    vi.mocked(api.post).mockRejectedValueOnce(new Error('GIRIS_HATALI'))
    await expect(girisYap('a@b.c', 'x')).rejects.toThrow('GIRIS_HATALI')
    expect(kullanici.value).toBeNull()
  })

  it('cikisYap: çıkış isteği başarısız olsa da yerel oturumu temizler', async () => {
    kullanici.value = yonetici
    vi.mocked(api.post).mockRejectedValue(new Error('ağ'))
    await expect(cikisYap()).rejects.toThrow('ağ')
    expect(kullanici.value).toBeNull()
  })
})

describe('oturum: makinist', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kullanici.value = null
    yuklendi.value = false
  })

  it('makinistMi yalnız MAKINIST rolünde doğru; geçici parola bayrağı kullanıcıda taşınır', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      id: 3,
      eposta: 'd001',
      adSoyad: 'Ali Veli',
      rol: 'MAKINIST',
      aktif: true,
      makinistId: 7,
      parolaDegistirmeli: true,
    })
    await girisYap('d001', 'gecici-parola-1')
    expect(makinistMi.value).toBe(true)
    expect(yoneticiMi.value).toBe(false)
    expect(kullanici.value?.parolaDegistirmeli).toBe(true)
    kullanici.value = null
    expect(makinistMi.value).toBe(false)
  })
})
