import { api, indir, sorguMetni } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'

export interface Sefer {
  id: number
  trenNo: string
  tarih: Tarih
  kalkisGarId: number
  kalkisGar: string
  varisGarId: number
  varisGar: string
  planlananKalkis: string
  planlananVaris: string
  kalkisGecikmeDk: number
  varisGecikmeDk: number
  guncelKalkis: string
  guncelVaris: string
  aracTipiId: number
  aracTipi: string
  hatId: number
  hat: string
  km: number | null
  durum: 'PLANLI' | 'IPTAL'
  versiyon: number
  /** Ara istasyonda makinist değişen trenin parçası (F2c); parçasızda null. */
  parcaNo?: number | null
  parcaSayisi?: number | null
}

export interface SeferGovde {
  trenNo: string
  tarih: Tarih
  kalkisGarId: number
  kalkisSaat: string
  varisGarId: number
  varisSaat: string
  varisTarihi?: Tarih
  aracTipiId: number
  hatId: number
  km?: number
  versiyon?: number
}

export interface YuklemeSatiri {
  satirNo: number
  trenNo: string
  tarih: Tarih
  kalkisGar: string
  varisGar: string
  kalkis: string
  varis: string
  aracTipi: string
  hat: string
  durum: 'YENI' | 'ZATEN_VAR'
  parcaNo?: number | null
  parcaSayisi?: number | null
}

export interface YuklemeSonucu {
  onizleme: boolean
  toplamSatir: number
  eklenen: number
  zatenVar: number
  hataSayisi: number
  satirlar: YuklemeSatiri[]
  /** `sayfa`/`trenNo`: hatanın hangi Excel sayfasından ve hangi trenden geldiği (F2c). */
  hatalar: { satirNo: number; neden: string; sayfa?: string; trenNo?: string | null }[]
  /** Onayı durduran hata var (ör. Değişim Noktaları'nda Seferler'de karşılığı olmayan tren): "Onayla" kilitli, sunucu da reddeder. */
  engelli?: boolean
}

export const seferApi = {
  liste: (gun: Tarih) => api.get<Sefer[]>(`/api/sefer${sorguMetni({ gun })}`),
  getir: (id: number) => api.get<Sefer>(`/api/sefer/${id}`),
  olustur: (g: SeferGovde) => api.post<Sefer>('/api/sefer', g),
  guncelle: (id: number, g: SeferGovde) => api.put<Sefer>(`/api/sefer/${id}`, g),
  sablon: () => indir('/api/sefer/sablon'),
  yukle: (dosya: File, onizle: boolean) => {
    const f = new FormData()
    f.append('dosya', dosya)
    return api.post<YuklemeSonucu>(`/api/sefer/yukle?onizle=${onizle}`, f)
  },
}
