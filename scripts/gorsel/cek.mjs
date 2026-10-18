// Görsel kontrol betiği (MK-142): çalışan uygulamanın ekran görüntülerini ve ölçümlerini alır. Bağımlılıksız (Node >= 22 + Chrome).
//
// Ortam (proje dışı, geçici; betikte parola YOK):
//   1. Geçici PostgreSQL:  docker run -d --name makinist-tasarim-gecici-db -e POSTGRES_PASSWORD=<geçici> -e POSTGRES_DB=gecici -p 127.0.0.1:5436:5432 postgres:17
//      (gerçek `makinist` DB'si 5434, ihale-takip 5433: dokunulmaz; iş bitince konteyneri `docker rm -f` ile sil)
//   2. Backend (dev profili, 8082; ortam değişkenleri .env'i ezer):
//      DB_PORT=5436 DB_ADI=gecici DB_KULLANICI=postgres DB_PAROLA=<geçici> APP_PORT=8082 MAKINIST_ADMIN_EPOSTA=<e-posta> MAKINIST_ADMIN_SIFRE=<parola>
//      backend\mvnw.cmd -f backend\pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
//   3. Vite (5175, /api -> 8082): frontend/ içinde, repo dışı yapılandırmayla (örn. vite.config kopyası, server.port=5175, proxy '/api': 'http://localhost:8082')
//   4. Demo veri: yönetici ile Ana sayfa > "Demo veri yükle" (yalnız dev profili)
//
// Ortam değişkenleri: GORSEL_KULLANICI / GORSEL_PAROLA (yönetici veya atamacı), GORSEL_MAKINIST_KULLANICI / GORSEL_MAKINIST_PAROLA,
//   GORSEL_TABAN (varsayılan http://localhost:5175), CHROME_YOLU (varsayılan: bilinen Chrome/Edge yolları).
//
// Kullanım:
//   node scripts/gorsel/cek.mjs --sayfa ana [--sayfa atama-masasi ...] | --hepsi
//        [--genislik 1366,1920] [--yukseklik <px>]  (varsayılan yükseklik: 1366→768, 1920→1080, 375→812, 320→568) [--cikti .team/<iş>/sonra] [--onek atamaci-]
//        [--rol yonetici|makinist] [--tam] [--olcek] [--yazdir] [--bekle 1200]
//   --tam: ayrıca tüm sayfa yüksekliği (-tam.png) · --olcek: %200 yakınlaştırma (683 px, cihaz ölçeği 2) · --yazdir: print medya emülasyonu
//   Çıktı: <cikti>/<onek><ad>-<genislik>[-tam|-olcek|-yazdir].png ve <cikti>/olcum.json (yatay taşma, görünür sefer, küçük hedef, konsol hatası).
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { baslat } from './cdp.mjs'
import { bekle, giris, git } from './ortak.mjs'

// ---- sayfa listesi: ad, yol (hash yönlendirici), rol grubu; acik = oturumsuz; eylem = çekimden önce sayfada çalışan ifade
const bugun = new Date().toLocaleDateString('sv-SE') // YYYY-MM-DD (yerel)
const SAYFALAR = [
  { ad: 'giris', yol: '/giris', grup: 'acik' },
  { ad: 'ana', yol: '/', grup: 'masaustu' },
  { ad: 'atama-masasi', yol: '/atama', grup: 'masaustu' },
  {
    ad: 'atama-masasi-secili',
    yol: '/atama',
    grup: 'masaustu',
    eylem: "document.querySelector('tr.sefer-satiri')?.click()",
  },
  { ad: 'odak-atla', yol: '/', grup: 'masaustu', tab: 1 }, // klavye: "İçeriğe geç"
  { ad: 'odak-cikis', yol: '/', grup: 'masaustu', tab: 2 },
  { ad: 'odak-menu', yol: '/', grup: 'masaustu', tab: 5 },
  { ad: 'personel-liste', yol: '/makinistler', grup: 'masaustu' },
  { ad: 'personel-yeni', yol: '/makinistler/yeni', grup: 'masaustu' },
  { ad: 'seferler', yol: '/seferler', grup: 'masaustu' },
  { ad: 'sefer-yukle', yol: '/seferler/yukle', grup: 'masaustu' },
  { ad: 'takas-talepleri', yol: '/takas', grup: 'masaustu' },
  { ad: 'belgeler', yol: '/belgeler', grup: 'masaustu' },
  { ad: 'tanimlar', yol: '/tanimlar', grup: 'masaustu' },
  { ad: 'ayarlar', yol: '/ayarlar', grup: 'masaustu' },
  { ad: 'adalet-raporu', yol: '/rapor/adalet', grup: 'masaustu' },
  { ad: 'yedek-plani', yol: '/rapor/yedek', grup: 'masaustu' },
  { ad: 'ozel-kurallar', yol: '/ozel-kurallar', grup: 'masaustu' },
  { ad: 'izinler', yol: '/izin', grup: 'masaustu' },
  { ad: 'konaklama', yol: '/konaklama', grup: 'masaustu' },
  { ad: 'konaklama-raporu', yol: '/konaklama/rapor', grup: 'masaustu' },
  { ad: 'puantaj', yol: '/rapor/puantaj', grup: 'masaustu' },
  { ad: 'kadro-ihtiyaci', yol: '/rapor/kadro', grup: 'masaustu' },
  { ad: 'denetim-raporu', yol: '/degisiklik', grup: 'masaustu' },
  { ad: 'yazdir-gun', yol: `/yazdir/gun/${bugun}`, grup: 'masaustu' },
  { ad: 'mobil-gorevler', yol: '/benim/gorevler', grup: 'makinist' },
  { ad: 'mobil-belgeler', yol: '/benim/belgeler', grup: 'makinist' },
  { ad: 'mobil-izin', yol: '/benim/izin', grup: 'makinist' },
  { ad: 'mobil-ozet', yol: '/benim/ozet', grup: 'makinist' },
  { ad: 'mobil-takas', yol: '/benim/takas', grup: 'makinist' },
  { ad: 'mobil-konaklama', yol: '/benim/konaklama', grup: 'makinist' },
]

// ---- bağımsız değişkenler
function oku(argv) {
  const a = { sayfa: [], bayrak: new Set(), deger: {} }
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i]
    if (!k.startsWith('--')) throw new Error(`Beklenmeyen bağımsız değişken: ${k}`)
    const ad = k.slice(2)
    if (['hepsi', 'tam', 'olcek', 'yazdir'].includes(ad)) a.bayrak.add(ad)
    else if (ad === 'sayfa') a.sayfa.push(argv[++i])
    else a.deger[ad] = argv[++i]
  }
  return a
}

// ---- sayfada çalışan ölçüm: yatay taşma, görünür sefer satırı, küçük etkileşim hedefi, h1 sayısı
const OLCUM = `(() => {
  const d = document.documentElement
  const gorunur = (e) => {
    const r = e.getBoundingClientRect()
    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && getComputedStyle(e).visibility !== 'hidden' && !e.closest('.gorunmez')
  }
  const esik = innerWidth < 500 ? 44 : 24
  const kucuk = [...document.querySelectorAll('a[href],button,input:not([type=hidden]),select,textarea,[role=tab]')]
    .filter(gorunur)
    .filter((e) => !(e.matches('input[type=checkbox],input[type=radio]') && e.closest('label')))
    .filter((e) => {
      const r = e.getBoundingClientRect()
      return r.width < esik || r.height < esik
    })
    .map((e) => e.tagName.toLowerCase() + (e.className ? '.' + String(e.className).trim().split(/\\s+/)[0] : ''))
  const satirlar = [...document.querySelectorAll('tr.sefer-satiri')]
  const gorunenSefer = satirlar.filter((s) => {
    const r = s.getBoundingClientRect()
    return r.top >= 0 && r.bottom <= innerHeight
  }).length
  const ae = document.activeElement
  const odak = ae && ae !== document.body ? { oge: ae.tagName.toLowerCase() + (ae.className ? '.' + String(ae.className).trim().split(/\s+/)[0] : ''), gorunur: ae.matches(':focus-visible'), cerceve: getComputedStyle(ae).outlineStyle + ' ' + getComputedStyle(ae).outlineWidth } : null
  return {
    odak,
    yol: location.hash,
    baslik: document.title,
    yatayTasma: d.scrollWidth > d.clientWidth,
    scrollWidth: d.scrollWidth,
    clientWidth: d.clientWidth,
    h1: document.querySelectorAll('h1').length,
    seferToplam: satirlar.length,
    gorunenSefer,
    kucukHedefSayisi: kucuk.length,
    kucukHedefOrnek: [...new Set(kucuk)].slice(0, 6),
  }
})()`

async function main() {
  const a = oku(process.argv)
  const taban = (a.deger.taban ?? process.env.GORSEL_TABAN ?? 'http://localhost:5175').replace(/\/$/, '')
  const rol = a.deger.rol ?? 'yonetici'
  const cikti = a.deger.cikti ?? '.team/sonra'
  const onek = a.deger.onek ?? ''
  const bekleMs = Number(a.deger.bekle ?? 1200)
  const olcek = a.bayrak.has('olcek')
  const genislikler = olcek
    ? [683]
    : (a.deger.genislik ?? (rol === 'makinist' ? '375' : '1366')).split(',').map(Number)
  // genişliğe göre tipik ekran yüksekliği (--yukseklik ile ezilir)
  const yukseklikOf = (g) => Number(a.deger.yukseklik ?? { 1920: 1080, 375: 812, 320: 568 }[g] ?? (rol === 'makinist' ? 812 : 768))

  const gruplar = rol === 'makinist' ? ['makinist'] : ['acik', 'masaustu']
  const secili = a.bayrak.has('hepsi')
    ? SAYFALAR.filter((s) => gruplar.includes(s.grup))
    : a.sayfa.map((ad) => SAYFALAR.find((s) => s.ad === ad) ?? (() => { throw new Error(`Bilinmeyen sayfa: ${ad}`) })())
  if (secili.length === 0) throw new Error('Sayfa seçilmedi: --sayfa <ad> ya da --hepsi')

  const ortamAdi = rol === 'makinist' ? ['GORSEL_MAKINIST_KULLANICI', 'GORSEL_MAKINIST_PAROLA'] : ['GORSEL_KULLANICI', 'GORSEL_PAROLA']
  const [kullanici, parola] = ortamAdi.map((k) => process.env[k])
  if (secili.some((s) => s.grup !== 'acik') && (!kullanici || !parola)) {
    throw new Error(`Oturum gerekli: ${ortamAdi.join(' ve ')} ortam değişkenlerini ayarlayın.`)
  }

  mkdirSync(cikti, { recursive: true })
  const { cdp, kapat } = await baslat()
  const konsol = []
  cdp.dinle((yontem, p) => {
    if (yontem === 'Runtime.exceptionThrown') konsol.push(`istisna: ${p.exceptionDetails.exception?.description ?? p.exceptionDetails.text}`)
    else if (yontem === 'Runtime.consoleAPICalled' && p.type === 'error') konsol.push(`konsol: ${p.args.map((x) => x.value ?? x.description).join(' ')}`)
    else if (yontem === 'Log.entryAdded' && p.entry.level === 'error' && !p.entry.url?.endsWith('/api/auth/ben')) konsol.push(`günlük: ${p.entry.text} ${p.entry.url ?? ''}`)
  })

  const sonuc = []
  try {
    if (a.bayrak.has('yazdir')) await cdp.gonder('Emulation.setEmulatedMedia', { media: 'print' })
    const sirali = [...secili.filter((s) => s.grup === 'acik'), ...secili.filter((s) => s.grup !== 'acik')]
    let girisYapildi = false
    for (const s of sirali) {
      if (s.grup !== 'acik' && !girisYapildi) {
        await cdp.gorunum(genislikler[0], yukseklikOf(genislikler[0]), olcek ? 2 : 1)
        await giris(cdp, taban, kullanici, parola)
        girisYapildi = true
      }
      for (const g of genislikler) {
        await cdp.gorunum(g, yukseklikOf(g), olcek ? 2 : 1)
        konsol.length = 0
        await git(cdp, taban, s.yol, bekleMs)
        for (let i = 0; i < (s.tab ?? 0); i++) await cdp.tus('Tab', 'Tab', 9)
        if (s.eylem) {
          await cdp.degerlendir(s.eylem)
          await bekle(400)
        }
        const olcum = await cdp.degerlendir(OLCUM)
        const sonek = a.bayrak.has('yazdir') ? '-yazdir' : olcek ? '-olcek' : ''
        const dosya = join(cikti, `${onek}${s.ad}-${g}${sonek}.png`)
        writeFileSync(dosya, Buffer.from(await cdp.ekranGoruntusu(false), 'base64'))
        if (a.bayrak.has('tam')) {
          writeFileSync(join(cikti, `${onek}${s.ad}-${g}${sonek}-tam.png`), Buffer.from(await cdp.ekranGoruntusu(true), 'base64'))
        }
        sonuc.push({ ad: s.ad, genislik: g, ...olcum, konsolHatalari: [...konsol] })
        console.log(JSON.stringify({ ad: s.ad, genislik: g, yatayTasma: olcum.yatayTasma, h1: olcum.h1, gorunenSefer: olcum.gorunenSefer, kucukHedef: olcum.kucukHedefSayisi, konsolHatasi: konsol.length }))
      }
    }
  } finally {
    await kapat()
  }
  writeFileSync(join(cikti, `${onek}olcum.json`), JSON.stringify(sonuc, null, 2))
  const sorunlu = sonuc.filter((s) => s.yatayTasma || s.konsolHatalari.length > 0)
  if (sorunlu.length > 0) {
    console.error(`UYARI: ${sorunlu.length} görüntüde yatay taşma ya da konsol hatası var (olcum.json).`)
    process.exitCode = 1
  }
}

main().catch((e) => {
  console.error(e.message)
  process.exit(2)
})
