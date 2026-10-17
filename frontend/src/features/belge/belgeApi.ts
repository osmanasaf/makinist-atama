import { api } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import type { MakinistDurumu, PersonelTuru } from '@/features/makinist/makinistApi'

export type BelgeDurumu = 'DOLDU' | 'YAKLASIYOR' | 'GECERLI' | 'GIRILMEMIS'
export type BelgeTuru =
  'EHLIYET' | 'SAGLIK' | 'PSIKOTEKNIK' | 'ARAC_TIPI' | 'HAT_SINAV' | 'HAT_SURUS' | 'GENEL_SURUS'

export interface BelgeDto {
  tur: BelgeTuru
  ad: string
  bitis: Tarih | null
  kalanGun: number | null
  durum: BelgeDurumu
}

export interface MakinistBelgeDto {
  makinistId: number
  sicil: string
  adSoyad: string
  durum: MakinistDurumu
  /** F3b: kondüktörde 3 satır (kişisel emniyet belgesi, sağlık, psikoteknik); eski yanıtta yoksa makinist. */
  tur?: PersonelTuru
  enKotu: BelgeDurumu
  belgeler: BelgeDto[]
}

export interface BelgeDurumuYaniti {
  bugun: Tarih
  uyariGun: number
  makinistler: MakinistBelgeDto[]
}

export const belgeApi = {
  durum: () => api.get<BelgeDurumuYaniti>('/api/rapor/belge-durumu'),
}
