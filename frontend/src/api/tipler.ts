// API sözleşmesinden elle yazılmış ortak tipler. Özellik tipleri features/<ad>/ altında tutulur.

/** "2026-10-30" */
export type Tarih = string
/** ISO-8601 UTC, "2026-10-05T06:12:33.123Z" */
export type Zaman = string
export type Rol = 'YONETICI' | 'ATAMACI' | 'MAKINIST'

/** application/problem+json gövdesi; detail Türkçe ve gösterilebilir. */
export interface Hata {
  /** sunucu yazmaz; title İngilizce durum metnidir, kullanmayın: kod + detail esastır */
  type?: string
  title: string
  status: number
  detail: string
  kod: string
  alanlar?: Record<string, string>
  /** 422 KURAL_IHLALI: atamayı engelleyen kurallar */
  ihlaller?: Ihlal[]
  /** 409 OZET_DEGISTI ("gördüm" bayat ekran): sunucudaki güncel görev */
  gorev?: unknown
}

/** Atama kuralı ihlali. ENGEL = atanamaz, UYARI = gerekçeyle atanabilir. */
export interface Ihlal {
  kod: string
  siddet: 'ENGEL' | 'UYARI'
  olculen: number
  esik: number
  birim: string
  metin: string
}

export interface KullaniciDto {
  id: number
  eposta: string
  adSoyad: string
  rol: Rol
  aktif: boolean
  /** Makinist hesabında bağlı makinist kartı; personelde yok. */
  makinistId?: number | null
  /** Geçici parolayla açılmış hesap: yalnız parola değiştirme ekranı kullanılabilir. */
  parolaDegistirmeli?: boolean
}

export const ROL_ETIKETLERI: Record<Rol, string> = {
  YONETICI: 'Yönetici',
  ATAMACI: 'Atamacı',
  MAKINIST: 'Personel',
}
