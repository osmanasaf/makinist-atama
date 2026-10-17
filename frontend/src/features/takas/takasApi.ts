import { api, sorguMetni } from '@/api/istemci'
import type { Zaman } from '@/api/tipler'
import type { AtamaNedeni, OneriSonucu } from '@/features/atama/atamaApi'
import type { TakasDurumu, TakasIhlali, TakasTuru } from './takasTipleri'

/** Atamacının talep satırı (GET /api/takas): makinist adları ve neden notu dahil. `durum` etkin durumdur (bayat / süresi dolmuş okumada türetilir). */
export interface TakasSatiri {
  id: number
  tur: TakasTuru
  durum: TakasDurumu
  kayitliDurum: TakasDurumu
  kaynakSeferId: number
  kaynakMakinistId: number
  kaynakSicil: string
  kaynakAd: string
  kaynakGorev: string
  hedefMakinistId: number | null
  hedefSicil: string | null
  hedefAd: string | null
  hedefGorev: string | null
  neden: AtamaNedeni
  nedenNotu: string | null
  olusturma: Zaman
  sonKabul: Zaman | null
  sonKalkis: Zaman
}

/** `bekleyen`: atamacı onayı bekleyen talep sayısı (üst çubuk rozeti). */
export interface TakasListe {
  bekleyen: number
  talepler: TakasSatiri[]
}

/** Ayrıntı ekranı: kural ihlallerinin AYRINTISI yalnız burada (hangi makinist hangi kural). `oneri`: bırakmada "yerine ata" önerisi. */
export interface TakasDetay {
  talep: TakasSatiri
  ihlaller: TakasIhlali[]
  oneri: OneriSonucu | null
}

export interface TakasOnayGovdesi {
  /** Yalnız bırakma: görevi devralacak makinist; boşsa görev yalnız kaldırılır. */
  yeniMakinistId?: number
  gerekce?: string
}

export const takasApi = {
  liste: (durum?: TakasDurumu) => api.get<TakasListe>(`/api/takas${sorguMetni({ durum })}`),
  detay: (id: number) => api.get<TakasDetay>(`/api/takas/${id}`),
  onay: (id: number, g: TakasOnayGovdesi) => api.post<TakasSatiri>(`/api/takas/${id}/onay`, g),
  red: (id: number) => api.post<TakasSatiri>(`/api/takas/${id}/red`),
}
