import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * F1 / AK-1.10, OK-44a: ürün, demo ve ekranlar hiçbir gerçek kurumun adını ya da markasını taşımaz; kurum adı ve logo müşterinin kendi verisidir.
 * Taranan: arayüz kaynağı (test dosyaları hariç), demo veri üretici, Excel şablon örnekleri, DEMO.md ve index.html. Yasaklı adlar küçük harfe katlanarak aranır.
 * İSTİSNA listesi AÇIK ve en dardır: kural parametrelerinin KAYNAK etiketi (parametreApi.ts, kuralın dayandığı belgeyi adlandırır; marka kullanımı değildir).
 */

const KOK = join(__dirname, '..', '..') // repo kökü (frontend/src -> frontend -> repo)
const ON = join(KOK, 'frontend', 'src')

const YASAKLI = [
  'tcdd',
  'türkiye cumhuriyet',
  'turkiye cumhuriyet',
  'yüksek hızlı tren',
  'yuksek hizli tren',
  'tülomsaş',
  'tulomsas',
  'tüvasaş',
  'marmaray',
  'başkentray',
  'baskentray',
  'izban',
  'metro istanbul',
]

/** İstisna: repo köküne göre yol (/ ayraçlı) -> bu dosyada izin verilen EN FAZLA yasaklı sözcük geçişi. */
const ISTISNA: Record<string, number> = {
  'frontend/src/features/parametre/parametreApi.ts': 1, // kaynak etiketi: İ -> 'TCDD genel müdürlük emri'
}

function dosyalar(klasor: string, uzantilar: string[]): string[] {
  const sonuc: string[] = []
  for (const ad of readdirSync(klasor)) {
    const yol = join(klasor, ad)
    if (statSync(yol).isDirectory()) {
      if (ad !== 'node_modules') sonuc.push(...dosyalar(yol, uzantilar))
    } else if (uzantilar.some((u) => ad.endsWith(u)) && !ad.endsWith('.test.ts')) {
      sonuc.push(yol)
    }
  }
  return sonuc
}

const goreli = (yol: string) => relative(KOK, yol).split(sep).join('/')

function taranacaklar(): string[] {
  return [
    ...dosyalar(ON, ['.vue', '.ts', '.css', '.html', '.svg']),
    ...dosyalar(join(KOK, 'backend', 'src', 'main', 'java', 'com', 'makinistatama', 'demo'), [
      '.java',
    ]),
    join(
      KOK,
      'backend',
      'src',
      'main',
      'java',
      'com',
      'makinistatama',
      'sefer',
      'SeferExcelOkuyucu.java',
    ),
    join(
      KOK,
      'backend',
      'src',
      'main',
      'java',
      'com',
      'makinistatama',
      'makinist',
      'MakinistExcelOkuyucu.java',
    ),
    join(KOK, 'DEMO.md'),
    join(KOK, 'frontend', 'index.html'),
  ]
}

function gecisler(icerik: string): string[] {
  const k = icerik.toLocaleLowerCase('tr-TR')
  const bulunan: string[] = []
  for (const y of YASAKLI) {
    let i = k.indexOf(y)
    while (i >= 0) {
      bulunan.push(y)
      i = k.indexOf(y, i + y.length)
    }
  }
  return bulunan
}

describe('marka taraması (AK-1.10, OK-44a)', () => {
  it('tarama gerçekten dosya buluyor (boş tarama yanıltmaz)', () => {
    const t = taranacaklar().map(goreli)
    expect(t.length).toBeGreaterThan(100)
    expect(t).toContain('DEMO.md')
    expect(t).toContain(
      'backend/src/main/java/com/makinistatama/makinist/MakinistExcelOkuyucu.java',
    )
    expect(t.some((x) => x.endsWith('/DemoVeriServisi.java'))).toBe(true)
    expect(t.some((x) => x.endsWith('.test.ts'))).toBe(false)
  })

  it('yasaklı kurum adı kaynakta, demoda, şablonda ve DEMO.md içinde geçmez (istisna listesi dışında)', () => {
    const ihlal: string[] = []
    for (const yol of taranacaklar()) {
      const g = gecisler(readFileSync(yol, 'utf8'))
      const izin = ISTISNA[goreli(yol)] ?? 0
      if (g.length > izin)
        ihlal.push(
          `${goreli(yol)}: ${[...new Set(g)].join(', ')} (${g.length} geçiş, izin ${izin})`,
        )
    }
    expect(ihlal).toEqual([])
  })

  it('istisna listesi gerçek: her istisna dosyası var ve izin verilen geçişi gerçekten içeriyor (bayat istisna kalmaz)', () => {
    for (const [dosya, adet] of Object.entries(ISTISNA)) {
      expect(gecisler(readFileSync(join(KOK, dosya), 'utf8'))).toHaveLength(adet)
    }
  })

  it('gerçek bir kurum logosu repoda gönderilmez: public/ yalnız favicon içerir, src altında görsel dosya yok', () => {
    expect(readdirSync(join(KOK, 'frontend', 'public')).sort()).toEqual(['favicon.ico'])
    const gorseller = dosyalar(ON, ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg']).map(goreli)
    expect(gorseller).toEqual([])
  })

  it('tarayıcı sekme başlığı ve ürün adı kurum adı değil, ürün adıdır', () => {
    expect(readFileSync(join(KOK, 'frontend', 'index.html'), 'utf8')).toContain(
      '<title>Makinist Atama</title>',
    )
  })

  it('tarayıcının kendisi çalışıyor: yasaklı sözcük bulucu büyük/küçük harf ve Türkçe harfi katlar', () => {
    expect(gecisler("Örnek TCDD'den")).toEqual(['tcdd'])
    expect(gecisler('TÜRKİYE CUMHURİYET devlet')).toEqual(['türkiye cumhuriyet'])
    expect(gecisler('Örnek İşletme (demo)')).toEqual([])
  })
})
