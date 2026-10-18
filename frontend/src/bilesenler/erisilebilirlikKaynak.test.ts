import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/** QA BUG-10 (WCAG 1.3.1 / 4.1.2): kaynak taraması; tüm tablolar adlıdır. */
function vueDosyalari(dizin: string): string[] {
  return readdirSync(dizin).flatMap((ad) => {
    const yol = join(dizin, ad)
    if (statSync(yol).isDirectory()) return vueDosyalari(yol)
    return yol.endsWith('.vue') ? [yol] : []
  })
}
const dosyalar = vueDosyalari(join(process.cwd(), 'src')).map((yol) => ({
  yol: yol
    .slice(process.cwd().length + 1)
    .split(sep)
    .join('/'),
  kaynak: readFileSync(yol, 'utf8'),
}))

describe('tablolar adlıdır (kaynak taraması)', () => {
  it('her <table> aria-label ya da aria-labelledby taşır (ya da <caption> içerir)', () => {
    const adsiz = dosyalar.flatMap((d) =>
      [...d.kaynak.matchAll(/<table\b[^>]*>(?:\s*<caption)?/g)]
        .filter((m) => !/aria-label(?:ledby)?=/.test(m[0]) && !m[0].includes('<caption'))
        .map((m) => `${d.yol}: ${m[0].replace(/\s+/g, ' ')}`),
    )
    expect(adsiz).toEqual([])
  })
})
