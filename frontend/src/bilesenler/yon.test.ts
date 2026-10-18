import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/** Bağımlılık yönü (MK-131): features/* -> bilesenler/* -> vue. bilesenler özellik bilmez. */
describe('bilesenler bağımlılık yönü', () => {
  it('bilesenler/ içinde @/features içe aktarımı yok', () => {
    const dizin = join(process.cwd(), 'src', 'bilesenler')
    const ihlal = readdirSync(dizin)
      .filter((d) => /\.(vue|ts)$/.test(d) && !d.endsWith('.test.ts'))
      .filter((d) => /from\s+['"]@\/features\//.test(readFileSync(join(dizin, d), 'utf8')))
    expect(ihlal).toEqual([])
  })
})
