import { api } from '@/api/istemci'

export type KurulumAdimKodu = 'KURUM' | 'TANIM' | 'MAKINIST' | 'PROFIL' | 'SEFER'

export interface KurulumAdimi {
  kod: KurulumAdimKodu
  ad: string
  yapildi: boolean
  /** Sayı özeti (kişisel veri yok). */
  ayrinti: string
}

export interface KurulumDurumu {
  adimlar: KurulumAdimi[]
  tamam: boolean
  /** Yönetici Ana Sayfa hatırlatma kartını gizledi. */
  gizli: boolean
}

/** İlk kurulum sihirbazı (yalnız yönetici). Durum her zaman veriden türetilir; ekran bayrak saklamaz. */
export const kurulumApi = {
  durum: () => api.get<KurulumDurumu>('/api/kurulum/durum'),
  gizle: (gizli: boolean) => api.put<KurulumDurumu>('/api/kurulum/sihirbaz-gizle', { gizli }),
}
