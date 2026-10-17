import { api, indir, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'

export type YerTipi = 'YATAKHANE' | 'OTEL' | 'DIGER'
export const YER_ETIKETLERI: Record<YerTipi, string> = {
  YATAKHANE: 'Yatakhane',
  OTEL: 'Otel',
  DIGER: 'Diğer',
}

/** Dış gar konaklaması. `gece`: süre / 24 saat yukarı yuvarlanır (en az 1); rapor ayı giriş günüdür. */
export interface Konaklama {
  id: number
  makinistId: number
  sicil: string
  adSoyad: string
  garId: number
  gar: string
  yerTipi: YerTipi
  giris: Zaman
  cikis: Zaman
  gece: number
  notu: string | null
  versiyon: number
}

export interface KonaklamaGovde {
  makinistId: number
  garId: number
  yerTipi: YerTipi
  giris: Zaman
  cikis: Zaman
  notu?: string
  /** Yalnız güncellemede zorunlu. */
  versiyon?: number
}

/** Dış garda iki nöbet arası boşluk: konaklama önerisi (otomatik yazılmaz). */
export interface KonaklamaOnerisi {
  makinistId: number
  sicil: string
  adSoyad: string
  garId: number
  gar: string
  bas: Zaman
  bit: Zaman
  sureDk: number
  gece: number
  kayitli: boolean
}

export interface RaporGari {
  garId: number
  gar: string
  konaklamaSayisi: number
  gece: number
}

export interface RaporMakinisti {
  makinistId: number
  sicil: string
  adSoyad: string
  konaklamaSayisi: number
  gece: number
  eksikOneri: number
  garlar: RaporGari[]
}

export interface KonaklamaRaporu {
  ay: string
  uretimZamani: Zaman
  ureten: string
  toplamGece: number
  toplamEksikOneri: number
  makinistler: RaporMakinisti[]
}

export const konaklamaApi = {
  liste: (bas: Tarih, bit: Tarih, makinistId?: number) =>
    api.get<Konaklama[]>(`/api/konaklama${sorguMetni({ bas, bit, makinistId })}`),
  oneri: (bas: Tarih, bit: Tarih, makinistId?: number) =>
    api.get<KonaklamaOnerisi[]>(`/api/konaklama/oneri${sorguMetni({ bas, bit, makinistId })}`),
  ekle: (g: KonaklamaGovde) => api.post<Konaklama>('/api/konaklama', g),
  guncelle: (id: number, g: KonaklamaGovde) => api.put<Konaklama>(`/api/konaklama/${id}`, g),
  sil: (id: number) => api.sil(`/api/konaklama/${id}`),
  rapor: (ay: string) => api.get<KonaklamaRaporu>(`/api/konaklama/rapor${sorguMetni({ ay })}`),
  raporExcel: (ay: string) => indir(`/api/konaklama/rapor/excel${sorguMetni({ ay })}`),
}
