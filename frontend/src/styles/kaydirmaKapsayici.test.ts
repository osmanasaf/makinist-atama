import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

// vitest kökü frontend/'dir (vitest.config.ts)
const stilDizini = join(process.cwd(), 'src', 'styles')

/**
 * QA tur 2 YENİ-4: yatay kaydıran (overflow-x: auto) bir kap `position: relative` olmadan bırakılırsa, içindeki mutlak konumlu `.gorunmez` (yalnız ekran okuyucu metni) hücre adları kabın
 * dışına taşıp belgeyi yatay kaydırır (%200 yakınlaştırmada /yazdir/cizelge). `.tablo-kap` bunu zaten böyle çözmüştü; yazdırma kabı unutulmuştu.
 */
describe('yatay kaydıran kaplar', () => {
  it('overflow-x: auto olan her kural position: relative da taşır', () => {
    const eksik: string[] = []
    for (const dosya of readdirSync(stilDizini).filter((f) => f.endsWith('.css'))) {
      const css = readFileSync(join(stilDizini, dosya), 'utf8')
      for (const [, secici, govde] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        if (/overflow-x:\s*auto/.test(govde!) && !/position:\s*relative/.test(govde!)) {
          eksik.push(`${dosya}: ${secici!.trim()}`)
        }
      }
    }
    expect(eksik).toEqual([])
  })
})
