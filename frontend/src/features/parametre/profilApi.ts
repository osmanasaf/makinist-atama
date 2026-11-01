import { api, indir } from '@/api/istemci'

export type SatirDurumu = 'DEGISIR' | 'AYNI' | 'GECERSIZ' | 'BILINMEYEN' | 'PROFILDE_YOK'
export type SatirTuru = 'PARAMETRE' | 'SIDDET' | 'AYAR' | 'OZEL_KURAL'

/** Profil paketi (sunucu `ProfilPaketi`): ekran içeriğini yorumlamaz, olduğu gibi önizlemeye/uygulamaya geri gönderir. */
export interface ProfilPaketi {
  format: string
  profilSurumu: number
  ad: string | null
  aciklama: string | null
  etiket: string | null
  uretimZamani?: string | null
  ureten?: string | null
  dayanak?: Record<string, string>
  parametreler?: Record<string, string>
  siddetler?: Record<string, string>
  ayarlar?: Record<string, number>
  ozelKurallar?: unknown[]
}

export interface ProfilSatiri {
  /** `TUR:anahtar`; uygulamada seçim anahtarı. */
  kimlik: string
  tur: SatirTuru
  anahtar: string
  ad: string
  mevcut: string | null
  yeni: string | null
  durum: SatirDurumu
  neden: string | null
  dayanak: string | null
  kaynak: string | null
  guvenilirlik: string | null
}

export interface ProfilOnizleme {
  paket: ProfilPaketi
  satirlar: ProfilSatiri[]
  uyarilar: string[]
  ozet: { degisir: number; ayni: number; gecersiz: number; bilinmeyen: number; profildeYok: number }
}

export interface HazirProfilOzeti {
  kod: string
  ad: string
  aciklama: string
  etiket: string
}

export interface UygulamaSonucu {
  yazilan: number
  atlanan: number
}

/** Sunucu sınırıyla aynı (1 MB): büyük dosya hiç gönderilmez. */
export const EN_COK_PROFIL_BAYT = 1_048_576

/** Hazır profillerde ve dışa aktarımda değişmeyen uyarı; sunucudan etiket gelmezse bu gösterilir. */
export const VARSAYILAN_ETIKET =
  'Örnek başlangıç — kurum mevzuatıyla doğrulanmalı. Karar destek aracıdır.'

export const profilApi = {
  hazirListe: () => api.get<HazirProfilOzeti[]>('/api/profil/hazir'),
  hazir: (kod: string) => api.get<ProfilPaketi>(`/api/profil/hazir/${encodeURIComponent(kod)}`),
  onizle: (paket: ProfilPaketi) => api.post<ProfilOnizleme>('/api/profil/onizle', paket),
  uygula: (paket: ProfilPaketi, secilen: string[], neden: string) =>
    api.post<UygulamaSonucu>('/api/profil/uygula', { paket, secilen, neden }),
  disaAktar: () => indir('/api/profil/disa-aktar'),
}
