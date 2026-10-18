import { api } from '@/api/istemci'

export type ParametreTipi = 'SAYI' | 'SAAT_GUNU' | 'METIN'
export type Guvenilirlik = 'YUKSEK' | 'ORTA' | 'DUSUK'
export type KuralSiddeti = 'ENGEL' | 'UYARI' | 'KAPALI'

export interface ParametreDto {
  anahtar: string
  /** Okunur ad (sunucu): ekranda ve denetim raporunda anahtar yerine bu görünür. */
  ad: string
  deger: string
  tip: ParametreTipi
  altSinir: number | null
  ustSinir: number | null
  birim: string
  aciklama: string
  kaynak: string
  guvenilirlik: Guvenilirlik
  varsayilan: string
  grup: string
  sira: number
}

export interface KuralSiddetDto {
  kod: string
  ad: string
  siddet: KuralSiddeti
  aciklama: string
  kaynak: string
  guvenilirlik: Guvenilirlik
  varsayilan: KuralSiddeti
  grup: string
  sira: number
}

/** Uygulama ayarı (yalnız yönetici okur): kaynak/güvenilirlik taşımaz. */
export interface AyarDto {
  anahtar: string
  ad: string
  deger: number
  birim: string
  alt: number
  ust: number
  aciklama: string
  varsayilan: number
  grup: string
  sira: number
}

export const SIDDET_ETIKETLERI: Record<KuralSiddeti, string> = {
  ENGEL: 'Engel',
  UYARI: 'Uyarı',
  KAPALI: 'Kapalı',
}

export const GUVENILIRLIK_ETIKETLERI: Record<Guvenilirlik, string> = {
  YUKSEK: 'Yüksek',
  ORTA: 'Orta',
  DUSUK: 'Düşük',
}

/** Ekran sırasıyla grup kodu → başlık. Bilinmeyen kod gelirse kodun kendisi başlık olur ve sona konur. */
export const GRUPLAR: readonly { kod: string; baslik: string }[] = [
  { kod: 'CALISMA', baslik: 'Çalışma ve dinlenme' },
  { kod: 'GECE', baslik: 'Gece çalışması' },
  { kod: 'BELGE', baslik: 'Belgeler' },
  { kod: 'PUANTAJ', baslik: 'Puantaj' },
  { kod: 'TAKAS_IZIN', baslik: 'Takas ve izin' },
  { kod: 'YOLCULUK', baslik: 'Yolculuk ve konaklama' },
  { kod: 'SEFER', baslik: 'Seferler ve Excel' },
  { kod: 'OTOMATIK', baslik: 'Otomatik atama ve adalet' },
  { kod: 'KADRO', baslik: 'Kadro' },
  { kod: 'PERSONEL', baslik: 'Diğer personel' },
  { kod: 'MAKINIST', baslik: 'Makinist görünümü' },
]

export const parametreApi = {
  parametreler: () => api.get<ParametreDto[]>('/api/parametre'),
  kurallar: () => api.get<KuralSiddetDto[]>('/api/kural'),
  ayarlar: () => api.get<AyarDto[]>('/api/ayar'),
  parametreKaydet: (anahtar: string, govde: { deger: string; neden: string }) =>
    api.put<ParametreDto>(`/api/parametre/${encodeURIComponent(anahtar)}`, govde),
  kuralKaydet: (kod: string, govde: { siddet: KuralSiddeti; neden: string }) =>
    api.put<KuralSiddetDto>(`/api/kural/${encodeURIComponent(kod)}/siddet`, govde),
  ayarKaydet: (anahtar: string, govde: { deger: number; neden: string }) =>
    api.put<AyarDto>(`/api/ayar/${encodeURIComponent(anahtar)}`, govde),
}

// ---- Birleşik satır modeli ------------------------------------------------

export type SatirTuru = 'SIDDET' | 'PARAMETRE' | 'AYAR'

/** Ekranın tek satır tipi: kural şiddeti, parametre ya da uygulama ayarı. Değerler metindir (düzenleme taslağıyla aynı biçim). */
export interface AyarSatiri {
  tur: SatirTuru
  /** Kural kodu (K-14) ya da parametre/ayar anahtarı. */
  anahtar: string
  /** Okunur ad; ekranda satır başlığıdır (anahtar yalnız kural kodu olarak küçük görünür). */
  ad: string
  aciklama: string
  birim: string
  deger: string
  varsayilan: string
  alt: number | null
  ust: number | null
  kaynak: string
  /** Uygulama ayarında yok (kaynak "[V] yerel ayar"). */
  guvenilirlik: Guvenilirlik | null
  /** Birim '0/1': açma/kapama onay kutusu. */
  bayrak: boolean
  sayisal: boolean
  grup: string
  sira: number
}

export const YEREL_AYAR_KAYNAGI = 'Uygulama ayarı'

const KAYNAK_ETIKETLERI: Record<string, string> = {
  Y: 'Yasa/yönetmelik',
  T: 'Toplu iş sözleşmesi',
  İ: 'TCDD genel müdürlük emri',
  E: 'Mülga yönetmelik',
  V: 'Varsayım',
}

/** Kaynak metnindeki `[Y]`, `[T]`, `[İ]`, `[E]`, `[V]` kısaltmalarını açık yazar ("[Y] 4857 md. 63" → "Yasa/yönetmelik: 4857 md. 63"). */
export function kaynakMetni(kaynak: string): string {
  return kaynak
    .replace(/\[V\]\s*kaynaksız/g, 'Kaynaksız')
    .replace(/\[(Y|T|İ|E|V)\]\s*/g, (_, k: string) => `${KAYNAK_ETIKETLERI[k]}: `)
}

/**
 * Değer eşitliği TİPİNE göre: sayısal satırlarda sayı eşitliği ("0.1" == "0.10" == "0,1"), diğerlerinde kırpılmış metin.
 * "Varsayılandan farklı" göstergesi ve "değişti" denetimi metin karşılaştırmasına dayanırsa kanonik olmayan tohum ("0.10") hep farklı görünür.
 */
export function degerEsit(sayisal: boolean, a: string, b: string): boolean {
  if (sayisal) {
    const x = a.trim().replace(',', '.')
    const y = b.trim().replace(',', '.')
    if (x !== '' && y !== '' && Number.isFinite(Number(x)) && Number.isFinite(Number(y))) {
      return Number(x) === Number(y)
    }
  }
  return a.trim() === b.trim()
}

export const satirAnahtari = (s: Pick<AyarSatiri, 'tur' | 'anahtar'>) => `${s.tur}:${s.anahtar}`

export const kuralSatiri = (d: KuralSiddetDto): AyarSatiri => ({
  tur: 'SIDDET',
  anahtar: d.kod,
  ad: d.ad,
  aciklama: d.aciklama,
  birim: '',
  deger: d.siddet,
  varsayilan: d.varsayilan,
  alt: null,
  ust: null,
  kaynak: d.kaynak,
  guvenilirlik: d.guvenilirlik,
  bayrak: false,
  sayisal: false,
  grup: d.grup,
  sira: d.sira,
})

/** Sunucu kataloğu bu parametreyi "Çalışma ve dinlenme" grubuna koyuyor; ekranda ait olduğu yerde (Seferler ve Excel) gösterilir. Kalıcı çözüm katalogdadır (V29); bu geçici ön yüz eşlemesidir. */
const GRUP_DUZELTMESI: Record<string, string> = { 'sefer.parca_azami_saat': 'SEFER' }
/** Eşleme iki kaynağa da uygulanır: parametre (/api/parametre) ve yerel ayar (/api/ayar); `sefer.parca_azami_saat` gerçekte ikincisinden gelir. */
const grupDuzelt = (anahtar: string, grup: string) => GRUP_DUZELTMESI[anahtar] ?? grup

export const parametreSatiri = (d: ParametreDto): AyarSatiri => ({
  tur: 'PARAMETRE',
  anahtar: d.anahtar,
  ad: d.ad,
  aciklama: d.aciklama,
  birim: d.birim,
  deger: d.deger,
  varsayilan: d.varsayilan,
  alt: d.altSinir,
  ust: d.ustSinir,
  kaynak: d.kaynak,
  guvenilirlik: d.guvenilirlik,
  bayrak: d.birim === '0/1',
  sayisal: d.tip === 'SAYI',
  grup: grupDuzelt(d.anahtar, d.grup),
  sira: d.sira,
})

export const ayarSatiri = (d: AyarDto): AyarSatiri => ({
  tur: 'AYAR',
  anahtar: d.anahtar,
  ad: d.ad,
  aciklama: d.aciklama,
  birim: d.birim,
  deger: String(d.deger),
  varsayilan: String(d.varsayilan),
  alt: d.alt,
  ust: d.ust,
  kaynak: YEREL_AYAR_KAYNAGI,
  guvenilirlik: null,
  bayrak: d.birim === '0/1',
  sayisal: true,
  grup: grupDuzelt(d.anahtar, d.grup),
  sira: d.sira,
})

const grupSirasi = (kod: string) => {
  const i = GRUPLAR.findIndex((g) => g.kod === kod)
  return i < 0 ? GRUPLAR.length : i
}

/** Üç kaynağı tek listeye katar: grup sırası (bilinmeyen kodlar sonda, kod sırasıyla), şiddetler önce, sonra sira (eşitlikte anahtar). */
export function birlestir(
  kurallar: KuralSiddetDto[],
  parametreler: ParametreDto[],
  ayarlar: AyarDto[],
): AyarSatiri[] {
  return [
    ...kurallar.map(kuralSatiri),
    ...parametreler.map(parametreSatiri),
    ...ayarlar.map(ayarSatiri),
  ].sort(
    (a, b) =>
      grupSirasi(a.grup) - grupSirasi(b.grup) ||
      (a.grup < b.grup ? -1 : a.grup > b.grup ? 1 : 0) ||
      Number(a.tur !== 'SIDDET') - Number(b.tur !== 'SIDDET') ||
      a.sira - b.sira ||
      a.anahtar.localeCompare(b.anahtar),
  )
}

export interface AyarGrubu {
  kod: string
  baslik: string
  siddetler: AyarSatiri[]
  degerler: AyarSatiri[]
  /** Ekran sırası: ilgili parametresi olan kural şiddeti, parametresinin hemen üstünde; diğer şiddetler grubun başında. */
  satirlar: AyarSatiri[]
}

/** Kural şiddeti → şiddetin açıp kapattığı ayarın ilk satırı (ekranda yan yana durur). */
export const ILISKILI_AYAR: Record<string, string> = {
  'K-07': 'hat.ara_max_ay',
  'K-08': 'brove.surus_ara_max_ay',
  'K-11': 'dinlenme.ev_saat',
  'K-12': 'dinlenme.dis_merkez_saat',
  'K-13': 'gunluk.max_saat',
  'K-14': 'haftalik.normal_saat',
  'K-15': 'yillik.fazla_mesai_max_saat',
  'K-16': 'gece.max_saat',
  'K-18': 'gece.ard_hafta_max',
  'K-19': 'hafta_tatili.min_saat',
  'K-21': 'sefer.max_saat',
  'K-23': 'nobet.asgari_aktarma_dk',
}

function ekranSirasi(g: Pick<AyarGrubu, 'siddetler' | 'degerler'>): AyarSatiri[] {
  const hedefler = new Set(g.degerler.map((d) => d.anahtar))
  const bagli = (k: AyarSatiri) => hedefler.has(ILISKILI_AYAR[k.anahtar] ?? '')
  return [
    ...g.siddetler.filter((k) => !bagli(k)),
    ...g.degerler.flatMap((d) => [
      ...g.siddetler.filter((k) => ILISKILI_AYAR[k.anahtar] === d.anahtar),
      d,
    ]),
  ]
}

/** `birlestir` çıktısını (sıralı) başlıklı gruplara böler; bilinmeyen grup kodu kendi başlığıyla sona düşer. */
export function gruplaYap(satirlar: AyarSatiri[]): AyarGrubu[] {
  const gruplar: AyarGrubu[] = []
  for (const s of satirlar) {
    let g = gruplar.find((x) => x.kod === s.grup)
    if (!g) {
      g = {
        kod: s.grup,
        baslik: GRUPLAR.find((x) => x.kod === s.grup)?.baslik ?? s.grup,
        siddetler: [],
        degerler: [],
        satirlar: [],
      }
      gruplar.push(g)
    }
    ;(s.tur === 'SIDDET' ? g.siddetler : g.degerler).push(s)
  }
  for (const g of gruplar) g.satirlar = ekranSirasi(g)
  return gruplar
}

// ---- Bağlamsal uyarılar (kaydı ENGELLEMEZ; sunucu sözleşmesi büyümez) --------

export interface AyarUyarisi {
  kod: string
  duzey: 'UYARI' | 'BILGI'
  metin: string
}

const dakika = (hhmm: string | undefined): number | null => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm?.trim() ?? '')
  return m ? Number(m[1]) * 60 + Number(m[2]) : null
}

export function ayarUyarilari(satirlar: AyarSatiri[]): AyarUyarisi[] {
  const deger = (a: string) => satirlar.find((s) => s.anahtar === a)?.deger
  const sayi = (a: string) => {
    const d = deger(a)
    return d === undefined || d.trim() === '' || Number.isNaN(Number(d)) ? null : Number(d)
  }
  const siddet = (kod: string) =>
    satirlar.find((s) => s.tur === 'SIDDET' && s.anahtar === kod)?.deger
  const uyarilar: AyarUyarisi[] = []

  const puantajBitis = dakika(deger('puantaj.gece_bitis'))
  const geceBitis = dakika(deger('gece.bitis'))
  if (puantajBitis !== null && geceBitis !== null && puantajBitis < geceBitis) {
    uyarilar.push({
      kod: 'PUANTAJ_GECE_BITIS',
      duzey: 'UYARI',
      metin: `Puantajda gece bitişi (${deger('puantaj.gece_bitis')}), gece çalışması kural penceresinin bitişinden (${deger('gece.bitis')}) erken. Kural penceresinin son kısmı puantajda gece sayılmaz.`,
    })
  }

  const aktarma = sayi('yolculuk.aktarma_dk')
  const asgari = sayi('nobet.asgari_aktarma_dk')
  const k23 = siddet('K-23')
  if (
    aktarma !== null &&
    asgari !== null &&
    aktarma > asgari &&
    k23 !== undefined &&
    k23 !== 'KAPALI'
  ) {
    uyarilar.push({
      kod: 'YOLCULUK_AKTARMA',
      duzey: 'UYARI',
      metin: `Yolculuk aktarma süresi (${aktarma} dk), nöbet içi asgari aktarma süresinden (${asgari} dk) büyük. K-23 yolculuk geçişlerine uygulanmaz; yolculuk geçişlerinde ${aktarma} dk aranır.`,
    })
  }

  const normal = sayi('haftalik.normal_saat')
  const azami = sayi('haftalik.max_saat')
  if (normal !== null && azami !== null && normal > azami && siddet('K-14') === 'KAPALI') {
    uyarilar.push({
      kod: 'HAFTALIK_ASGARI',
      duzey: 'BILGI',
      metin: `Haftalık üst sınır (${azami} saat) K-14 kapalıyken kullanılmaz; normal haftalık süreyi (${normal} saat) aşan çalışma fazla mesai sayılır.`,
    })
  }
  return uyarilar
}
