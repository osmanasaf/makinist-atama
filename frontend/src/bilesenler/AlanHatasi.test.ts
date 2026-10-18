import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, sep } from 'node:path'
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AlanHatasi from './AlanHatasi.vue'

describe('AlanHatasi', () => {
  it('metin varken id + role=alert + alan-hata sınıfıyla çizilir', () => {
    const w = mount(AlanHatasi, { props: { id: 'x-hata', metin: 'Zorunlu alan.' } })
    const p = w.get('p')
    expect(p.attributes('id')).toBe('x-hata')
    expect(p.attributes('role')).toBe('alert')
    expect(p.classes()).toContain('alan-hata')
    expect(p.text()).toBe('Zorunlu alan.')
  })
  it('metin yoksa ya da boşsa hiçbir şey çizilmez', () => {
    expect(
      mount(AlanHatasi, { props: { id: 'x-hata' } })
        .find('p')
        .exists(),
    ).toBe(false)
    expect(
      mount(AlanHatasi, { props: { id: 'x-hata', metin: '' } })
        .find('p')
        .exists(),
    ).toBe(false)
  })
})

/** Kaynak taraması: form hataları tek desenle (AlanHatasi) yazılır ve her biri bir alana `aria-describedby` ile bağlanır. */
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

describe('form hata deseni (kaynak taraması)', () => {
  it('ham class="alan-hata" yalnız role="alert" ile kullanılır (listeli ayar satırları dışında hepsi AlanHatasi)', () => {
    const ihlal = dosyalar
      .filter((d) => d.yol !== 'src/bilesenler/AlanHatasi.vue')
      .flatMap((d) =>
        [...d.kaynak.matchAll(/<p\b[^>]*class="alan-hata[^"]*"[^>]*>/g)]
          .filter((m) => !m[0].includes('role="alert"'))
          .map((m) => `${d.yol}: ${m[0].replace(/\s+/g, ' ')}`),
      )
    expect(ihlal).toEqual([])
  })

  it('her AlanHatasi kimliği, aynı dosyada bir alanın aria-describedby değerinde geçer', () => {
    const ihlal: string[] = []
    let sayac = 0
    for (const d of dosyalar) {
      for (const m of d.kaynak.matchAll(/<AlanHatasi\b[^>]*?\bid="([^"]+)"/g)) {
        sayac++
        const id = m[1]!
        const kacis = id.replace(/[$.{}]/g, (c) => `[${c}]`) // ${k.id} gibi dinamik kimlikler birebir aranır
        const bagli = new RegExp(`aria-describedby="[^"]*${kacis}[^"]*"`).test(d.kaynak)
        if (!bagli) ihlal.push(`${d.yol}: #${id}`)
      }
    }
    expect(sayac).toBeGreaterThan(30) // desen gerçekten yaygın kullanılıyor
    expect(ihlal).toEqual([])
  })
})
