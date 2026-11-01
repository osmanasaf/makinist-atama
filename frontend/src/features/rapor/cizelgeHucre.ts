// Aylık çizelge hücre mantığı (F5, MK-179): SAF (Vue import etmez). Sunucu hücresini ekran verisine çevirir: kısa kod, işaretler ve erişilebilir ad.
// Hücre anlamı yalnız renkle taşınmaz (AK-5.7): izin "İ"/"R"/"D", yolculuk "Y" öneki, gece simge + metin, kontrol bekleyen simge + metin, tatil başlıkta ad.
import { ROL_ETIKETLERI } from '@/features/atama/atamaApi'
import type { IzinTuru } from '@/features/makinist/makinistApi'
import type { CizelgeHucre } from './cizelgeApi'

export const IZIN_KODLARI: Record<IzinTuru, { kod: string; ad: string }> = {
  YILLIK: { kod: 'İ', ad: 'yıllık' },
  RAPOR: { kod: 'R', ad: 'rapor' },
  DIGER: { kod: 'D', ad: 'diğer izin' },
}

/** Sunucudaki serbest intikal etiketi (`AtamaSatiri.INTIKAL_ETIKETI`); hücrede "İnt" olarak kısalır. */
const INTIKAL_ETIKETI = 'İntikal'

export interface HucreGorunumu {
  tur: 'izin' | 'gorev' | 'yolculuk'
  /** Hücrenin kısa kodu: tren no, "Y T4" (yolculuk), "İ" (izin). */
  kod: string
  /** İlk görevin kalkış saati ("06:00"); izinde boş. */
  saat: string
  /** İlk görevden sonraki görev sayısı ("+N"). */
  ek: number
  gece: boolean
  kirmizi: boolean
  /** Hem izin hem görev (nadir): izin kodu işareti. */
  izinKodu: string | null
  /** Derin bağlantı için ilk görevin sefer kimliği; izin ve serbest intikalde yok. */
  seferId: number | null
  /** Erişilebilir ad: kişi, gün, her görev (tren, saat, rol, yolculuk, gece, kontrol) ve izin/tatil. */
  ad: string
}

const kisaKod = (trenNo: string) => (trenNo === INTIKAL_ETIKETI ? 'İnt' : trenNo)

/** Hücre yoksa (görevsiz, izinsiz gün) görünüm yok. `tatil`: o günün resmi tatil adı (yalnız adda). */
export function hucreGorunumu(
  h: CizelgeHucre | undefined,
  personel: string,
  gun: string,
  tatil?: string,
): HucreGorunumu | null {
  if (!h) return null
  const gorevler = h.gorevler ?? []
  const ilk = gorevler[0]
  const izin = h.izin ? IZIN_KODLARI[h.izin] : null
  const parcalar = gorevler.map((x) => {
    const ek = [
      ROL_ETIKETLERI[x.rol],
      ...(x.yolculuk ? ['yolculuk'] : []),
      ...(x.gece ? ['gece görevi'] : []),
      ...(x.kirmizi ? ['kontrol bekliyor'] : []),
    ]
    return `${x.trenNo} ${x.kalkis}–${x.varis}, ${ek.join(', ')}`
  })
  if (izin) parcalar.push(`${izin.ad} (onaylı izin)`)
  if (tatil) parcalar.push(`resmi tatil: ${tatil}`)
  const kod = ilk ? `${ilk.yolculuk ? 'Y ' : ''}${kisaKod(ilk.trenNo)}` : (izin?.kod ?? '')
  return {
    tur: !ilk ? 'izin' : ilk.yolculuk ? 'yolculuk' : 'gorev',
    kod,
    saat: ilk?.kalkis ?? '',
    ek: Math.max(0, gorevler.length - 1),
    gece: gorevler.some((x) => x.gece),
    kirmizi: gorevler.some((x) => x.kirmizi),
    izinKodu: ilk && izin ? izin.kod : null,
    seferId: ilk?.seferId ?? null,
    ad: `${personel}, ${gun}: ${parcalar.join('; ')}`,
  }
}

const pad = (n: number) => String(n).padStart(2, '0')

/** "2026-10" + 5 -> "2026-10-05". */
export const tarihKodu = (ay: string, gun: number) => `${ay}-${pad(gun)}`

/** UTC öğle: yerel saat dilimi kaymasından bağımsız tarih. */
const tarih = (ay: string, gun: number) => new Date(`${tarihKodu(ay, gun)}T12:00:00Z`)

/** "12 Ekim 2026". */
export const gunMetni = (ay: string, gun: number) =>
  tarih(ay, gun).toLocaleDateString('tr-TR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })

const GUN_ADLARI = ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt']

/** Cumartesi ya da Pazar. */
export const haftaSonuMu = (ay: string, gun: number) => [0, 6].includes(tarih(ay, gun).getUTCDay())

/** Sütun başlığı: gün no, kısa gün adı, hafta sonu. */
export function gunBasligi(ay: string, gun: number) {
  return {
    no: gun,
    ad: GUN_ADLARI[tarih(ay, gun).getUTCDay()]!,
    haftaSonu: haftaSonuMu(ay, gun),
  }
}
