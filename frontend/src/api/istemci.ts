import type { Hata, Ihlal } from './tipler'

/** ProblemDetail + kod çevirisi. status 0 = sunucuya hiç ulaşılamadı. */
export class ApiHata extends Error {
  readonly status: number
  readonly kod: string
  readonly detail: string
  readonly alanlar?: Record<string, string>
  readonly ihlaller?: Ihlal[]
  /** Bazı 409'larda sunucunun döndüğü güncel kayıt (ör. OZET_DEGISTI → güncel görev). */
  readonly gorev?: unknown

  constructor(
    status: number,
    kod: string,
    detail: string,
    ekstra: Pick<Hata, 'alanlar' | 'ihlaller' | 'gorev'> = {},
  ) {
    super(detail)
    this.name = 'ApiHata'
    this.status = status
    this.kod = kod
    this.detail = detail
    this.alanlar = ekstra.alanlar
    this.ihlaller = ekstra.ihlaller
    this.gorev = ekstra.gorev
  }

  get cakismaMi(): boolean {
    return this.kod === 'VERSIYON_CAKISMASI'
  }
}

// Yanıt (başlıklar) bu sürede gelmezse istek iptal edilir ve AG_HATASI olur: yarı açık bağlantı arayüzü kilitlemesin.
const ZAMAN_ASIMI_MS = 30_000
const YUKLEME_ZAMAN_ASIMI_MS = 300_000 // dosya yükleme (Excel)
const INDIRME_ZAMAN_ASIMI_MS = 120_000 // Excel üretimi + indirme
/** Sunucuda uzun süren hesaplar (otomatik atama önizle/uygula, kural dene): sunucu tavanı 30 sn, yük altında bunun üstüne çıkabilir. */
export const UZUN_ISLEM_ZAMAN_ASIMI_MS = 120_000

// 401 bu yollarda "oturum yok / hatalı giriş" bilgisidir, yönlendirme tetiklemez.
const SESSIZ_401 = ['/api/auth/ben', '/api/auth/giris']

let yetkisizIsleyici: (() => void) | null = null
/** 401 gelince (oturum düştü) ne yapılacağı main.ts'te bağlanır: istemci router'ı bilmez. */
export function yetkisizIsleyiciKaydet(isleyici: (() => void) | null): void {
  yetkisizIsleyici = isleyici
}

function xsrfToken(): string | null {
  const cerez = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cerez ? decodeURIComponent(cerez.slice('XSRF-TOKEN='.length)) : null
}

async function hataOku(yanit: Response): Promise<ApiHata> {
  try {
    const g = JSON.parse(await yanit.text()) as Partial<Hata>
    if (typeof g.kod === 'string') {
      return new ApiHata(yanit.status, g.kod, g.detail ?? 'İşlem başarısız oldu.', {
        alanlar: g.alanlar,
        ihlaller: Array.isArray(g.ihlaller) ? g.ihlaller : undefined,
        gorev: g.gorev,
      })
    }
  } catch {
    // JSON değil (ters vekil/proxy hatası): aşağıdaki genel hata.
  }
  return new ApiHata(yanit.status, 'SUNUCU_HATASI', 'Beklenmeyen bir hata oluştu. Tekrar deneyin.')
}

/** Ortak istek hattı: CSRF başlığı, ağ/401/ProblemDetail hata eşlemesi. Başarılı yanıtı olduğu gibi döner. */
async function yanitAl(
  yontem: string,
  yol: string,
  govde?: unknown,
  kabul = 'application/json',
  zamanAsimiMs = ZAMAN_ASIMI_MS,
): Promise<Response> {
  const basliklar: Record<string, string> = { Accept: kabul }
  // FormData (dosya yükleme): Content-Type'ı (sınır dizesiyle) tarayıcı koyar, elle yazılırsa sınır kaybolur.
  const formMu = govde instanceof FormData
  // Blob/File (ham dosya gövdesi, ör. kurum logosu): olduğu gibi gider, içerik türü dosyanınkidir.
  const hamMu = govde instanceof Blob
  if (hamMu && govde.type) basliklar['Content-Type'] = govde.type
  else if (govde !== undefined && !formMu && !hamMu) basliklar['Content-Type'] = 'application/json'
  if (yontem !== 'GET') {
    const token = xsrfToken()
    if (token) basliklar['X-XSRF-TOKEN'] = token
  }

  let yanit: Response
  const denetci = new AbortController()
  const zamanlayici = setTimeout(() => denetci.abort(), zamanAsimiMs)
  try {
    yanit = await fetch(yol, {
      method: yontem,
      headers: basliklar,
      body: govde === undefined ? undefined : formMu || hamMu ? govde : JSON.stringify(govde),
      signal: denetci.signal,
    })
  } catch {
    // Otomatik yeniden deneme YOK (hiçbir yöntemde): zaman aşımından sonra sunucu yazma işlemini uygulamış olabilir, POST tekrarı çift
    // kayıt yaratır. Hata yalnız kullanıcıya gösterilir; tekrar kararı onundur.
    throw new ApiHata(
      0,
      'AG_HATASI',
      denetci.signal.aborted
        ? yontem === 'GET'
          ? 'Sunucu zamanında yanıt vermedi. Bağlantınızı kontrol edip tekrar deneyin.'
          : 'Sunucu zamanında yanıt vermedi. İşlem sunucuda uygulanmış olabilir: tekrar denemeden önce kaydı kontrol edin.'
        : 'Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.',
    )
  } finally {
    clearTimeout(zamanlayici)
  }

  if (yanit.ok) return yanit
  const hata = await hataOku(yanit)
  if (yanit.status === 401 && !SESSIZ_401.some((p) => yol.startsWith(p))) yetkisizIsleyici?.()
  throw hata
}

export async function istek<T>(
  yontem: string,
  yol: string,
  govde?: unknown,
  zamanAsimiMs?: number,
): Promise<T> {
  const yanit = await yanitAl(
    yontem,
    yol,
    govde,
    'application/json',
    zamanAsimiMs ?? (govde instanceof FormData ? YUKLEME_ZAMAN_ASIMI_MS : ZAMAN_ASIMI_MS),
  )
  return (yanit.status === 204 ? undefined : await yanit.json()) as T
}

/** `istek` + HTTP durumu: aynı uç hem 201 (yeni) hem 200 (mevcut kayıt döndü) verebiliyorsa ayrım durumdan okunur. */
export async function istekDurumlu<T>(
  yontem: string,
  yol: string,
  govde?: unknown,
): Promise<{ durum: number; veri: T }> {
  const yanit = await yanitAl(yontem, yol, govde)
  return {
    durum: yanit.status,
    veri: (yanit.status === 204 ? undefined : await yanit.json()) as T,
  }
}

/** Content-Disposition başlığından dosya adı: önce filename*=UTF-8''... (Türkçe karakter), yoksa filename=. */
export function dosyaAdiOku(baslik: string | null): string | null {
  if (!baslik) return null
  const utf8 = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(baslik)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1]!.trim())
    } catch {
      // bozuk yüzde kodlaması: düz filename'e düş
    }
  }
  const duz = /filename\s*=\s*(?:"([^"]*)"|([^;]+))/i.exec(baslik)
  const ad = (duz?.[1] ?? duz?.[2] ?? '').trim()
  return ad === '' ? null : ad
}

/** Dosya indirme (xlsx): bayt + sunucunun verdiği ad. Hata gövdesi JSON ProblemDetail'dir (ApiHata olarak fırlar). */
export async function indir(yol: string): Promise<{ blob: Blob; ad: string | null }> {
  const yanit = await yanitAl('GET', yol, undefined, '*/*', INDIRME_ZAMAN_ASIMI_MS)
  return {
    blob: await yanit.blob(),
    ad: dosyaAdiOku(yanit.headers.get('Content-Disposition')),
  }
}

/** POST ile dosya indirme (xlsx): gövde JSON; aynı hata/zaman aşımı hattı. */
export async function indirPost(
  yol: string,
  govde: unknown,
): Promise<{ blob: Blob; ad: string | null }> {
  const yanit = await yanitAl('POST', yol, govde, '*/*', INDIRME_ZAMAN_ASIMI_MS)
  return {
    blob: await yanit.blob(),
    ad: dosyaAdiOku(yanit.headers.get('Content-Disposition')),
  }
}

export const api = {
  get: <T>(yol: string) => istek<T>('GET', yol),
  /** `zamanAsimiMs`: uzun hesaplar için (varsayılan 30 sn, dosya yüklemede 5 dk). */
  post: <T = void>(yol: string, govde?: unknown, zamanAsimiMs?: number) =>
    istek<T>('POST', yol, govde, zamanAsimiMs),
  put: <T = void>(yol: string, govde?: unknown) => istek<T>('PUT', yol, govde),
  patch: <T = void>(yol: string, govde?: unknown) => istek<T>('PATCH', yol, govde),
  sil: <T = void>(yol: string) => istek<T>('DELETE', yol),
}

/** `?a=1&b=x`; boş/undefined/null değerler atılır. */
export function sorguMetni(p: Record<string, string | number | null | undefined>): string {
  const s = new URLSearchParams()
  for (const [anahtar, deger] of Object.entries(p)) {
    if (deger !== undefined && deger !== null && deger !== '') s.set(anahtar, String(deger))
  }
  const metin = s.toString()
  return metin ? `?${metin}` : ''
}
