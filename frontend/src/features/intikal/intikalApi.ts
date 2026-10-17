import { api, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { AtamaNedeni, KaldirSonucu } from '@/features/atama/atamaApi'

/** Serbest intikal (İ-1, MK-104): sefer olarak kayıtlı olmayan yolla (servis, otobüs, taksi) bir garından diğerine gidiş. */
export type AracTuru = 'SERVIS' | 'OTOBUS' | 'TAKSI' | 'DIGER'
export const ARAC_ETIKETLERI: Record<AracTuru, string> = {
  SERVIS: 'Servis',
  OTOBUS: 'Otobüs',
  TAKSI: 'Taksi',
  DIGER: 'Diğer',
}

/** Silme yok: iptal edilen intikal PLANLI -> IPTAL olur ve listede kalır. */
export type IntikalDurumu = 'PLANLI' | 'IPTAL'
export type IntikalGordu = 'YENI' | 'GORULDU' | 'DEGISTI'

/** Atamacı görünümü. `kirmizi/nedenler/uyarilar` her okumada canlı hesaplanır (MK-107); IPTAL için hepsi boş. */
export interface Intikal {
  id: number
  makinistId: number
  sicil: string
  adSoyad: string
  kalkisGarId: number
  kalkisGar: string
  varisGarId: number
  varisGar: string
  kalkis: Zaman
  varis: Zaman
  aracTuru: AracTuru
  notu: string | null
  durum: IntikalDurumu
  versiyon: number
  /** Makinistin "gördüm" onayı (özet değişince DEGISTI). */
  gordu: IntikalGordu
  gorduZamani: Zaman | null
  kirmizi: boolean
  nedenler: string[]
  uyarilar: string[]
}

export interface IntikalGovde {
  makinistId: number
  kalkisGarId: number
  varisGarId: number
  kalkis: Zaman
  varis: Zaman
  aracTuru: AracTuru
  notu?: string
  /** Kural UYARISI varsa zorunlu. */
  gerekce?: string
  /** Yalnız düzeltmede zorunlu. */
  versiyon?: number
}

/** Yazma sonucu: `yeniKirmizi` iptal / düzeltme sonrası ÖNCE kırmızı olmayıp SONRA kırmızı olan atamalar (kural gereği; atamacı bilsin). */
export interface IntikalSonucu {
  intikal: Intikal
  yeniKirmizi: KaldirSonucu['yeniKirmizi']
}

export interface IntikalIptalParametre {
  neden: AtamaNedeni
  nedenNotu?: string
  versiyon: number
}

export const intikalApi = {
  liste: (bas: Tarih, bit: Tarih, makinistId?: number) =>
    api.get<Intikal[]>(`/api/intikal${sorguMetni({ bas, bit, makinistId })}`),
  ekle: (g: IntikalGovde) => api.post<IntikalSonucu>('/api/intikal', g),
  guncelle: (id: number, g: IntikalGovde) => api.put<IntikalSonucu>(`/api/intikal/${id}`, g),
  iptal: (id: number, p: IntikalIptalParametre) =>
    api.sil<IntikalSonucu>(`/api/intikal/${id}${sorguMetni({ ...p })}`),
}
