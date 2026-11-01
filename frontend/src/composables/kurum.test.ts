import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/istemci', () => ({ api: { get: vi.fn() } }))

import { api } from '@/api/istemci'
import {
  URUN_ADI,
  kurumAdi,
  kurumAdiGirildi,
  kurumGuncelle,
  kurumSifirla,
  kurumYukle,
  logoAdresi,
} from './kurum'

describe('kurum (F1)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    kurumSifirla()
  })

  it('boş kurulumda ürün adı görünür ve logo yoktur', () => {
    expect(URUN_ADI).toBe('Makinist Atama')
    expect(kurumAdi.value).toBe('Makinist Atama')
    expect(kurumAdiGirildi.value).toBe(false)
    expect(logoAdresi.value).toBeNull()
  })

  it('kurumYukle: anonim uçtan okur; ad ve logo adresi (sürümlü) oluşur', async () => {
    vi.mocked(api.get).mockResolvedValue({
      ad: ' Örnek İşletme (demo) ',
      logoVar: true,
      logoSurum: 4,
    })
    await kurumYukle()
    expect(api.get).toHaveBeenCalledWith('/api/kurum/acik')
    expect(kurumAdi.value).toBe('Örnek İşletme (demo)')
    expect(kurumAdiGirildi.value).toBe(true)
    expect(logoAdresi.value).toBe('/api/kurum/logo?s=4')
  })

  it('uç hatası ekranı bozmaz: hata yutulur, ürün adı kalır (AK-1.8)', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('ağ'))
    await expect(kurumYukle()).resolves.toBeUndefined()
    expect(kurumAdi.value).toBe('Makinist Atama')
    expect(logoAdresi.value).toBeNull()
  })

  it('hata, önceki başarılı bilgiyi silmez', async () => {
    kurumGuncelle({ ad: 'Örnek İşletme (demo)', logoVar: false, logoSurum: 1 })
    vi.mocked(api.get).mockRejectedValue(new Error('ağ'))
    await kurumYukle()
    expect(kurumAdi.value).toBe('Örnek İşletme (demo)')
  })

  it('boşluktan ibaret ad ürün adına döner; logoVar=false adres vermez', () => {
    kurumGuncelle({ ad: '   ', logoVar: false, logoSurum: 9 })
    expect(kurumAdi.value).toBe('Makinist Atama')
    expect(logoAdresi.value).toBeNull()
  })
})
