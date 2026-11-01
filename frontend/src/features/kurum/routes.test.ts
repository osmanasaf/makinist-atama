import { describe, expect, it } from 'vitest'
import kurumRotalari from './routes'

describe('kurum rotası (F1)', () => {
  it('/kurum yalnız yöneticiye açıktır, menüde yoktur (Ayarlar sayfasından ve kurulum sihirbazından erişilir)', () => {
    expect(kurumRotalari).toHaveLength(1)
    expect(kurumRotalari[0]!.path).toBe('/kurum')
    expect(kurumRotalari[0]!.meta).toMatchObject({ rol: 'Y', baslik: 'Kurum bilgisi' })
    expect(kurumRotalari[0]!.meta?.menu).toBeUndefined()
  })
})
