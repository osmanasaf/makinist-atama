import { describe, expect, it } from 'vitest'
import masa from '../features/atama/AtamaMasasi.vue?raw'

// H2 (AK-1.1): aynı simge adı iki farklı anlamda kullanılmaz. Rota menü simgeleri ve masadaki `Simge ad=` kullanımları taranır.
const rotalar = import.meta.glob<{
  default: { path: string; meta?: { menu?: { simge?: string } } }[]
}>('../features/*/routes.ts', { eager: true })
const menuSimgeleri = Object.values(rotalar)
  .flatMap((m) => m.default)
  .map((r) => r.meta?.menu?.simge)
  .filter(Boolean)

describe('simge çakışmaları (H2)', () => {
  it('gecikme simgesi menüde kullanılmaz (yalnız gecikme bağlamı)', () => {
    expect(menuSimgeleri).not.toContain('gecikme')
  })

  it('ay simgesi menüde kullanılmaz (yalnız gece göstergesi)', () => {
    expect(menuSimgeleri).not.toContain('ay')
  })

  it('takas simgesi menüde yalnız Takas Talepleri için; masada Yerine ata kullanmaz', () => {
    expect(menuSimgeleri.filter((s) => s === 'takas')).toHaveLength(1)
    const yerineAta = masa.slice(masa.indexOf('seferinde yerine ata'))
    expect(yerineAta.slice(0, 600)).not.toContain('ad="takas"')
    expect(yerineAta.slice(0, 600)).toContain('ad="kisi-degistir"')
  })

  it('Puantaj tablo, Konaklama Raporu yatak-belge simgesini kullanır', () => {
    expect(menuSimgeleri).toContain('tablo')
    expect(menuSimgeleri).toContain('yatak-belge')
  })
})
