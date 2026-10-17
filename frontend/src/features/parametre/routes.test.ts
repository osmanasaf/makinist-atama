import { describe, expect, it } from 'vitest'
import routes from './routes'

describe('parametre rotaları', () => {
  it('özel kurallar sayfası menüde 45. sırada ve her role açıktır', () => {
    const ozel = routes.find((r) => r.name === 'ozel-kurallar')!
    expect(ozel.path).toBe('/ozel-kurallar')
    expect(ozel.meta).toMatchObject({ menu: { ad: 'Özel kurallar', sira: 45 } })
    expect(ozel.meta?.rol).toBeUndefined()
  })

  it('Ayarlar menüde 40. sırada ve yalnız yöneticiye açıktır', () => {
    const ayarlar = routes.find((r) => r.name === 'ayarlar')!
    expect(ayarlar.path).toBe('/ayarlar')
    expect(ayarlar.meta).toMatchObject({ menu: { ad: 'Ayarlar', sira: 40 }, rol: 'Y' })
  })

  it('eski /parametreler adresi menüsüz olarak /ayarlar sayfasına yönlenir', () => {
    const eski = routes.find((r) => r.path === '/parametreler')!
    expect(eski.redirect).toBe('/ayarlar')
    expect(eski.meta?.menu).toBeUndefined()
    expect(routes.some((r) => r.meta?.menu?.ad === 'Parametreler')).toBe(false)
  })
})
