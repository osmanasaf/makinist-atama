// Atama masası + diyalog görüntüleri ve ölçümleri (tasarım aşaması C + D). cek.mjs ile aynı ortam ve değişkenler (GORSEL_KULLANICI/GORSEL_PAROLA = atamacı ya da yönetici,
// GORSEL_TABAN, CHROME_YOLU); betikte parola yok. Kullanım: node scripts/gorsel/cd.mjs --cikti .team/<iş>/sonra/C-D [--genislik 1366,1920]
//
// Sahneler (her genişlikte ya da yalnız 1366'da): masa, masa-secili (aday paneli), masa-kirmizi (+ seçili), masa-gecikme, masa-klavye-odak,
// diyaloglar (otomatik ata, gecikme, böl, atama onayı, yerine ata), takas onayı.
// Ölçüm (cd-olcum.json): görünen sefer sayısı, yatay taşma, satır yüksekliği, sarılan saat/tren no sayısı, kontrast (en düşük oran / seçici), konsol hatası.
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { baslat } from './cdp.mjs'
import { bekle, git, giris, gunOtele, oku, tikla } from './ortak.mjs'
const OLCUM = `(() => {
  const d = document.documentElement
  const satirlar = [...document.querySelectorAll('tr.sefer-satiri')]
  const kap = document.querySelector('.masa-kap')
  const kr = kap?.getBoundingClientRect()
  const baslikAlt = document.querySelector('.masa-tablo thead')?.getBoundingClientRect().bottom ?? 0
  const gorunen = satirlar.filter((s) => {
    const r = s.getBoundingClientRect()
    return kr && r.top >= baslikAlt - 1 && r.bottom <= kr.bottom + 1 && r.top >= 0 && r.bottom <= innerHeight
  })
  const yuks = gorunen.map((s) => Math.round(s.getBoundingClientRect().height))
  const tekSatir = (sel) => [...document.querySelectorAll(sel)].filter((e) => { const r = e.getBoundingClientRect(); const lh = parseFloat(getComputedStyle(e).lineHeight) || 18; return r.height > lh * 1.6 }).length
  // kontrast
  const rgb = (c) => { const m = c.match(/rgba?\\(([^)]+)\\)/); if (!m) return null; const p = m[1].split(/[ ,\\/]+/).filter(Boolean).map(Number); return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 } }
  const lum = ({ r, g, b }) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4) }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b) }
  const karisim = (ust, alt) => ({ r: ust.r * ust.a + alt.r * (1 - ust.a), g: ust.g * ust.a + alt.g * (1 - ust.a), b: ust.b * ust.a + alt.b * (1 - ust.a), a: 1 })
  const zemin = (el) => { const zincir = []; for (let e = el; e; e = e.parentElement) zincir.push(e); let bg = { r: 255, g: 255, b: 255, a: 1 }; for (const e of zincir.reverse()) { const c = rgb(getComputedStyle(e).backgroundColor); if (c && c.a > 0) bg = karisim(c, bg) } return bg }
  const oran = (el) => { const on = rgb(getComputedStyle(el).color); if (!on) return null; const bg = zemin(el); const f = karisim(on, bg); const a = lum(f), b = lum(bg); return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) }
  const SECICILER = ['.sefer-satiri .tren-no', '.sefer-satiri .guzergah', '.sefer-satiri .gorevli-ad', '.sefer-satiri .gordum', '.sefer-satiri .bos-neden', '.sefer-satiri .kirmizi-neden', '.sefer-satiri .uyari-notu', '.masa-tablo th', '.cip', '.sefer-satiri .rozet', '.aday .aday-ad', '.aday .alt-bilgi', '.aday .rozet', '.aday .ihlal-listesi li', '.aday.soluk', 'dialog h2', 'dialog .bildirim', '.intikal', '.ipucu']
  const kontrast = {}
  for (const sel of SECICILER) {
    const r = [...document.querySelectorAll(sel)].filter((e) => e.getBoundingClientRect().width > 0 && (e.textContent || '').trim()).map(oran).filter((x) => x !== null)
    if (r.length) kontrast[sel] = { n: r.length, enDusuk: Math.round(Math.min(...r) * 100) / 100 }
  }
  return {
    yatayTasma: d.scrollWidth > d.clientWidth,
    sayfaKayiyor: d.scrollHeight > innerHeight + 1,
    belgeYuksekligi: d.scrollHeight,
    pencereYuksekligi: innerHeight,
    masaYatayKaydirma: kap ? kap.scrollWidth > kap.clientWidth + 1 : null,
    seferToplam: satirlar.length,
    gorunenSefer: gorunen.length,
    satirYuksekligi: yuks.length ? { min: Math.min(...yuks), medyan: yuks.sort((a, b) => a - b)[Math.floor(yuks.length / 2)], max: Math.max(...yuks) } : null,
    sarilanSaat: tekSatir('.sefer-satiri .saat'),
    sarilanTrenNo: tekSatir('.sefer-satiri .tren-no'),
    masaKapYuksekligi: kr ? Math.round(kr.height) : null,
    stickyBaslikUstu: document.querySelector('.masa-tablo th') ? Math.round(document.querySelector('.masa-tablo th').getBoundingClientRect().top) : null,
    kontrast,
    kontrastAltinda45: Object.entries(kontrast).filter(([, v]) => v.enDusuk < 4.5).map(([k, v]) => k + ' ' + v.enDusuk),
  }
})()`


async function main() {
  const a = oku(process.argv)
  const taban = (a.taban ?? process.env.GORSEL_TABAN ?? 'http://localhost:5175').replace(/\/$/, '')
  const cikti = a.cikti ?? '.team/sonra/C-D'
  const onek = a.onek ?? ''
  const genislikler = (a.genislik ?? '1366,1920').split(',').map(Number)
  const kullanici = process.env.GORSEL_KULLANICI
  const parola = process.env.GORSEL_PAROLA
  if (!kullanici || !parola) throw new Error('GORSEL_KULLANICI ve GORSEL_PAROLA ayarlayın.')
  mkdirSync(cikti, { recursive: true })
  const { cdp, kapat } = await baslat()
  const konsol = []
  cdp.dinle((y, p) => {
    if (y === 'Runtime.exceptionThrown') konsol.push(`istisna: ${p.exceptionDetails.exception?.description ?? p.exceptionDetails.text}`)
    else if (y === 'Runtime.consoleAPICalled' && p.type === 'error') konsol.push(`konsol: ${p.args.map((x) => x.value ?? x.description).join(' ')}`)
  })
  const sonuc = []
  const yukseklik = (g) => ({ 1920: 1080 }[g] ?? 768)
  const cek = async (ad, g, ek = {}) => {
    const olcum = await cdp.degerlendir(OLCUM)
    writeFileSync(join(cikti, `${onek}${ad}-${g}.png`), Buffer.from(await cdp.ekranGoruntusu(false), 'base64'))
    sonuc.push({ ad, genislik: g, ...olcum, ...ek, konsolHatalari: [...konsol] })
    console.log(JSON.stringify({ ad, g, gorunenSefer: olcum.gorunenSefer, tasma: olcum.yatayTasma, sayfaKayiyor: olcum.sayfaKayiyor, sarilan: olcum.sarilanSaat + olcum.sarilanTrenNo, masaKaydirma: olcum.masaYatayKaydirma, belge: olcum.belgeYuksekligi, pencere: olcum.pencereYuksekligi, kontrastAltinda: olcum.kontrastAltinda45.length }))
  }
  try {
    await cdp.gorunum(genislikler[0], yukseklik(genislikler[0]))
    await giris(cdp, taban, kullanici, parola)
    const yarin = gunOtele(1)
    const bugun = gunOtele(0)
    for (const g of genislikler) {
      await cdp.gorunum(g, yukseklik(g))
      konsol.length = 0
      // 1) masa: seçim yok
      await git(cdp, taban, `/atama?tarih=${yarin}`)
      await cek('masa', g)
      // 2) seçili sefer + aday paneli
      await tikla(cdp, "document.querySelector('tr.sefer-satiri')")
      await cdp.bekleKosul("document.querySelectorAll('tr.aday').length > 0", 8000)
      await bekle(500)
      await cek('masa-secili', g)
      if (g === genislikler[0] || g === 1366) {
        // 3) aday paneline kaydır: engelli/uyarılı adaylar görünsün
        await cdp.degerlendir("document.querySelector('.aday-kap')?.scrollTo(0, 400)")
        await cek('aday-paneli', g)
      }
      // 4) kırmızı satır (bugün) ve kırmızı + seçili
      await git(cdp, taban, `/atama?tarih=${bugun}`)
      await cdp.degerlendir("document.querySelector('tr.sefer-satiri.kirmizi')?.scrollIntoView({ block: 'center' })")
      await bekle(300)
      await cek('masa-kirmizi', g)
      await tikla(cdp, "document.querySelector('tr.sefer-satiri.kirmizi')")
      await bekle(900)
      await cek('masa-kirmizi-secili', g)
      // 5) gecikmeli sefer satırı
      await git(cdp, taban, `/atama?tarih=${bugun}`)
      await cdp.degerlendir("document.querySelector('.gecikme')?.closest('tr')?.scrollIntoView({ block: 'center' })")
      await bekle(300)
      await cek('masa-gecikme', g)
    }
    // 6) 1366: klavye odağı (gerçek ok tuşu), diyaloglar
    await cdp.gorunum(1366, 768)
    await git(cdp, taban, `/atama?tarih=${yarin}`)
    await cdp.degerlendir("document.querySelector('tr.sefer-satiri')?.focus()")
    await cdp.tus('ArrowDown', 'ArrowDown', 40)
    await cdp.tus('ArrowDown', 'ArrowDown', 40)
    await bekle(200)
    await cek('masa-klavye-odak', 1366)

    const diyalog = async (ad, ac, ek = 0) => {
      await git(cdp, taban, `/atama?tarih=${yarin}`)
      const tamam = await ac()
      await cdp.bekleKosul("!!document.querySelector('dialog[open]')", 4000)
      await bekle(700 + ek)
      if (!tamam) console.log('UYARI: diyalog açılamadı', ad)
      await cek(ad, 1366, { diyalogAcik: !!(await cdp.degerlendir("!!document.querySelector('dialog[open]')")) })
    }
    await diyalog('atama-otomatik-ata-dialog', () => tikla(cdp, "[...document.querySelectorAll('button')].find((b) => b.textContent.trim() === 'Otomatik ata')"))
    await diyalog('atama-gecikme-dialog', () => tikla(cdp, "document.querySelector('tr.sefer-satiri button[aria-label$=\"gecikme gir\"]')"))
    await diyalog('atama-bol-dialog', () => tikla(cdp, "document.querySelector('tr.sefer-satiri button[aria-label$=\"seferini böl\"]')"), 600)
    await diyalog('atama-iptal-dialog', () => tikla(cdp, "document.querySelector('tr.sefer-satiri button[aria-label$=\"seferini iptal et\"]')"))
    await diyalog('atama-yerine-ata-dialog', () => tikla(cdp, "document.querySelector('tr.sefer-satiri:has(.gorevli-asil) button[aria-label$=\"yerine ata\"]')"), 1500)
    await diyalog('atama-onay-dialog', async () => {
      await tikla(cdp, "document.querySelector('tr.sefer-satiri')")
      await cdp.bekleKosul("document.querySelectorAll('tr.aday.secilebilir').length > 0", 8000)
      return tikla(cdp, "document.querySelector('tr.aday.secilebilir')")
    })
    // 7) takas onayı
    await git(cdp, taban, '/takas')
    const acildi = await cdp.degerlendir(`(() => { const b = [...document.querySelectorAll('button')].find((x) => /Onay|İncele|Karar/.test(x.textContent)); if (!b) return false; b.click(); return true })()`)
    await cdp.bekleKosul("!!document.querySelector('dialog[open]')", 4000)
    await bekle(1500)
    if (!acildi) console.log('UYARI: takas onay düğmesi bulunamadı')
    await cek('takas-onay-dialog', 1366, { diyalogAcik: !!(await cdp.degerlendir("!!document.querySelector('dialog[open]')")) })
  } finally {
    await kapat()
  }
  writeFileSync(join(cikti, `${onek}cd-olcum.json`), JSON.stringify(sonuc, null, 2))
}

main().catch((e) => {
  console.error(e.message)
  process.exit(2)
})
