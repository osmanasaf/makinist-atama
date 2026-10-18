// Chrome DevTools Protocol yardımcısı (bağımlılıksız; Node >= 22: yerleşik WebSocket ve fetch).
// Başsız Chrome'u geçici profille açar, tek sayfa hedefine bağlanır. Kullanım: cek.mjs (görsel kontrol betiği).
import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const bekle = (ms) => new Promise((r) => setTimeout(r, ms))

const VARSAYILAN_YOLLAR = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
]

/** Chrome yolu: CHROME_YOLU ortam değişkeni, yoksa bilinen kurulum yerleri. */
export function chromeYolu() {
  const yol = process.env.CHROME_YOLU ?? VARSAYILAN_YOLLAR.find((y) => existsSync(y))
  if (!yol) throw new Error('Chrome bulunamadı: CHROME_YOLU ortam değişkenini ayarlayın.')
  return yol
}

export class Cdp {
  #ws
  #id = 0
  #bekleyen = new Map()
  #dinleyiciler = []

  constructor(ws) {
    this.#ws = ws
    ws.addEventListener('message', (m) => {
      const ileti = JSON.parse(m.data)
      if (ileti.id !== undefined) {
        const b = this.#bekleyen.get(ileti.id)
        this.#bekleyen.delete(ileti.id)
        if (!b) return
        if (ileti.error) b.red(new Error(`${b.yontem}: ${ileti.error.message}`))
        else b.coz(ileti.result)
      } else {
        for (const d of this.#dinleyiciler) d(ileti.method, ileti.params)
      }
    })
  }

  gonder(yontem, parametreler = {}) {
    const id = ++this.#id
    return new Promise((coz, red) => {
      this.#bekleyen.set(id, { coz, red, yontem })
      this.#ws.send(JSON.stringify({ id, method: yontem, params: parametreler }))
    })
  }

  /** Olay dinleyici: (yontem, parametreler) => void. */
  dinle(fn) {
    this.#dinleyiciler.push(fn)
  }

  /** Sayfada ifade çalıştırır (await desteklenir); değeri döndürür. */
  async degerlendir(ifade) {
    const s = await this.gonder('Runtime.evaluate', {
      expression: ifade,
      awaitPromise: true,
      returnByValue: true,
    })
    if (s.exceptionDetails) throw new Error(s.exceptionDetails.exception?.description ?? 'degerlendir hatası')
    return s.result.value
  }

  /** Koşul sağlanana dek (ms zaman aşımı) sayfada ifadeyi yoklar. */
  async bekleKosul(ifade, zamanAsimi = 10000) {
    const son = Date.now() + zamanAsimi
    while (Date.now() < son) {
      if (await this.degerlendir(ifade)) return true
      await bekle(100)
    }
    return false
  }

  /** Gerçek klavye tuşu (Tab, Enter...): :focus-visible klavye kipinde değerlendirilir. */
  async tus(anahtar, kod, vk) {
    const ortak = { key: anahtar, code: kod, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk }
    await this.gonder('Input.dispatchKeyEvent', { type: 'keyDown', ...ortak })
    await this.gonder('Input.dispatchKeyEvent', { type: 'keyUp', ...ortak })
  }


  /**
   * /api isteklerine (yalnız yolu /api/ ile başlayanlar) Fetch ile müdahale (yükleniyor / sunucu yok / veri yok sahneleri). `kural` çağıranda değiştirilebilir bir nesnedir:
   *   tut: RegExp  -> eşleşen istek (istek aşamasında) birak() çağrılana dek tutulur
   *   dusur: RegExp -> eşleşen istek ağ hatasıyla düşürülür (sunucu yok)
   *   bosalt: RegExp -> eşleşen GET yanıtı donustur(json, url) ile değiştirilir (veri yok)
   * Dönen nesne: { birak() tutulanları sürdürür, tutulan() tutulan istek sayısı }.
   */
  async ara(kural) {
    let tutulan = []
    await this.gonder('Fetch.enable', {
      patterns: [
        { urlPattern: '*/api/*', requestStage: 'Request' },
        { urlPattern: '*/api/*', requestStage: 'Response' },
      ],
    })
    const sessiz = (p) => p.catch(() => {}) // sayfa gezinmesiyle iptal olmuş istek: önemsiz
    this.dinle((yontem, p) => {
      if (yontem !== 'Fetch.requestPaused') return
      const { requestId, request } = p
      const u = new URL(request.url)
      if (!u.pathname.startsWith('/api/')) return void sessiz(this.gonder('Fetch.continueRequest', { requestId })) // Vite kaynakları (/src/api/...) dışarıda
      const url = u.pathname + u.search // kurallar yol + sorgu üzerinde (köken yok)
      if (p.responseStatusCode === undefined && !p.responseErrorReason) {
        if (kural.dusur?.test(url)) return void sessiz(this.gonder('Fetch.failRequest', { requestId, errorReason: 'ConnectionRefused' }))
        if (kural.tut?.test(url)) return void tutulan.push(requestId)
        return void sessiz(this.gonder('Fetch.continueRequest', { requestId }))
      }
      if (kural.bosalt?.test(url) && request.method === 'GET' && p.responseStatusCode < 400) {
        return void sessiz(
          (async () => {
            const g = await this.gonder('Fetch.getResponseBody', { requestId })
            const metin = g.base64Encoded ? Buffer.from(g.body, 'base64').toString('utf8') : g.body
            const yeni = JSON.stringify(kural.donustur(JSON.parse(metin), url))
            const basliklar = (p.responseHeaders ?? []).filter((h) => !/^content-(encoding|length)$/i.test(h.name))
            await this.gonder('Fetch.fulfillRequest', {
              requestId,
              responseCode: p.responseStatusCode,
              responseHeaders: basliklar,
              body: Buffer.from(yeni, 'utf8').toString('base64'),
            })
          })(),
        )
      }
      sessiz(this.gonder('Fetch.continueRequest', { requestId }))
    })
    return {
      tutulan: () => tutulan.length,
      birak: async () => {
        const ids = tutulan
        tutulan = []
        for (const requestId of ids) await sessiz(this.gonder('Fetch.continueRequest', { requestId }))
      },
    }
  }

  /** Görünüm alanı boyutu (genislik, yukseklik, cihazOlcegi). */
  gorunum(genislik, yukseklik, cihazOlcegi = 1) {
    return this.gonder('Emulation.setDeviceMetricsOverride', {
      width: genislik,
      height: yukseklik,
      deviceScaleFactor: cihazOlcegi,
      mobile: genislik < 500,
    })
  }

  /** PNG kaydı verir (base64). tam=true: sayfanın tüm yüksekliği. */
  async ekranGoruntusu(tam = false) {
    const parametreler = { format: 'png' }
    if (tam) {
      const m = await this.gonder('Page.getLayoutMetrics')
      const { width, height } = m.cssContentSize
      Object.assign(parametreler, {
        captureBeyondViewport: true,
        clip: { x: 0, y: 0, width, height, scale: 1 },
      })
    }
    return (await this.gonder('Page.captureScreenshot', parametreler)).data
  }
}

/** Başsız Chrome'u açar ve bağlı Cdp + kapat() döner. */
export async function baslat({ port = 9333, chrome = chromeYolu() } = {}) {
  const profil = mkdtempSync(join(tmpdir(), 'gorsel-'))
  const surec = spawn(
    chrome,
    [
      '--headless=new',
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profil}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-gpu',
      'about:blank',
    ],
    { stdio: 'ignore' },
  )
  const kapat = async () => {
    if (process.platform === 'win32') spawnSync('taskkill', ['/pid', String(surec.pid), '/T', '/F'], { stdio: 'ignore' })
    else surec.kill()
    await bekle(500)
    try {
      rmSync(profil, { recursive: true, force: true })
    } catch {
      // profil klasörü hemen silinemedi (Chrome dosya tutuyor olabilir): geçici dizindedir, sistem temizler
    }
  }
  try {
    let hedef
    for (let i = 0; i < 100 && !hedef; i++) {
      try {
        const liste = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()
        hedef = liste.find((h) => h.type === 'page')
      } catch {
        await bekle(100)
      }
    }
    if (!hedef) throw new Error('Chrome hata ayıklama bağlantısı açılamadı')
    const ws = new WebSocket(hedef.webSocketDebuggerUrl)
    await new Promise((coz, red) => {
      ws.addEventListener('open', coz)
      ws.addEventListener('error', () => red(new Error('WebSocket bağlanamadı')))
    })
    const cdp = new Cdp(ws)
    await cdp.gonder('Page.enable')
    await cdp.gonder('Runtime.enable')
    await cdp.gonder('Log.enable')
    return { cdp, kapat }
  } catch (e) {
    await kapat()
    throw e
  }
}
