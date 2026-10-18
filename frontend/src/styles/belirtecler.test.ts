import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

// vitest kökü frontend/'dir (vitest.config.ts); jsdom ortamında import.meta.url dosya adresi değildir
const kaynakDizini = join(process.cwd(), 'src')
const stilDizini = join(kaynakDizini, 'styles')
const css = readFileSync(join(stilDizini, 'belirtecler.css'), 'utf8')

/** `:root` bloğundaki özel özellikler (var() başvuruları çözülür). */
function belirtecler(): Map<string, string> {
  const kok = /:root\s*\{([\s\S]*?)\n\}/.exec(css)?.[1] ?? ''
  const ham = new Map<string, string>()
  for (const [, ad, deger] of kok.matchAll(/(--[a-z0-9-]+):\s*([^;]+);/g)) {
    ham.set(ad!, deger!.replace(/\s+/g, ' ').trim())
  }
  return ham
}
const B = belirtecler()

function coz(ad: string): string {
  let d = B.get(ad)
  if (d === undefined) throw new Error(`belirteç yok: ${ad}`)
  for (let i = 0; i < 5 && d.startsWith('var('); i++) d = coz(d.slice(4, -1).trim())
  return d
}

function parlaklik(hex: string): number {
  const k = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
  const [r, g, b] = k.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)) as [
    number,
    number,
    number,
  ]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
function oran(on: string, arka: string): number {
  const [a, b] = [parlaklik(coz(on)), parlaklik(coz(arka))]
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

// [çift, ön, arka, gereken oran (metin 4,5; UI 3), 03-mimari.md'deki hesaplanmış oran]
type Cift = [string, string, string, number, number | null]
const n0 = '--renk-n-0'
const n50 = '--renk-n-50'
const n100 = '--renk-n-100'
const n400 = '--renk-n-400'
const n500 = '--renk-n-500'
const n700 = '--renk-n-700'
const n900 = '--renk-n-900'
const p900 = '--renk-p-900'
const p800 = '--renk-p-800'
const p700 = '--renk-p-700'
const p600 = '--renk-p-600'
const p50 = '--renk-p-50'
const CIFTLER: Cift[] = [
  ['metin n-900 / n-0', n900, n0, 4.5, 17.26],
  ['metin n-900 / n-50 (sayfa)', n900, n50, 4.5, 16.1],
  ['metin n-900 / n-100', n900, n100, 4.5, 14.97],
  ['tablo başlığı n-700 / n-100', n700, n100, 4.5, 8.95],
  ['soluk n-500 / n-0', n500, n0, 4.5, 5.81],
  ['soluk n-500 / n-50', n500, n50, 4.5, 5.42],
  ['soluk n-500 / n-100', n500, n100, 4.5, 5.03],
  ['soluk n-500 / uyarı zemini', n500, '--renk-uyari-zemin', 4.5, 5.3],
  ['soluk n-500 / engel zemini', n500, '--renk-engel-zemin', 4.5, 4.94],
  ['soluk n-500 / bilgi zemini', n500, p50, 4.5, 5.05],
  ['kontrol kenarlığı n-400 / n-0', n400, n0, 3, 3.64],
  ['kontrol kenarlığı n-400 / n-50', n400, n50, 3, 3.4],
  ['kontrol kenarlığı n-400 / n-100', n400, n100, 3, 3.16],
  ['birincil düğme beyaz / p-600', n0, p600, 4.5, 6.09],
  ['birincil düğme hover beyaz / p-700', n0, p700, 4.5, 8.55],
  ['bağlantı p-600 / n-0', p600, n0, 4.5, 6.09],
  ['bağlantı p-600 / n-50', p600, n50, 4.5, 5.68],
  ['bağlantı p-700 / p-50', p700, p50, 4.5, 7.43],
  ['ikincil düğme metni p-700 / n-0', p700, n0, 4.5, 8.55],
  ['ikincil düğme kenarlığı p-600 / n-0', p600, n0, 3, 6.09],
  ['yan menü metni / p-900', '--renk-menu-metin', p900, 4.5, 10.27],
  ['yan menü grup başlığı / p-900', '--renk-menu-baslik', p900, 4.5, 7.14],
  ['yan menü aktif beyaz / p-700', n0, p700, 4.5, 8.55],
  ['yan menü hover beyaz / p-800', n0, p800, 4.5, 11.34],
  ['yan menü metni / p-800', '--renk-menu-metin', p800, 4.5, 7.7],
  ['odak halkası / n-0', '--renk-odak', n0, 3, 6.39],
  ['odak halkası / n-50', '--renk-odak', n50, 3, 5.96],
  ['odak halkası / engel zemini', '--renk-odak', '--renk-engel-zemin', 3, 5.43],
  ['odak halkası / p-50', '--renk-odak', p50, 3, 5.55],
  ['odak halkası koyu zeminde / p-900', '--renk-odak-koyu', p900, 3, 7.24],
  ['başarı metin / başarı zemini', '--renk-basari-metin', '--renk-basari-zemin', 4.5, 5.51],
  ['başarı metin / n-0', '--renk-basari-metin', n0, 4.5, 6.33],
  ['başarı dolu beyaz / başarı metin', n0, '--renk-basari-metin', 4.5, 6.33],
  ['başarı çizgi / n-0 (UI)', '--renk-basari-metin', n0, 3, 6.33],
  ['uyarı metin / uyarı zemini', '--renk-uyari-metin', '--renk-uyari-zemin', 4.5, 6.51],
  ['uyarı metin / n-0', '--renk-uyari-metin', n0, 4.5, 7.13],
  ['uyarı çizgi / uyarı zemini', '--renk-uyari-cizgi', '--renk-uyari-zemin', 3, 3.32],
  ['uyarı çizgi / n-0', '--renk-uyari-cizgi', n0, 3, 3.63],
  ['uyarı dolu n-900 / uyarı dolgu', n900, '--renk-uyari-dolu', 4.5, 9.49],
  ['engel metin / engel zemini', '--renk-engel-metin', '--renk-engel-zemin', 4.5, 6.43],
  ['engel metin / n-0', '--renk-engel-metin', n0, 4.5, 7.56],
  ['engel dolu beyaz / engel dolu', n0, '--renk-engel-dolu', 4.5, 6.54],
  ['engel şerit / engel zemini (UI)', '--renk-engel-dolu', '--renk-engel-zemin', 3, 5.56],
  ['engel satır metni n-900 / engel zemini', n900, '--renk-engel-zemin', 4.5, 14.68],
  ['bilgi metin / bilgi zemini', '--renk-bilgi-metin', '--renk-bilgi-zemin', 4.5, 7.43],
  ['seçili halka p-600 / engel zemini', p600, '--renk-engel-zemin', 3, 5.18],
  ['seçili halka p-600 / p-50', p600, p50, 3, 5.29],
  ['gece / gece zemini', '--renk-gece-metin', '--renk-gece-zemin', 4.5, 8.17],
  ['gece / n-0', '--renk-gece-metin', n0, 4.5, 9.62],
  ['ASIL metin / zemin', '--renk-rol-asil-metin', '--renk-rol-asil-zemin', 4.5, 7.43],
  ['YARDIMCI metin / zemin', '--renk-rol-yardimci-metin', '--renk-rol-yardimci-zemin', 4.5, 6.44],
  [
    'KONDUKTOR metin / zemin',
    '--renk-rol-konduktor-metin',
    '--renk-rol-konduktor-zemin',
    4.5,
    7.76,
  ],
  ['YOLCU metin / zemin', '--renk-rol-yolcu-metin', '--renk-rol-yolcu-zemin', 4.5, 6.6],
  ['ASIL kenarlık / n-0', '--renk-rol-asil-metin', n0, 3, 8.55],
  ['YARDIMCI kenarlık / n-0', '--renk-rol-yardimci-metin', n0, 3, 7.46],
  ['KONDUKTOR kenarlık / n-0', '--renk-rol-konduktor-metin', n0, 3, 9.18],
  ['YOLCU kenarlık / n-0', '--renk-rol-yolcu-metin', n0, 3, 7.53],
  ['engelli aday metni n-500 / n-0', n500, n0, 4.5, 5.81],
  ['engelli aday adı n-700 / n-0', n700, n0, 4.5, 10.32],
  ['devre dışı düğme n-500 / n-100', n500, n100, 4.5, 5.03],
  ['yer tutucu n-500 / n-0', n500, n0, 4.5, 5.81],
  ['grafik eğri p-600 / n-0', p600, n0, 3, 6.09],
  ['grafik hedef çizgisi / n-0', '--renk-uyari-cizgi', n0, 3, 3.63],
  [
    'grafik ızgara n-300 / n-0 (süs, muaf: yalnız değer kayması denetlenir)',
    '--renk-n-300',
    n0,
    1,
    1.65,
  ],
  ['grafik eksen metni n-500 / n-0', n500, n0, 4.5, 5.81],
  // temel.css'in kullandığı, tabloda olmayan çiftler (kural: önce teste girer)
  ['üst çubuk marka/Çıkış beyaz / p-900', n0, p900, 4.5, null],
  ['hata metni engel metin / n-50', '--renk-engel-metin', n50, 4.5, null],
  ['soluk düğme tehlike hover beyaz / tehlike koyu', n0, '--renk-tehlike-koyu', 4.5, null],
]

describe('belirteçler: kontrast (WCAG 2.1 AA)', () => {
  it.each(CIFTLER)('%s', (_ad, on, arka, gereken, beklenen) => {
    const r = oran(on, arka)
    expect(r).toBeGreaterThanOrEqual(gereken)
    // tablodaki hesapla uyum: belirteç değeri sessizce kaymasın (ek çiftlerde yalnız alt sınır)
    expect(beklenen === null ? 0 : Math.abs(r - beklenen)).toBeLessThan(0.02)
  })

  it('mimari tablosundaki çift sayısı korunur (65 çift + temel.css ekleri)', () => {
    expect(CIFTLER.length).toBeGreaterThanOrEqual(65)
  })
})

describe('belirteçler: yapı', () => {
  it('kalan eski adların takma adları çözülür (H4: --renk-soluk, --bosluk, --yazi-boyutu kaldırıldı)', () => {
    for (const ad of [
      '--renk-zemin',
      '--renk-yuzey',
      '--renk-metin',
      '--renk-cizgi',
      '--renk-birincil',
      '--renk-birincil-koyu',
      '--renk-birincil-yazi',
      '--renk-tehlike',
      '--renk-uyari-zemin',
      '--renk-uyari-cizgi',
      '--renk-hata-zemin',
      '--renk-bilgi-zemin',
      '--renk-odak',
      '--yaricap',
    ]) {
      expect(B.has(ad) ? '' : ad).toBe('')
    }
  })

  it('yoğunluk öznitelikleri 4 belirteç tanımlar', () => {
    for (const y of ['yogun', 'dokunma']) {
      const blok = new RegExp(`\\[data-yogunluk='${y}'\\]\\s*\\{([^}]*)\\}`).exec(css)?.[1] ?? ''
      for (const ad of ['--hucre-dikey', '--kontrol-yukseklik', '--tablo-yazi', '--satir-min']) {
        expect(blok.includes(ad) ? '' : `${y} ${ad}`).toBe('')
      }
    }
  })
})

describe('belirteçler: ham renk sızıntı bütçesi (MK-147)', () => {
  /** A sonu 19 (başlangıç 29), C+D sonu 15, E+F sonu 0: yalnız belirtecler.css ve yazdir.css ham renk içerir. */
  const BUTCE = 0

  function hamRenkSayisi(): { toplam: number; dosyalar: Record<string, number> } {
    const dosyalar: Record<string, number> = {}
    let toplam = 0
    for (const g of readdirSync(kaynakDizini, { recursive: true }) as string[]) {
      const ad = g.replaceAll('\\', '/')
      if (!/\.(vue|css)$/.test(ad) || /(^|\/)(belirtecler|yazdir)\.css$/.test(ad)) continue
      const n = (
        readFileSync(join(kaynakDizini, g), 'utf8').match(/#[0-9a-fA-F]{3,8}\b|rgba?\(/g) ?? []
      ).length
      if (n > 0) {
        dosyalar[ad] = n
        toplam += n
      }
    }
    return { toplam, dosyalar }
  }

  it(`belirtecler.css ve yazdir.css dışında ham renk sayısı ${BUTCE}'i aşmaz`, () => {
    const { toplam, dosyalar } = hamRenkSayisi()
    expect(toplam <= BUTCE ? 'ok' : JSON.stringify(dosyalar)).toBe('ok')
  })

  it('temel.css ve bilesenler/ ham renk içermez', () => {
    const { dosyalar } = hamRenkSayisi()
    expect(
      Object.keys(dosyalar).filter((d) => d === 'styles/temel.css' || d.startsWith('bilesenler/')),
    ).toEqual([])
  })
})
