import { api, indir, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import { tarihBicimle } from '@/lib/tarih'

/** Tür seçicide listelenen türler. "Gördüm" (GORDUM) günde yüzlerce kayıt üretir: seçicide yok, ayrı onay kutusuyla istenir (varsayılan raporda gizli). */
export type ListelenenTur =
  | 'TANIM_EKLE'
  | 'TANIM_GUNCELLE'
  | 'PARAMETRE_DEGISTI'
  | 'KURAL_EKLE'
  | 'KURAL_GUNCELLE'
  | 'MAKINIST_EKLE'
  | 'MAKINIST_GUNCELLE'
  | 'IZIN_EKLE'
  | 'SEFER_EKLE'
  | 'SEFER_GUNCELLE'
  | 'SEFER_YUKLE'
  | 'SEFER_IPTAL'
  | 'SEFER_GECIKME'
  | 'SEFER_BOL'
  | 'SEFER_BIRLESTIR'
  | 'INTIKAL_EKLE'
  | 'INTIKAL_GUNCELLE'
  | 'INTIKAL_IPTAL'
  | 'INTIKAL_SURESI_DEGISTI'
  | 'PROFIL_UYGULA'
  | 'TATIL_EKLE'
  | 'TATIL_SIL'
  | 'ATAMA'
  | 'ATAMA_KALDIR'
  | 'ATAMA_DEGISTIR'
  | 'OTOMATIK_ATAMA'

export type DegisiklikTuru = ListelenenTur | 'GORDUM'

export const GORDUM_ETIKETI = 'Gördüm (tebliğ kanıtı)'

export const TUR_ETIKETLERI: Record<ListelenenTur, string> = {
  TANIM_EKLE: 'Tanım eklendi',
  TANIM_GUNCELLE: 'Tanım güncellendi',
  PARAMETRE_DEGISTI: 'Parametre değişti',
  KURAL_EKLE: 'Kural eklendi',
  KURAL_GUNCELLE: 'Kural güncellendi',
  MAKINIST_EKLE: 'Personel eklendi',
  MAKINIST_GUNCELLE: 'Personel güncellendi',
  IZIN_EKLE: 'İzin eklendi',
  SEFER_EKLE: 'Sefer eklendi',
  SEFER_GUNCELLE: 'Sefer güncellendi',
  SEFER_YUKLE: 'Sefer yüklendi',
  SEFER_IPTAL: 'Sefer iptali',
  SEFER_GECIKME: 'Sefer gecikmesi',
  SEFER_BOL: 'Sefer bölündü',
  SEFER_BIRLESTIR: 'Sefer birleştirildi',
  INTIKAL_EKLE: 'İntikal eklendi',
  INTIKAL_GUNCELLE: 'İntikal güncellendi',
  INTIKAL_IPTAL: 'İntikal iptal edildi',
  INTIKAL_SURESI_DEGISTI: 'Gar intikal süresi değişti',
  PROFIL_UYGULA: 'Kural profili uygulandı',
  TATIL_EKLE: 'Resmi tatil eklendi',
  TATIL_SIL: 'Resmi tatil silindi',
  ATAMA: 'Atama',
  ATAMA_KALDIR: 'Atama kaldırıldı',
  ATAMA_DEGISTIR: 'Atama değiştirildi',
  OTOMATIK_ATAMA: 'Otomatik atama',
}

/** Sorgu/süzgeç: bas ve bit zorunlu (en çok 366 gün), diğerleri boş bırakılabilir. */
export interface DegisiklikSuzgeci {
  bas: Tarih
  bit: Tarih
  trenNo?: string
  kullanici?: string
  tur?: DegisiklikTuru | ''
}

export interface DegisiklikSatiri {
  id: number
  zaman: Zaman
  kim: string
  tur: DegisiklikTuru
  turAdi: string
  sicil: string | null
  trenNo: string | null
  seferTarihi: Tarih | null
  eski: string | null
  yeni: string | null
  neden: string | null
  nedenNotu: string | null
  uyarili: boolean
  uyariGerekcesi: string | null
  /** Uyaran kuralların kodu + metni (ör. "Ö-001: Tek sefer süresi: ..."); boş olabilir. */
  uyarilar: string[]
  /** Kaydı otomatik atama üretti (ya da otomatik atama çalıştırma kaydı). */
  otomatik: boolean
}

export interface DegisiklikBolumu {
  kod: string
  ad: string
  sayi: number
  satirlar: DegisiklikSatiri[]
}

export interface DegisiklikYaniti {
  uretimZamani: Zaman
  ureten: string
  filtre: {
    bas: Tarih
    bit: Tarih
    makinistId?: number | null
    makinist?: string | null
    trenNo?: string | null
    kullanici?: string | null
    tur?: string | null
  }
  kayitSayisi: number
  bolumler: DegisiklikBolumu[]
}

/** Boş süzgeç alanları atılır (sorgu metni ve yazdırma adresi için). */
export function suzgecSorgusu(f: DegisiklikSuzgeci): Record<string, string> {
  const s: Record<string, string> = { bas: f.bas, bit: f.bit }
  if (f.trenNo?.trim()) s.trenNo = f.trenNo.trim()
  if (f.kullanici?.trim()) s.kullanici = f.kullanici.trim()
  if (f.tur) s.tur = f.tur
  return s
}

/** "Tarih: 03.10.2026 – 09.10.2026 · Tren no: 1001 · Kullanıcı: ali · Tür: Atama" (yalnız dolu alanlar). */
export function suzgecOzeti(f: DegisiklikYaniti['filtre']): string {
  const parcalar = [`Tarih: ${tarihBicimle(f.bas)} – ${tarihBicimle(f.bit)}`]
  if (f.makinist) parcalar.push(`Personel: ${f.makinist}`)
  if (f.trenNo) parcalar.push(`Tren no: ${f.trenNo}`)
  if (f.kullanici) parcalar.push(`Kullanıcı: ${f.kullanici}`)
  if (f.tur)
    parcalar.push(
      `Tür: ${f.tur === 'GORDUM' ? GORDUM_ETIKETI : (TUR_ETIKETLERI[f.tur as ListelenenTur] ?? f.tur)}`,
    )
  return parcalar.join(' · ')
}

export const degisiklikApi = {
  rapor: (f: DegisiklikSuzgeci) =>
    api.get<DegisiklikYaniti>(`/api/degisiklik${sorguMetni(suzgecSorgusu(f))}`),
  excel: (f: DegisiklikSuzgeci) => indir(`/api/degisiklik/excel${sorguMetni(suzgecSorgusu(f))}`),
}
