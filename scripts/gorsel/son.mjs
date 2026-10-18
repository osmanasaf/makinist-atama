// "Önce / sonra" tam görüntü seti (MK-142): `once/` klasöründeki 102 görüntünün aynı adla karşılığını + ek taramaları üretir ve ölçer.
// Bağımlılıksız (Node >= 22 + Chrome). cek.mjs / cd.mjs / ef.mjs mantığı bu betikte birleşiktir; cdp.mjs yardımcıları kullanılır (Fetch müdahalesi: cdp.ara).
//
// Ortam (cek.mjs ile aynı; betikte parola YOK):
//   GORSEL_KULLANICI / GORSEL_PAROLA                      yönetici
//   GORSEL_ATAMACI_KULLANICI / GORSEL_ATAMACI_PAROLA      atamacı (isteğe bağlı: atamaci-* görüntüleri)
//   GORSEL_MAKINIST_KULLANICI / GORSEL_MAKINIST_PAROLA    makinist (parola değiştirme bayrağı KAPALI hesap)
//   GORSEL_MAKINIST_ZORUNLU_KULLANICI / _PAROLA           makinist (geçici parola bayrağı AÇIK hesap; mobil-parola-zorunlu için)
//   GORSEL_TABAN (varsayılan http://localhost:5175), CHROME_YOLU
//
// Kullanım:
//   node scripts/gorsel/son.mjs --cikti <klasör> [--yalniz <ad-parçası>[,<ad-parçası>...]] [--once <klasör>]
//   --yalniz: yalnız adında bu parçalardan biri geçen görüntüleri üretir (ör. --yalniz kadro,mobil-gorevler)
//   --once:   karşılaştırılacak "önce" klasörü (varsayılan .team/2026-10-17-onyuz-tasarim/once); eksik adlar sonda listelenir
//
// Üretilenler (hepsi <cikti>/<ad>.png; uzun sayfalar için ayrıca <ad>-tam.png):
//   <ekran>-<genişlik>          önce/ ile aynı adlar (normal, -bos, -yukleniyor, hata, diyalog, masa durumları, mobil-*)
//   mobil-<ad>-375|320          makinist Benim sayfaları (+ 375'te -tam)
//   olcek-<ad>-683|320          %200 yakınlaştırma (cihaz ölçeği 2; masaüstü ekranları 683, makinist sayfaları 683 ve 320)
//   yazdir-<ad>-1366-yazdir     print medya emülasyonu (yazdırma rotaları + yazdırılan sayfalar)
//   odak-<ad>                   gerçek Tab tuşuyla klavye odağı (atla, menü, masa satırı, diyalog, form alanı, makinist sekme)
//   son-olcum.json              her çekim için taşma, h1, konsol, kontrast, dokunma hedefi + özet
// Durumlar: -bos = /api yanıtı boşaltılır (Fetch), -yukleniyor = istek tutulur (yakalanınca sürdürülür), sunucu yok = istek düşürülür.
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { baslat } from './cdp.mjs'
import { bekle, dugme, dugmeIcerir, git as ortakGit, girisDoldur, gunOtele, oku, tikla as ortakTikla } from './ortak.mjs'
const BUGUN = gunOtele(0)
const AY = BUGUN.slice(0, 7)
const YARIN = gunOtele(1) // masa/yazdırma: yarının seferleri atanmış (bugünün geçmiş seferleri atanmaz)
const MASA = `/atama?tarih=${YARIN}`

// ---- bağımsız değişkenler
// ---- sayfada çalışan ölçüm (işlev metni sayfaya gönderilir; dış değişken KULLANMAZ)
function olcumIslevi() {
  const d = document.documentElement
  const gorunur = (e) => {
    const r = e.getBoundingClientRect()
    // belge koordinatında: kaydırma konumundan bağımsız (sayfanın solu/üstü dışındaki gizli bağlantılar sayılmaz)
    if (r.width <= 0 || r.height <= 0 || r.bottom + scrollY <= 0 || r.right + scrollX <= 0) return false
    const s = getComputedStyle(e)
    return s.visibility !== 'hidden' && s.display !== 'none' && !e.closest('.gorunmez')
  }
  const tanim = (e) => {
    const sinif = (x) => (x && x.className && typeof x.className === 'string' ? '.' + x.className.trim().split(/\s+/)[0] : '')
    return e.tagName.toLowerCase() + sinif(e) + (e.parentElement ? ' < ' + e.parentElement.tagName.toLowerCase() + sinif(e.parentElement) : '')
  }
  // dokunma hedefi: 375/320 -> 44 px, masaüstü -> 24 px (satır içi bağlantılar ve etiketli onay kutusu/radyo muaf)
  const esik = innerWidth < 500 ? 44 : 24
  const kucuk = [...document.querySelectorAll('a[href],button,input:not([type=hidden]),select,textarea,summary,[role=tab],[role=button]')]
    .filter(gorunur)
    .filter((e) => !(e.matches('input[type=checkbox],input[type=radio]') && e.closest('label')))
    .filter((e) => !(e.tagName === 'A' && getComputedStyle(e).display === 'inline'))
    .map((e) => ({ e, r: e.getBoundingClientRect() }))
    .filter(({ r }) => r.width < esik || r.height < esik)
    .map(({ e, r }) => ({ oge: tanim(e), metin: (e.getAttribute('aria-label') || e.textContent || '').trim().slice(0, 30), genislik: Math.round(r.width), yukseklik: Math.round(r.height) }))
  // kontrast: tüm görünür metin düğümleri (devre dışı denetimler muaf; arka plan görseli/gradyanı olan zincir "belirsiz" sayılır)
  const rgb = (c) => {
    const m = c.match(/rgba?\(([^)]+)\)/)
    if (!m) return null
    const p = m[1].split(/[ ,/]+/).filter(Boolean).map(Number)
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 }
  }
  const lum = ({ r, g, b }) => {
    const f = (v) => ((v /= 255) <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4))
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const kar = (u, a) => ({ r: u.r * u.a + a.r * (1 - u.a), g: u.g * u.a + a.g * (1 - u.a), b: u.b * u.a + a.b * (1 - u.a), a: 1 })
  const onbellek = new Map()
  const zemin = (el) => {
    if (!el) return { r: 255, g: 255, b: 255, a: 1, belirsiz: false }
    if (onbellek.has(el)) return onbellek.get(el)
    const s = getComputedStyle(el)
    const alt = zemin(el.parentElement)
    const c = rgb(s.backgroundColor)
    let sonuc = alt
    if (c && c.a > 0) sonuc = { ...kar(c, alt), belirsiz: alt.belirsiz }
    if (s.backgroundImage !== 'none') sonuc = { ...sonuc, belirsiz: true }
    onbellek.set(el, sonuc)
    return sonuc
  }
  const opaklik = (el) => {
    let o = 1
    for (let e = el; e; e = e.parentElement) o *= parseFloat(getComputedStyle(e).opacity)
    return o
  }
  const olculen = []
  let belirsiz = 0
  const yurur = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)
  const bakilan = new Set()
  for (let n = yurur.nextNode(); n; n = yurur.nextNode()) {
    const el = n.parentElement
    if (!el || bakilan.has(el) || !(n.textContent || '').trim()) continue
    bakilan.add(el)
    if (el.closest('script,style,noscript,option,svg') || el.closest(':disabled') || !gorunur(el)) continue
    const bg = zemin(el)
    if (bg.belirsiz) {
      belirsiz++
      continue
    }
    const on = rgb(getComputedStyle(el).color)
    if (!on) continue
    on.a *= opaklik(el)
    const f = kar(on, bg)
    const a = lum(f)
    const b = lum(bg)
    const st = getComputedStyle(el)
    const buyuk = parseFloat(st.fontSize) >= 24 || (parseFloat(st.fontSize) >= 18.66 && parseInt(st.fontWeight, 10) >= 700)
    olculen.push({ oran: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05), esik: buyuk ? 3 : 4.5, secici: tanim(el), metin: n.textContent.trim().slice(0, 36) })
  }
  const dusuk = olculen.filter((x) => x.oran < x.esik).sort((x, y) => x.oran - y.oran)
  const yuvarla = (x) => Math.round(x * 100) / 100
  const enDusuk = olculen.length ? olculen.reduce((m, x) => (x.oran < m.oran ? x : m)) : null
  const gorunenH1 = [...document.querySelectorAll('h1')].filter(gorunur).length
  return {
    yol: location.hash,
    baslik: document.title,
    yatayTasma: d.scrollWidth > d.clientWidth,
    scrollWidth: d.scrollWidth,
    clientWidth: d.clientWidth,
    h1: gorunenH1,
    h1Toplam: document.querySelectorAll('h1').length,
    dokunmaEsigi: esik,
    kucukHedefSayisi: kucuk.length,
    kucukHedefler: kucuk.slice(0, 40),
    kontrast: {
      olculenMetin: olculen.length,
      belirsizAtlanan: belirsiz,
      enDusuk: enDusuk ? { oran: yuvarla(enDusuk.oran), esik: enDusuk.esik, secici: enDusuk.secici, metin: enDusuk.metin } : null,
      esikAltiSayisi: dusuk.length,
      esikAlti: dusuk.slice(0, 12).map((x) => ({ oran: yuvarla(x.oran), esik: x.esik, secici: x.secici, metin: x.metin })),
    },
  }
}
const OLCUM = `(${olcumIslevi.toString()})()`

// ---- giriş ve gezinme (cek.mjs ile aynı kalıp)

const ORTAM = {
  yonetici: ['GORSEL_KULLANICI', 'GORSEL_PAROLA'],
  atamaci: ['GORSEL_ATAMACI_KULLANICI', 'GORSEL_ATAMACI_PAROLA'],
  makinist: ['GORSEL_MAKINIST_KULLANICI', 'GORSEL_MAKINIST_PAROLA'],
  zorunlu: ['GORSEL_MAKINIST_ZORUNLU_KULLANICI', 'GORSEL_MAKINIST_ZORUNLU_PAROLA'],
}
const ROL_SIRASI = ['acik', 'yonetici', 'atamaci', 'makinist', 'zorunlu']

const YUKSEKLIK = { 1920: 1080, 1366: 768, 683: 768, 375: 812, 320: 568 }

async function main() {
  const a = oku(process.argv)
  const taban = (a.taban ?? process.env.GORSEL_TABAN ?? 'http://localhost:5175').replace(/\/$/, '')
  const cikti = a.cikti
  if (!cikti) throw new Error('--cikti <klasör> gerekli')
  const suzgec = a.yalniz ? a.yalniz.split(',').map((x) => x.trim()).filter(Boolean) : []
  const onceKlasoru = a.once ?? '.team/2026-10-17-onyuz-tasarim/once'
  const eslesir = (ad) => suzgec.length === 0 || suzgec.some((p) => ad.includes(p))
  mkdirSync(cikti, { recursive: true })
  const t0 = Date.now()

  const { cdp, kapat } = await baslat()
  const konsol = []
  cdp.dinle((y, p) => {
    if (y === 'Runtime.exceptionThrown') konsol.push(`istisna: ${p.exceptionDetails.exception?.description ?? p.exceptionDetails.text}`)
    else if (y === 'Runtime.consoleAPICalled' && p.type === 'error') konsol.push(`konsol: ${p.args.map((x) => x.value ?? x.description).join(' ')}`)
    else if (y === 'Log.entryAdded' && p.entry.level === 'error' && !p.entry.url?.endsWith('/api/auth/ben')) konsol.push(`günlük: ${p.entry.text} ${p.entry.url ?? ''}`)
  })
  // yeni belgede yerel depolama temizlenir: her çekim temiz yüklemedir (menü açık/kapalı tercihi sızmaz)
  await cdp.gonder('Page.addScriptToEvaluateOnNewDocument', { source: 'try{localStorage.clear();sessionStorage.clear()}catch(e){}' })
  const kural = {
    donustur: (v, url) => {
      // boş durum: dizi -> [], sayı -> 0, "yuklu" -> false (Ana sayfa demo düğmesi); metin/mantıksal alanlar korunur
      const bos = (x, k) => (Array.isArray(x) ? [] : x && typeof x === 'object' ? Object.fromEntries(Object.entries(x).map(([kk, vv]) => [kk, bos(vv, kk)])) : typeof x === 'number' ? 0 : k === 'yuklu' ? false : x)
      return bos(v)
    },
  }
  const mud = await cdp.ara(kural)
  const BOSALT = /^\/api\/(?!auth|parametre|ayar|kural|ozel-kural|tanimlar)/

  const sonuc = [] // olcum kayıtları
  const uretilen = new Set()
  const hatalar = [] // sahne çalıştırma hataları
  let kimlik = null // oturumdaki rol

  const gorunum = (g, dsf = 1) => cdp.gorunum(g, YUKSEKLIK[g] ?? 768, dsf)
  const git = (yol, bekleMs = 1000) => ortakGit(cdp, taban, yol, bekleMs)
  const tikla = (ifade) => ortakTikla(cdp, ifade)
  const diyalogBekle = async (ek = 0) => {
    await cdp.bekleKosul("!!document.querySelector('dialog[open]')", 4000)
    await bekle(700 + ek)
  }
  const giriseDoldur = (kullanici, parola) => girisDoldur(cdp, kullanici, parola)
  async function oturumKur(rol) {
    if (kimlik === rol) return
    await cdp.gonder('Network.clearBrowserCookies')
    kimlik = rol
    if (rol === 'acik') return
    const [k, p] = ORTAM[rol].map((x) => process.env[x])
    if (!k || !p) throw new Error(`Oturum için ${ORTAM[rol].join(' ve ')} ortam değişkenleri gerekli`)
    await gorunum(rol === 'makinist' || rol === 'zorunlu' ? 375 : 1366)
    await cdp.gonder('Page.navigate', { url: 'about:blank' })
    await cdp.gonder('Page.navigate', { url: `${taban}/#/giris` })
    await giriseDoldur(k, p)
    if (!(await cdp.bekleKosul("!location.hash.startsWith('#/giris')", 15000))) throw new Error(`Giriş başarısız (${rol})`)
    await bekle(800)
  }

  // ---- çekim: ölçer, PNG (+ isteğe bağlı tam sayfa) yazar
  let sahneBaslangic = 0
  async function cek(ad, { tam = false, beklenenHata = false, ek = {} } = {}) {
    const adTam = `${ad}-tam`
    const asil = eslesir(ad)
    const tamIste = tam && eslesir(adTam)
    if (!asil && !tamIste) return
    const olcum = await cdp.degerlendir(OLCUM)
    const g = await cdp.degerlendir('innerWidth')
    if (asil) {
      writeFileSync(join(cikti, `${ad}.png`), Buffer.from(await cdp.ekranGoruntusu(false), 'base64'))
      uretilen.add(`${ad}.png`)
    }
    if (tamIste) {
      writeFileSync(join(cikti, `${adTam}.png`), Buffer.from(await cdp.ekranGoruntusu(true), 'base64'))
      uretilen.add(`${adTam}.png`)
    }
    const hatalarSahne = konsol.slice(sahneBaslangic)
    sonuc.push({ ad, genislik: g, ...olcum, beklenenHata, konsolHatalari: hatalarSahne, ...ek })
    console.log(JSON.stringify({ ad, tasma: olcum.yatayTasma, h1: olcum.h1, kucuk: olcum.kucukHedefSayisi, kontrastAlti: olcum.kontrast.esikAltiSayisi, konsol: hatalarSahne.length }))
  }

  // ---- sahne listesi
  const S = []
  const ekle = (adlar, rol, calistir, secenek = {}) => S.push({ adlar: [].concat(adlar), rol, calistir, ...secenek })

  // sayfa listesi: [ad, yol, rol, hazırlık(isteğe bağlı), tam sayfa 1366'da]
  const kartaGit = async () => {
    await tikla("document.querySelector('table a[href*=\"/makinistler/\"]')")
    await bekle(1500)
  }
  const SAYFALAR = [
    ['giris', '/giris', 'acik', null, false],
    ['ana', '/', 'yonetici', null, true],
    ['atama-masasi', MASA, 'yonetici', null, true],
    ['personel-liste', '/makinistler', 'yonetici', null, true],
    ['personel-karti', '/makinistler', 'yonetici', kartaGit, true],
    ['personel-yeni', '/makinistler/yeni', 'yonetici', null, false],
    ['seferler', '/seferler', 'yonetici', null, false],
    ['sefer-yukle', '/seferler/yukle', 'yonetici', null, false],
    ['takas-talepleri', '/takas', 'yonetici', null, false],
    ['belgeler', '/belgeler', 'yonetici', null, true],
    ['tanimlar', '/tanimlar', 'yonetici', null, false],
    ['ayarlar', '/ayarlar', 'yonetici', null, true],
    ['adalet-raporu', '/rapor/adalet', 'yonetici', null, true],
    ['yedek-plani', '/rapor/yedek', 'yonetici', null, false],
    ['ozel-kurallar', '/ozel-kurallar', 'yonetici', null, false],
    ['izinler', '/izin', 'yonetici', null, true],
    ['konaklama', '/konaklama', 'yonetici', null, true],
    ['konaklama-raporu', '/konaklama/rapor', 'yonetici', null, false],
    ['puantaj', '/rapor/puantaj', 'yonetici', null, true],
    ['kadro-ihtiyaci-bos', '/rapor/kadro', 'yonetici', null, true],
    ['denetim-raporu', '/degisiklik', 'yonetici', null, true],
    ['yazdir-gun', `/yazdir/gun/${YARIN}`, 'yonetici', null, true],
  ]
  for (const [ad, yol, rol, hazirla, tam] of SAYFALAR) {
    for (const g of [1366, 1920]) {
      ekle(`${ad}-${g}`, rol, async () => {
        await gorunum(g)
        await git(yol)
        if (hazirla) await hazirla()
        await cek(`${ad}-${g}`, { tam: tam && g === 1366 })
      }, { tamAdi: tam && g === 1366 })
    }
    // %200 yakınlaştırma: 1366 px görünüm 683 CSS px'e iner, cihaz ölçeği 2
    ekle(`olcek-${ad}-683`, rol, async () => {
      await gorunum(683, 2)
      await git(yol)
      if (hazirla) await hazirla()
      await cek(`olcek-${ad}-683`)
    })
  }
  // atamacı: giriş sonrası ana + masa
  for (const [ad, yol] of [['atamaci-ana', '/'], ['atamaci-atama-masasi', MASA]]) {
    for (const g of [1366, 1920]) ekle(`${ad}-${g}`, 'atamaci', async () => { await gorunum(g); await git(yol); await cek(`${ad}-${g}`) })
  }

  // masa durumları
  const ilkSatirSec = async () => {
    await tikla("document.querySelector('tr.sefer-satiri')")
    await cdp.bekleKosul("document.querySelectorAll('tr.aday').length > 0", 8000)
    await bekle(600)
  }
  for (const g of [1366, 1920]) {
    ekle(`atama-masasi-secili-${g}`, 'yonetici', async () => {
      await gorunum(g)
      await git(MASA)
      await ilkSatirSec()
      await cek(`atama-masasi-secili-${g}`)
    })
  }
  ekle('atama-masasi-kirmizi-secili-1366', 'yonetici', async () => {
    await gorunum(1366)
    // kırmızı satırı olan ilk gün (bugünden başlayarak)
    await git('/atama')
    const gun = await cdp.degerlendir(`(async () => {
      for (let i = 0; i < 8; i++) {
        const d = new Date(); d.setDate(d.getDate() + i); const t = d.toLocaleDateString('sv-SE')
        const r = await fetch('/api/atama/gun?tarih=' + t, { credentials: 'same-origin' })
        if (r.ok && (await r.json()).satirlar.some((s) => s.kirmizi)) return t
      }
      return null })()`)
    await git(`/atama?tarih=${gun ?? BUGUN}`)
    await cdp.degerlendir("document.querySelector('tr.sefer-satiri.kirmizi')?.scrollIntoView({ block: 'center' })")
    await bekle(300)
    await tikla("document.querySelector('tr.sefer-satiri.kirmizi')")
    await bekle(900)
    await cdp.degerlendir("document.querySelector('tr.sefer-satiri.kirmizi')?.scrollIntoView({ block: 'center' })")
    await bekle(300)
    await cek('atama-masasi-kirmizi-secili-1366', { ek: { gun } })
  })
  ekle('atama-masasi-klavye-odak-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git(MASA)
    await cdp.degerlendir("document.querySelector('tr.sefer-satiri')?.focus()")
    await cdp.tus('ArrowDown', 'ArrowDown', 40)
    await cdp.tus('ArrowDown', 'ArrowDown', 40)
    await bekle(200)
    await cek('atama-masasi-klavye-odak-1366')
  })

  // boş durumlar (Fetch ile /api yanıtı boşaltılır)
  const bosSahne = (ad, yol, g) =>
    ekle(`${ad}-bos-${g}`, 'yonetici', async () => {
      await gorunum(g)
      kural.bosalt = BOSALT
      try {
        await git(yol)
        await cek(`${ad}-bos-${g}`)
      } finally {
        kural.bosalt = null
      }
    })
  bosSahne('ana', '/', 1366)
  bosSahne('ana', '/', 1920)
  bosSahne('atama-masasi', MASA, 1366)
  bosSahne('atama-masasi', MASA, 1920)
  bosSahne('adalet-raporu', '/rapor/adalet', 1366)
  bosSahne('belgeler', '/belgeler', 1366)
  bosSahne('izinler', '/izin', 1366)
  bosSahne('personel-liste', '/makinistler', 1366)
  bosSahne('seferler', '/seferler', 1366)

  // yükleniyor: istek tutulur, ekran yakalanır, sonra sürdürülür
  const yukleniyorSahne = (ad, yolIfade, tutRe) =>
    ekle(`${ad}-yukleniyor-1366`, 'yonetici', async () => {
      await gorunum(1366)
      kural.tut = tutRe
      try {
        await cdp.gonder('Page.navigate', { url: 'about:blank' })
        await cdp.gonder('Page.navigate', { url: `${taban}/#${yolIfade}` })
        await cdp.bekleKosul("document.readyState === 'complete'")
        for (let i = 0; i < 50 && mud.tutulan() === 0; i++) await bekle(100)
        await bekle(700)
        await cek(`${ad}-yukleniyor-1366`, { ek: { tutulanIstek: mud.tutulan() } })
      } finally {
        kural.tut = null
        await mud.birak()
      }
    })
  yukleniyorSahne('atama-masasi', MASA, /^\/api\/atama\/gun/)
  yukleniyorSahne('adalet-raporu', '/rapor/adalet', /^\/api\/rapor\/adalet\?/)

  // kadro ihtiyacı: hesaplanıyor (istek tutulur) -> sonuç (aynı çalıştırma)
  const kadroHesapla = async () => {
    await tikla("document.querySelector('[data-test=hesapla]')")
  }
  const kadroSonucBekle = async () => {
    await cdp.bekleKosul("!!document.querySelector('[data-test=egri]') && !/Hesaplanıyor/.test(document.body.innerText)", 90000)
    await bekle(900)
  }
  ekle(['kadro-ihtiyaci-hesaplaniyor-1366', 'kadro-ihtiyaci-sonuc-1366'], 'yonetici', async () => {
    await gorunum(1366)
    await git('/rapor/kadro')
    kural.tut = /^\/api\/rapor\/kadro$/
    try {
      await kadroHesapla()
      for (let i = 0; i < 50 && mud.tutulan() === 0; i++) await bekle(100)
      await bekle(700)
      await cek('kadro-ihtiyaci-hesaplaniyor-1366', { ek: { tutulanIstek: mud.tutulan() } })
    } finally {
      kural.tut = null
      await mud.birak()
    }
    if (eslesir('kadro-ihtiyaci-sonuc-1366') || eslesir('kadro-ihtiyaci-sonuc-1366-tam')) {
      await kadroSonucBekle()
      await cek('kadro-ihtiyaci-sonuc-1366', { tam: true })
    }
  }, { tamAdi: true })
  ekle('kadro-ihtiyaci-sonuc-1920', 'yonetici', async () => {
    await gorunum(1920)
    await git('/rapor/kadro')
    await kadroHesapla()
    await kadroSonucBekle()
    await cek('kadro-ihtiyaci-sonuc-1920')
  })
  ekle('denetim-raporu-sonuc-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git('/degisiklik')
    await tikla(dugme('Raporu getir'))
    await bekle(1800)
    await cek('denetim-raporu-sonuc-1366', { tam: true })
  }, { tamAdi: true })
  ekle('puantaj-sonuc-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git('/rapor/puantaj')
    await tikla(dugme('Raporu getir'))
    await bekle(2000)
    await cek('puantaj-sonuc-1366')
  })

  // diyaloglar (masa: yarın; seçiciler aria-label'a göre)
  const masaDiyalog = (ad, ac, ek = 0) =>
    ekle(ad, 'yonetici', async () => {
      await gorunum(1366)
      await git(`/atama?tarih=${YARIN}`)
      const tamam = await ac()
      await diyalogBekle(ek)
      if (!tamam) console.log('UYARI: diyalog açılamadı', ad)
      await cek(ad, { ek: { diyalogAcik: !!(await cdp.degerlendir("!!document.querySelector('dialog[open]')")) } })
    })
  masaDiyalog('atama-otomatik-ata-dialog-1366', () => tikla(dugme('Otomatik ata')))
  ekle('atama-otomatik-ata-onizleme-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git(`/atama?tarih=${YARIN}`)
    await tikla(dugme('Otomatik ata'))
    await diyalogBekle()
    await tikla(dugme('Önizle'))
    await cdp.bekleKosul("/atanacak/i.test(document.querySelector('dialog[open]')?.innerText ?? '')", 30000)
    await bekle(900)
    await cek('atama-otomatik-ata-onizleme-1366')
  })
  masaDiyalog('atama-gecikme-dialog-1366', () => tikla("document.querySelector('tr.sefer-satiri button[aria-label*=\"gecikme gir\"]')"))
  masaDiyalog('atama-bol-dialog-1366', () => tikla("document.querySelector('tr.sefer-satiri button[aria-label*=\"seferini böl\"]')"), 600)
  masaDiyalog('atama-iptal-dialog-1366', () => tikla("document.querySelector('tr.sefer-satiri button[aria-label*=\"seferini iptal et\"]')"))
  masaDiyalog('atama-yerine-ata-dialog-1366', () => tikla("document.querySelector('tr.sefer-satiri:has(.gorevli-asil) button[aria-label*=\"yerine ata\"]')"), 1500)
  masaDiyalog('atama-onay-dialog-1366', async () => {
    await ilkSatirSec()
    await cdp.bekleKosul("document.querySelectorAll('tr.aday.secilebilir').length > 0", 8000)
    return tikla("document.querySelector('tr.aday.secilebilir')")
  })
  ekle('ozel-kural-kurucu-dialog-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git('/ozel-kurallar')
    const tamam = await tikla(dugmeIcerir('Yeni kural'))
    await diyalogBekle()
    if (!tamam) console.log('UYARI: Yeni kural düğmesi bulunamadı')
    await cek('ozel-kural-kurucu-dialog-1366')
  })
  ekle('takas-onay-dialog-1366', 'yonetici', async () => {
    await gorunum(1366)
    await git('/takas')
    const tamam = await tikla("[...document.querySelectorAll('button')].find((b) => /Onay|İncele|Karar/.test(b.textContent))")
    await diyalogBekle(800)
    if (!tamam) console.log('UYARI: takas onay düğmesi bulunamadı')
    await cek('takas-onay-dialog-1366')
  })

  // giriş hataları ve sunucu yok
  ekle('giris-hata-1366', 'acik', async () => {
    await gorunum(1366)
    await git('/giris')
    await giriseDoldur('yanlis@ornek.test', 'yanlis-parola-1')
    await cdp.bekleKosul("/hata/i.test(document.body.innerText)", 8000)
    await bekle(500)
    await cek('giris-hata-1366')
  })
  ekle('giris-hata-sunucu-yok-1366', 'acik', async () => {
    await gorunum(1366)
    await git('/giris')
    kural.dusur = /^\/api\/auth\/giris/
    try {
      await giriseDoldur('x@ornek.test', 'yanlis-parola-1')
      await cdp.bekleKosul("/Beklenmeyen|bağlan|hata/i.test(document.body.innerText)", 8000)
      await bekle(500)
      await cek('giris-hata-sunucu-yok-1366', { beklenenHata: true })
    } finally {
      kural.dusur = null
    }
  })
  ekle('sunucu-yok-acilis-1366', 'acik', async () => {
    await gorunum(1366)
    kural.dusur = /^\/api\//
    try {
      await cdp.gonder('Page.navigate', { url: 'about:blank' })
      await cdp.gonder('Page.navigate', { url: `${taban}/#/` })
      await cdp.bekleKosul("document.readyState === 'complete'")
      await bekle(2000)
      await cek('sunucu-yok-acilis-1366', { beklenenHata: true })
    } finally {
      kural.dusur = null
    }
  })
  ekle('mobil-giris-375', 'acik', async () => {
    await gorunum(375)
    await git('/giris')
    await cek('mobil-giris-375')
  })
  ekle('mobil-parola-zorunlu-375', 'zorunlu', async () => {
    await gorunum(375)
    // oturumKur giriş sonrası geçici parola bayrağı nedeniyle parola sayfasına yönlendirilmeyi bekler
    await cdp.bekleKosul("location.hash.includes('parola')", 6000)
    await bekle(800)
    await cek('mobil-parola-zorunlu-375')
  })

  // makinist (Benim) sayfaları: 375 (+tam) ve 320; %200: 683 ve 320
  const BENIM = ['gorevler', 'belgeler', 'izin', 'ozet', 'takas', 'konaklama', 'parola']
  for (const b of BENIM) {
    for (const g of [375, 320]) {
      ekle(`mobil-${b}-${g}`, 'makinist', async () => {
        await gorunum(g)
        await git(`/benim/${b}`)
        await cek(`mobil-${b}-${g}`, { tam: g === 375 })
      }, { tamAdi: g === 375 })
    }
    for (const g of [683, 320]) {
      ekle(`olcek-mobil-${b}-${g}`, 'makinist', async () => {
        await gorunum(g, 2)
        await git(`/benim/${b}`)
        await cek(`olcek-mobil-${b}-${g}`)
      })
    }
  }

  // yazdırma: print medya emülasyonu (yazdırma rotaları + yazdırılan sayfalar)
  const yazdirSahne = (ad, yol) =>
    ekle(`yazdir-${ad}-1366-yazdir`, 'yonetici', async () => {
      await gorunum(1366)
      await cdp.gonder('Emulation.setEmulatedMedia', { media: 'print' })
      try {
        await git(yol, 1400)
        await cek(`yazdir-${ad}-1366-yazdir`)
      } finally {
        await cdp.gonder('Emulation.setEmulatedMedia', { media: '' })
      }
    })
  yazdirSahne('gun', `/yazdir/gun/${YARIN}`)
  yazdirSahne('atama-masasi', MASA)
  yazdirSahne('belgeler', '/belgeler')
  yazdirSahne('adalet', `/yazdir/adalet?ay=${AY}`)
  yazdirSahne('yedek-plani', '/rapor/yedek')
  yazdirSahne('puantaj', `/yazdir/puantaj?ay=${AY}`)
  yazdirSahne('konaklama-raporu', '/konaklama/rapor')
  yazdirSahne('degisiklik', `/yazdir/degisiklik?bas=${gunOtele(-7)}&bit=${BUGUN}`)

  // klavye odağı: gerçek Tab tuşu; koşul sağlanana dek basılır
  const tabaKadar = async (kosul, azami = 90) => {
    for (let i = 0; i < azami; i++) {
      await cdp.tus('Tab', 'Tab', 9)
      if (await cdp.degerlendir(kosul)) return i + 1
    }
    return 0
  }
  const odakSahne = (ad, rol, g, hazirla) =>
    ekle(`odak-${ad}`, rol, async () => {
      await gorunum(g)
      const basilan = await hazirla()
      await bekle(250)
      await cek(`odak-${ad}`, {
        ek: {
          tusSayisi: basilan,
          odak: await cdp.degerlendir(`(() => { const e = document.activeElement; if (!e || e === document.body) return null; const s = getComputedStyle(e); return { oge: e.tagName.toLowerCase() + (e.className && typeof e.className === 'string' ? '.' + e.className.trim().split(/\\s+/)[0] : ''), odakGorunur: e.matches(':focus-visible'), cerceve: s.outlineStyle + ' ' + s.outlineWidth, golge: s.boxShadow.slice(0, 60) } })()`),
        },
      })
    })
  odakSahne('atla', 'yonetici', 1366, async () => {
    await git('/')
    return await tabaKadar('true', 1)
  })
  odakSahne('menu', 'yonetici', 1366, async () => {
    await git('/')
    return await tabaKadar("!!document.activeElement.closest('nav') && /#\\/atama/.test(document.activeElement.getAttribute('href') || document.activeElement.href || '')")
  })
  odakSahne('masa-satiri', 'yonetici', 1366, async () => {
    await git(MASA)
    return await tabaKadar("document.activeElement.matches('tr.sefer-satiri')", 120)
  })
  odakSahne('dialog', 'yonetici', 1366, async () => {
    await git(`/atama?tarih=${YARIN}`)
    await tikla(dugme('Otomatik ata'))
    await diyalogBekle()
    let n = 0
    for (let i = 0; i < 4; i++, n++) await cdp.tus('Tab', 'Tab', 9)
    return n
  })
  odakSahne('form-alani', 'yonetici', 1366, async () => {
    await git('/makinistler/yeni')
    return await tabaKadar("document.activeElement.matches('main input, main select, main textarea, form input')")
  })
  odakSahne('makinist-sekme', 'makinist', 375, async () => {
    await git('/benim/gorevler')
    return await tabaKadar("!!document.activeElement.closest('nav') && /benim\\/belgeler/.test(document.activeElement.getAttribute('href') || document.activeElement.href || '')")
  })

  // ---- çalıştır: oturum sırasına göre; süzgeçle eşleşmeyen sahneler atlanır
  const hepsiAdlar = (s) => s.adlar.flatMap((x) => (s.tamAdi ? [x, `${x}-tam`] : [x]))
  const calisacak = S.filter((s) => hepsiAdlar(s).some(eslesir)).sort((x, y) => ROL_SIRASI.indexOf(x.rol) - ROL_SIRASI.indexOf(y.rol))
  console.log(`${calisacak.length} sahne çalışacak (toplam ${S.length})`)
  try {
    for (const s of calisacak) {
      try {
        await oturumKur(s.rol)
        sahneBaslangic = konsol.length
        await s.calistir()
      } catch (e) {
        hatalar.push({ sahne: s.adlar.join(','), hata: e.message })
        console.log(`HATA ${s.adlar.join(',')}: ${e.message}`)
      }
    }
  } finally {
    await kapat()
  }

  // ---- ölçüm özeti
  const beklenmeyen = (x) => (x.beklenenHata ? [] : x.konsolHatalari)
  const ozet = {
    olusturma: new Date().toISOString(),
    taban,
    sureSn: Math.round((Date.now() - t0) / 1000),
    cekimSayisi: sonuc.length,
    yatayTasma: sonuc.filter((x) => x.yatayTasma).map((x) => `${x.ad} (${x.scrollWidth}>${x.clientWidth})`),
    h1BirdenFazlaYaDaYok: sonuc.filter((x) => x.h1 !== 1).map((x) => `${x.ad} (h1=${x.h1})`),
    konsolHatasiOlanlar: sonuc.filter((x) => beklenmeyen(x).length > 0).map((x) => ({ ad: x.ad, hatalar: beklenmeyen(x).slice(0, 3) })),
    kontrastEsikAltiOlanlar: sonuc.filter((x) => x.kontrast.esikAltiSayisi > 0).map((x) => ({ ad: x.ad, sayi: x.kontrast.esikAltiSayisi, enDusuk: x.kontrast.enDusuk })),
    kontrastEnDusuk: sonuc.reduce((m, x) => (x.kontrast.enDusuk && (!m || x.kontrast.enDusuk.oran < m.oran) ? { ad: x.ad, ...x.kontrast.enDusuk } : m), null),
    dokunmaHedefiKucukOlanlar: sonuc.filter((x) => x.kucukHedefSayisi > 0).map((x) => ({ ad: x.ad, esik: x.dokunmaEsigi, sayi: x.kucukHedefSayisi })),
    sahneHatalari: hatalar,
  }
  writeFileSync(join(cikti, 'son-olcum.json'), JSON.stringify({ ozet, cekimler: sonuc }, null, 2))

  // ---- "önce" ile karşılaştırma (yalnız süzgeçsiz tam çalıştırmada eksik sayılır)
  if (suzgec.length === 0 && existsSync(onceKlasoru)) {
    const once = readdirSync(onceKlasoru).filter((f) => f.endsWith('.png'))
    const var_ = new Set(existsSync(cikti) ? readdirSync(cikti) : [])
    const eksik = once.filter((f) => !var_.has(f))
    console.log(`önce: ${once.length} görüntü; üretilen: ${once.length - eksik.length}; eksik: ${eksik.length}${eksik.length ? ' -> ' + eksik.join(', ') : ''}`)
    ozet.onceKarsilastirma = { once: once.length, eksik }
    writeFileSync(join(cikti, 'son-olcum.json'), JSON.stringify({ ozet, cekimler: sonuc }, null, 2))
  }
  console.log(`bitti: ${sonuc.length} çekim, ${hatalar.length} sahne hatası, ${ozet.sureSn} sn`)
  if (hatalar.length) process.exitCode = 1
}

main().catch((e) => {
  console.error(e.message)
  process.exit(2)
})
