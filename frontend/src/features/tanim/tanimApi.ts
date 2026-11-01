import { api, sorguMetni } from '@/api/istemci'

export type TanimTipi = 'GAR' | 'HAT' | 'ARAC_TIPI'

export interface TanimDto {
  id: number
  tip: TanimTipi
  ad: string
}

export const TANIM_ETIKETLERI: Record<TanimTipi, string> = {
  GAR: 'Garlar',
  HAT: 'Hatlar',
  ARAC_TIPI: 'Araç tipleri',
}

/** İ-2 (MK-109): gar çifti intikal süresi (yönsüz; `garAId < garBId`). Tanımlar verisidir; boşken "yolculukla ata"da servis ile intikal önerilmez. */
export interface GarIntikalSuresi {
  garAId: number
  garA: string
  garBId: number
  garB: string
  dakika: number
  versiyon: number
}

export interface GarIntikalSureGovde {
  garAId: number
  garBId: number
  dakika: number
  /** Çift zaten kayıtlıysa zorunlu (iyimser kilit). */
  versiyon?: number
}

/** F5 (MK-178): resmi tatil; yönetici elle girer, YALNIZ GÖSTERİM (aylık çizelge başlığı). */
export interface ResmiTatil {
  /** "YYYY-MM-DD" */
  tarih: string
  ad: string
}

export const tanimApi = {
  resmiTatiller: (yil?: number) => api.get<ResmiTatil[]>(`/api/resmi-tatil${sorguMetni({ yil })}`),
  tatilEkle: (g: ResmiTatil) => api.post<ResmiTatil>('/api/resmi-tatil', g),
  tatilSil: (tarih: string) => api.sil(`/api/resmi-tatil/${tarih}`),
  liste: (tip: TanimTipi) => api.get<TanimDto[]>(`/api/tanimlar?tip=${tip}`),
  ekle: (tip: TanimTipi, ad: string) => api.post<TanimDto>('/api/tanimlar', { tip, ad }),
  adlandir: (id: number, ad: string) => api.put<TanimDto>(`/api/tanimlar/${id}`, { ad }),
  intikalSureleri: () => api.get<GarIntikalSuresi[]>('/api/tanimlar/intikal-sure'),
  intikalSuresiYaz: (g: GarIntikalSureGovde) =>
    api.put<GarIntikalSuresi>('/api/tanimlar/intikal-sure', g),
  intikalSuresiSil: (garAId: number, garBId: number) =>
    api.sil(`/api/tanimlar/intikal-sure?garAId=${garAId}&garBId=${garBId}`),
}
