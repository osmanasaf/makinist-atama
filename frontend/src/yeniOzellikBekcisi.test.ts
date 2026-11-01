import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * OK-50 (review D-13): yeni özellik dosyaları (F1–F5) için sıfır-tolerans bekçisi. `belirtecler.test.ts` ham rengi yalnız `#hex`/`rgb(` ile sayar; marka taraması yalnız kurum adı arar.
 * Burada ek olarak: `hsl(`/`hwb(`/`lab(` ve adlandırılmış renk, emoji, `v-html`/`innerHTML` ve harici adres (CDN, font, izleyici) bu dosyalarda YOK. Yeni özellik dosyası eklenince KAPSAM'a yazılır.
 */
const ON = join(__dirname)
const KAPSAM_KLASOR = ['features/kurulum', 'features/kurum', 'features/yazdir']
const KAPSAM_DOSYA = [
  'features/rapor/AylikCizelge.vue',
  'features/rapor/OzetGovdesi.vue',
  'features/rapor/YonetimOzeti.vue',
  'features/rapor/cizelgeApi.ts',
  'features/rapor/cizelgeHucre.ts',
  'features/rapor/ozetApi.ts',
  'features/parametre/ProfilSayfasi.vue',
  'features/parametre/profilApi.ts',
  'features/parametre/profilFark.ts',
  'features/makinist/MakinistYukleme.vue',
  'features/tanim/ResmiTatiller.vue',
  'features/atama/YakinlikNotu.vue',
  'features/atama/YorgunlukOzeti.vue',
  'features/atama/yakinlik.ts',
  'composables/kurum.ts',
]

function klasorDosyalari(klasor: string): string[] {
  const sonuc: string[] = []
  for (const ad of readdirSync(klasor)) {
    const yol = join(klasor, ad)
    if (statSync(yol).isDirectory()) sonuc.push(...klasorDosyalari(yol))
    else if (/\.(vue|ts|css)$/.test(ad) && !ad.endsWith('.test.ts')) sonuc.push(yol)
  }
  return sonuc
}

const dosyalar = [
  ...KAPSAM_KLASOR.flatMap((k) => klasorDosyalari(join(ON, k))),
  ...KAPSAM_DOSYA.map((d) => join(ON, d)),
]
const goreli = (yol: string) => relative(ON, yol).split(sep).join('/')

const CSS_ADLI_RENK =
  /(?:^|[\s;{])(?:color|background(?:-color)?|border(?:-[a-z]+)*-color|border(?:-[a-z]+)?|fill|stroke|outline(?:-color)?)\s*:\s*(?:white|black|red|green|blue|yellow|orange|purple|pink|gr[ae]y|silver|gold|navy|teal|maroon|lime|aqua|cyan|magenta|brown)\b/i

/** Kural -> deseni her dosyada arayan tarayıcı; ihlal "dosya: kural" olarak toplanır. */
const KURALLAR: [string, RegExp][] = [
  [
    'ham renk (#hex, rgb, hsl, hwb, lab, lch, oklch)',
    /#[0-9a-fA-F]{3,8}\b|\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch)\(/,
  ],
  ['adlandırılmış CSS rengi', CSS_ADLI_RENK],
  ['emoji', /\p{Extended_Pictographic}/u],
  ['v-html / innerHTML', /v-html|\binnerHTML\b|\bouterHTML\b/],
  ['harici adres (http/https/protokolsüz //)', /https?:\/\/|["'`(]\/\/[a-z0-9.-]+\./i],
]

describe('yeni özellik dosyaları bekçisi (F1–F5, OK-50 D-13)', () => {
  it('kapsam boş değil ve dosyalar okunabilir', () => {
    expect(dosyalar.length).toBeGreaterThan(25)
    expect(dosyalar.filter((d) => readFileSync(d, 'utf8').length === 0).map(goreli)).toEqual([])
  })

  for (const [ad, desen] of KURALLAR) {
    it(`${ad}: sıfır`, () => {
      const ihlal = dosyalar.filter((d) => desen.test(readFileSync(d, 'utf8'))).map(goreli)
      expect(ihlal).toEqual([])
    })
  }

  it('bekçi gerçekten yakalar: kötü örnekler her kurala takılır (duyarlılık)', () => {
    const kotu: Record<string, string> = {
      'ham renk (#hex, rgb, hsl, hwb, lab, lch, oklch)': 'a { color: hsl(10 20% 30%); }',
      'adlandırılmış CSS rengi': 'a { color: red; }',
      emoji: 'Kaydedildi ✅ 🚆',
      'v-html / innerHTML': '<p v-html="x" />',
      'harici adres (http/https/protokolsüz //)': 'import "https://cdn.example/x.js"',
    }
    const yakalanmayan = KURALLAR.filter(([ad, desen]) => !desen.test(kotu[ad]!)).map(([ad]) => ad)
    expect(yakalanmayan).toEqual([])
  })
})
