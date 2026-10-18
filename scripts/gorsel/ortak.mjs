// scripts/gorsel ortak yardımcıları (W4): cek.mjs, cd.mjs, ef.mjs, son.mjs aynı giriş/gezinti/tıklama kodunu burada paylaşır.
// Bağımlılıksız (Node >= 22). Parola betiklerde değil ortam değişkenlerindedir (bkz. cek.mjs başlığı).

export const bekle = (ms) => new Promise((r) => setTimeout(r, ms))

/** Bugünden n gün sonrası, yerel gün (YYYY-MM-DD). */
export const gunOtele = (n) => {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d.toLocaleDateString('sv-SE')
}

/** `--ad değer` çiftleri (bayraksız; bayraklı kullanım için cek.mjs kendi ayrıştırıcısını taşır). */
export function oku(argv) {
  const a = {}
  for (let i = 2; i < argv.length; i += 2) {
    if (!argv[i].startsWith('--') || argv[i + 1] === undefined) throw new Error(`Bağımsız değişken hatası: ${argv[i]}`)
    a[argv[i].slice(2)] = argv[i + 1]
  }
  return a
}

/** Açık giriş formunu doldurup gönderir (sonucu beklemez; hatalı giriş sahneleri bunu kullanır). */
export async function girisDoldur(cdp, kullanici, parola) {
  await cdp.bekleKosul("!!document.querySelector('#eposta')")
  await cdp.degerlendir(`(() => {
    const doldur = (sec, v) => {
      const el = document.querySelector(sec)
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v)
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    doldur('#eposta', ${JSON.stringify(kullanici)})
    doldur('#parola', ${JSON.stringify(parola)})
    document.querySelector('form button[type=submit]').click()
  })()`)
}

/** Giriş sayfasına gider, formu doldurup gönderir; oturum açılana dek bekler. */
export async function giris(cdp, taban, kullanici, parola) {
  await cdp.gonder('Page.navigate', { url: `${taban}/#/giris` })
  if (!(await cdp.bekleKosul("!!document.querySelector('#eposta')"))) throw new Error('Giriş formu açılmadı')
  await girisDoldur(cdp, kullanici, parola)
  if (!(await cdp.bekleKosul("!location.hash.startsWith('#/giris')", 15000))) {
    throw new Error('Giriş başarısız (kullanıcı/parola ya da sunucu)')
  }
}

/**
 * Her çekim temiz yüklemedir (boş sayfadan): görünüm genişliğine bağlı ilk durum (ör. menü daraltma) doğru kurulur, durum sızmaz.
 * Yükleniyor göstergeleri bitene dek (en çok 8 sn) bekler, sonra çizim için `bekleMs` kadar durur.
 */
export async function git(cdp, taban, yol, bekleMs = 1000) {
  await cdp.gonder('Page.navigate', { url: 'about:blank' })
  await cdp.gonder('Page.navigate', { url: `${taban}/#${yol}` })
  await cdp.bekleKosul("document.readyState === 'complete'")
  await cdp.bekleKosul('!/Yükleniyor|Hesaplanıyor|hazırlanıyor/.test(document.body.innerText)', 8000)
  await bekle(bekleMs)
}

/** Sayfada `ifade` ile bulunan öğeye tıklar; bulunamazsa false. */
export const tikla = (cdp, ifade) =>
  cdp.degerlendir(`(() => { const e = ${ifade}; if (!e) return false; e.click(); return true })()`)

/** Metni birebir `m` olan düğme/bağlantı için ifade (tikla'ya verilir). */
export const dugme = (m) =>
  `[...document.querySelectorAll('button,a.btn')].find((b) => b.textContent.trim() === ${JSON.stringify(m)})`

/** Metni `m` içeren düğme/bağlantı için ifade. */
export const dugmeIcerir = (m) =>
  `[...document.querySelectorAll('button,a.btn')].find((b) => b.textContent.includes(${JSON.stringify(m)}))`
