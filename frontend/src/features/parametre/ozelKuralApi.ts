import { api, UZUN_ISLEM_ZAMAN_ASIMI_MS } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { KuralSiddeti } from './parametreApi'

export { SIDDET_ETIKETLERI } from './parametreApi'

/** Ölçü kodları katalogdan gelir; tanınmayan kod da olduğu gibi gösterilir. */
export type Olcu = string
export type Pencere = 'SEFER' | 'NOBET' | 'GUN' | 'HAFTA' | 'AY' | 'YIL' | 'KAYAN' | 'SERI'
export type Karsilastirma = 'EN_FAZLA' | 'EN_AZ'
export type Vardiya = 'HEPSI' | 'GECE' | 'GUNDUZ'
export type TrenTuru = 'YUK' | 'YOLCU'
export type PersonelTuru = 'MAKINIST' | 'KONDUKTOR'
export type Birim = 's' | 'adet' | 'gün'

export interface Kapsam {
  hatlar: number[]
  aracTipleri: number[]
  kalkisGarlari: number[]
  varisGarlari: number[]
  trenTurleri: TrenTuru[]
  vardiya: Vardiya
  /** F3b (MK-85): boş/yok = tüm personel türleri; kural yalnız bu türlerdeki personele uygulanır. */
  personelTurleri?: PersonelTuru[]
}

export interface OzelKuralDto {
  id: number
  kod: string
  ad: string
  olcu: Olcu
  olcuAd: string
  birim: Birim
  pencere: Pencere
  /** Yalnız pencere KAYAN iken dolu. */
  kayanGun: number | null
  karsilastirma: Karsilastirma
  esik: number
  kapsam: Kapsam
  siddet: KuralSiddeti
  gecerlilikBas: Tarih
  gecerlilikBit: Tarih | null
  gerekce: string
  kaynak: string | null
  versiyon: number
  ozet: string
  olusturan: string
  olusturma: Zaman
  guncelleme: Zaman
}

export interface OlcuDto {
  kod: Olcu
  ad: string
  birim: Birim
  aciklama: string
  pencereler: {
    kod: Pencere
    ad: string
    aciklama: string
    esikAlt: number
    esikAltDahil: boolean
    esikUst: number
  }[]
  karsilastirmalar: { kod: Karsilastirma; ad: string; aciklama: string }[]
}

export interface OzelKuralIstegi {
  ad: string
  olcu: Olcu
  pencere: Pencere
  /** Pencere KAYAN ise 2–366, değilse null. */
  kayanGun?: number | null
  karsilastirma: Karsilastirma
  esik: number
  kapsam?: Kapsam
  siddet: KuralSiddeti
  gecerlilikBas?: Tarih
  gecerlilikBit?: Tarih | null
  gerekce: string
  kaynak?: string
  /** Yalnız PUT: düzenlenen kuralın sürümü. */
  versiyon?: number
  /** Yalnız PUT: değişiklik nedeni. */
  neden?: string
}

export interface DeneOrnegi {
  sicil: string
  tarih: Tarih
  trenNo: string
  durum: 'KIRMIZI' | 'UYARI'
  metin: string
}

export interface DeneSonucu {
  bas: Tarih
  bit: Tarih
  toplamAtama: number
  kirmiziAtama: number
  kirmiziMakinist: number
  uyariAtama: number
  uyariMakinist: number
  ornekler: DeneOrnegi[]
}

export const VARDIYA_ETIKETLERI: Record<Vardiya, string> = {
  HEPSI: 'Hepsi',
  GECE: 'Yalnız gece',
  GUNDUZ: 'Yalnız gündüz',
}

export const TREN_TURU_ETIKETLERI: Record<TrenTuru, string> = { YUK: 'Yük', YOLCU: 'Yolcu' }

export const SIDDET_ACIKLAMALARI: Record<KuralSiddeti, string> = {
  ENGEL: 'Atama yapılamaz; mevcut atamalar kırmızı görünür.',
  UYARI: 'Atama yapılabilir ama gerekçe istenir; otomatik atama bu seferi uyarılı adaya vermez.',
  KAPALI: 'Kural kayıtlı kalır ama hiç değerlendirilmez.',
}

/** Birim kodunun uzun adı (alan etiketi için). */
export const BIRIM_ADLARI: Record<Birim, string> = { s: 'saat', adet: 'adet', gün: 'gün' }

/** Mevcut kuralı istek gövdesine çevirir (sürüm ve neden çağıran tarafından eklenir). */
export function kuralIstegi(k: OzelKuralDto): OzelKuralIstegi {
  return {
    ad: k.ad,
    olcu: k.olcu,
    pencere: k.pencere,
    kayanGun: k.kayanGun,
    karsilastirma: k.karsilastirma,
    esik: k.esik,
    kapsam: k.kapsam,
    siddet: k.siddet,
    gecerlilikBas: k.gecerlilikBas,
    gecerlilikBit: k.gecerlilikBit,
    gerekce: k.gerekce,
    kaynak: k.kaynak ?? undefined,
  }
}

export const ozelKuralApi = {
  liste: () => api.get<OzelKuralDto[]>('/api/ozel-kural'),
  katalog: () => api.get<OlcuDto[]>('/api/ozel-kural/katalog'),
  ekle: (govde: OzelKuralIstegi) => api.post<OzelKuralDto>('/api/ozel-kural', govde),
  guncelle: (id: number, govde: OzelKuralIstegi) =>
    api.put<OzelKuralDto>(`/api/ozel-kural/${id}`, govde),
  dene: (govde: OzelKuralIstegi, id?: number) =>
    api.post<DeneSonucu>(
      `/api/ozel-kural/dene${id === undefined ? '' : `?id=${id}`}`,
      govde,
      UZUN_ISLEM_ZAMAN_ASIMI_MS,
    ),
}
