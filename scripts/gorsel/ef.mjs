// Aşama E + F görüntüleri ve ölçümleri (kadro/raporlar, ayarlar, kurallar, tanımlar, personel, listeler) + masa aday paneli (1920) + gece göstergesi.
// cek.mjs ile aynı ortam ve değişkenler (GORSEL_KULLANICI / GORSEL_PAROLA = yönetici, GORSEL_TABAN, GORSEL_API = backend, varsayılan http://localhost:8082).
// Kullanım: node scripts/gorsel/ef.mjs --cikti .team/<iş>/sonra/E-F
// Gece sahnesi: yönetici ile gece.baslangic/gece.bitis ayarı API'den değiştirilir, masa yeniden açılır, sonra ayar eski değerine döndürülür.
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { baslat } from './cdp.mjs'
import { bekle, dugme, git, giris, gunOtele, oku, tikla } from './ortak.mjs'
const OLCUM = `(() => {
  const d = document.documentElement
  const gorunur = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden' && !e.closest('.gorunmez') }
  const kucuk = [...document.querySelectorAll('a[href],button,input:not([type=hidden]),select,textarea,[role=tab]')].filter(gorunur)
    .filter((e) => !(e.matches('input[type=checkbox],input[type=radio]') && e.closest('label')))
    .filter((e) => { const r = e.getBoundingClientRect(); return r.width < 24 || r.height < 24 }).length
  const sayisal = [...document.querySelectorAll('td.sayisal,th.sayisal')].filter(gorunur)
  const solda = sayisal.filter((e) => ['left', 'start'].includes(getComputedStyle(e).textAlign)).length
  const tabular = sayisal.filter((e) => !getComputedStyle(e).fontVariantNumeric.includes('tabular-nums')).length
  return {
    yol: location.hash, h1: document.querySelectorAll('h1').length,
    yatayTasma: d.scrollWidth > d.clientWidth, scrollWidth: d.scrollWidth, clientWidth: d.clientWidth,
    kucukHedef: kucuk, sayisalHucre: sayisal.length, sayisalSoldaHizali: solda, sayisalTabularDegil: tabular,
    emojiVar: /[\\u{1F300}-\\u{1FAFF}\\u2705\\u274C\\u26A0\\u26D4\\u23F3\\u231B]/u.test(document.body.innerText),
  }
})()`


// Node tarafı API (ayar değiştirme): oturum + CSRF çerezi elle taşınır.
async function apiOturumu(api, eposta, parola) {
  const jar = {}
  const al = (r) => {
    for (const c of r.headers.getSetCookie()) {
      const [kv] = c.split(';')
      const i = kv.indexOf('=')
      jar[kv.slice(0, i)] = kv.slice(i + 1)
    }
  }
  const baslik = () => ({
    'content-type': 'application/json',
    cookie: Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '),
    'x-xsrf-token': jar['XSRF-TOKEN'] ?? '',
  })
  al(await fetch(api + '/api/auth/ben'))
  const g = await fetch(api + '/api/auth/giris', { method: 'POST', headers: baslik(), body: JSON.stringify({ eposta, parola }) })
  al(g)
  if (!g.ok) throw new Error('API girişi başarısız')
  return async (m, p, govde) => {
    const r = await fetch(api + p, { method: m, headers: baslik(), body: govde ? JSON.stringify(govde) : undefined })
    al(r)
    return r
  }
}

async function main() {
  const a = oku(process.argv)
  const taban = (a.taban ?? process.env.GORSEL_TABAN ?? 'http://localhost:5175').replace(/\/$/, '')
  const apiTaban = (process.env.GORSEL_API ?? 'http://localhost:8082').replace(/\/$/, '')
  const cikti = a.cikti ?? '.team/sonra/E-F'
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
  // tüm sayfa yüksekliği de çekilecek sahneler (grafik, uzun formlar)
  const TAM = new Set(['kadro-ihtiyaci-sonuc', 'ayarlar', 'personel-karti', 'tanimlar-gar-sureleri', 'adalet-raporu'])
  const yukseklik = (g) => (g === 1920 ? 1080 : 768)
  const cek = async (ad, g, ek = {}) => {
    const olcum = await cdp.degerlendir(OLCUM)
    writeFileSync(join(cikti, `${ad}-${g}.png`), Buffer.from(await cdp.ekranGoruntusu(false), 'base64'))
    sonuc.push({ ad, genislik: g, ...olcum, ...ek, konsolHatalari: [...konsol] })
    console.log(JSON.stringify({ ad, g, h1: olcum.h1, tasma: olcum.yatayTasma, kucuk: olcum.kucukHedef, sayisal: olcum.sayisalHucre, solda: olcum.sayisalSoldaHizali, tabularDegil: olcum.sayisalTabularDegil, emoji: olcum.emojiVar, konsol: konsol.length, ...ek }))
  }
  // [ad, yol, genişlikler, ön eylem]
  const SAHNELER = [
    ['kadro-ihtiyaci-bos', '/rapor/kadro', [1366, 1920]],
    ['kadro-ihtiyaci-sonuc', '/rapor/kadro', [1366, 1920], async () => {
      await tikla(cdp, "document.querySelector('[data-test=hesapla]')")
      await cdp.bekleKosul("!!document.querySelector('[data-test=egri]') && !/Hesaplanıyor/.test(document.body.innerText)", 60000)
      await bekle(900)
    }],
    ['adalet-raporu', '/rapor/adalet', [1366, 1920]],
    ['puantaj', '/rapor/puantaj', [1366, 1920]],
    ['yedek-plani', '/rapor/yedek', [1366]],
    ['konaklama-raporu', '/konaklama/rapor', [1366]],
    ['denetim-raporu', '/degisiklik', [1366]],
    ['denetim-raporu-sonuc', '/degisiklik', [1366], async () => { await tikla(cdp, dugme('Raporu getir')); await bekle(1500) }],
    ['ayarlar', '/ayarlar', [1366, 1920]],
    ['ozel-kurallar', '/ozel-kurallar', [1366, 1920]],
    ['tanimlar', '/tanimlar', [1366]],
    ['tanimlar-gar-sureleri', '/tanimlar', [1366, 1920], async () => {
      await tikla(cdp, "[...document.querySelectorAll('[role=tab]')].find((t) => /Gar/i.test(t.textContent))")
      await bekle(900)
    }],
    ['personel-liste', '/makinistler', [1366, 1920]],
    ['personel-karti', '/makinistler', [1366, 1920], async () => {
      await tikla(cdp, "document.querySelector('table a[href*=\"/makinistler/\"]')")
      await bekle(1500)
    }],
    ['seferler', '/seferler', [1366]],
    ['sefer-yukle', '/seferler/yukle', [1366]],
    ['belgeler', '/belgeler', [1366, 1920]],
    ['izinler', '/izin', [1366]],
    ['takas-talepleri', '/takas', [1366]],
    ['konaklama', '/konaklama', [1366]],
  ]
  try {
    await cdp.gorunum(1366, 768)
    await giris(cdp, taban, kullanici, parola)
    for (const [ad, yol, genislikler, eylem] of SAHNELER) {
      for (const g of genislikler) {
        await cdp.gorunum(g, yukseklik(g))
        konsol.length = 0
        await git(cdp, taban, yol)
        if (eylem) await eylem()
        await cek(ad, g)
        if (TAM.has(ad)) writeFileSync(join(cikti, `${ad}-${g}-tam.png`), Buffer.from(await cdp.ekranGoruntusu(true), 'base64'))
      }
    }
    // (5) masa aday paneli 1920 ve 1366
    const adayAc = async () => {
      await git(cdp, taban, `/atama?tarih=${gunOtele(1)}`)
      await tikla(cdp, "document.querySelector('tr.sefer-satiri')")
      await cdp.bekleKosul("document.querySelectorAll('tr.aday').length > 0", 8000)
      await bekle(600)
    }
    await cdp.gorunum(1920, 1080)
    konsol.length = 0
    await adayAc()
    await cek('masa-aday-paneli', 1920, {
      panelGenislik: await cdp.degerlendir("Math.round(document.querySelector('.aday-kap').getBoundingClientRect().width)"),
      gorunenSefer: await cdp.degerlendir("[...document.querySelectorAll('tr.sefer-satiri')].filter((s) => { const r = s.getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight }).length"),
    })
    await cdp.degerlendir("document.querySelector('.aday-kap')?.scrollTo(0, 500)")
    await cek('masa-aday-paneli-kaydirilmis', 1920)
    await cdp.gorunum(1366, 768)
    await adayAc()
    await cek('masa-aday-paneli', 1366)
    // (7) masa Durum sütunu, gecikmeli satırla
    await git(cdp, taban, `/atama?tarih=${gunOtele(0)}`)
    await cdp.degerlendir("document.querySelector('.gecikme')?.closest('tr')?.scrollIntoView({ block: 'center' })")
    await bekle(300)
    await cek('masa-durum-sutunu', 1366, {
      durumSutunGenislik: await cdp.degerlendir("Math.round(document.querySelector('.masa-tablo th.kol-durum').getBoundingClientRect().width)"),
    })
    // (6) gece göstergesi: ayar değişince
    const api = await apiOturumu(apiTaban, kullanici, parola)
    const gecesayisi = () => cdp.degerlendir("document.querySelectorAll('tr.sefer-satiri [data-test=gece]').length")
    const ayarla = async (b, e) => {
      for (const [k, v] of [['gece.baslangic', b], ['gece.bitis', e]]) {
        const r = await api('PUT', `/api/parametre/${k}`, { deger: v, neden: 'Görsel kontrol: gece göstergesi' })
        if (!r.ok) throw new Error(`Ayar yazılamadı ${k} ${v}: ${r.status} ${(await r.text()).slice(0, 200)}`)
      }
    }
    const ozet = {}
    try {
      for (const [ad, b, e] of [['gece-20-06', '20:00', '06:00'], ['gece-23-04', '23:00', '04:00'], ['gece-18-07', '18:00', '07:00']]) {
        await ayarla(b, e)
        await git(cdp, taban, `/atama?tarih=${gunOtele(1)}`)
        const n = await gecesayisi()
        ozet[ad] = n
        await cek(`masa-${ad}`, 1366, { geceGostergesi: n, pencere: `${b}-${e}` })
      }
    } finally {
      await ayarla('20:00', '06:00')
    }
    console.log('GECE', JSON.stringify(ozet))
    writeFileSync(join(cikti, 'gece-ozet.json'), JSON.stringify(ozet, null, 2))
  } finally {
    await kapat()
  }
  writeFileSync(join(cikti, 'ef-olcum.json'), JSON.stringify(sonuc, null, 2))
}
main().catch((e) => {
  console.error(e.message)
  process.exit(2)
})
