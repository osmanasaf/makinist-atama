import { api, sorguMetni } from '@/api/istemci'
import type { Zaman } from '@/api/tipler'
import type { AtamaRolu } from '@/features/atama/atamaApi'
import type { IzinTuru, PersonelTuru } from '@/features/makinist/makinistApi'
import type { Ay } from './raporApi'

/** Hücredeki görev (F5, MK-177). `gece`/`yolculuk`/`kirmizi` yalnız doğruysa gelir; `seferId` serbest intikalde yoktur. */
export interface CizelgeGorev {
  seferId?: number
  trenNo: string
  /** "HH:mm" (İstanbul, güncel saat) */
  kalkis: string
  varis: string
  rol: AtamaRolu
  gece?: boolean
  yolculuk?: boolean
  /** Yalnız `kontrol=true` isteğinde. */
  kirmizi?: boolean
}

/** Seyrek hücre: yalnız görevi ya da onaylı izni olan günler gelir. */
export interface CizelgeHucre {
  /** Ayın günü (1..gunSayisi). */
  gun: number
  izin?: IzinTuru
  gorevler?: CizelgeGorev[]
}

export interface CizelgeSatiri {
  makinistId: number
  sicil: string
  adSoyad: string
  bagliGar: string
  /** Toplamlar adalet raporuyla aynı sayıdır. */
  gorevSaat: number
  geceSaat: number
  km: number
  seferSayisi: number
  yolculukSayisi: number
  izinGunu: number
  hucreler: CizelgeHucre[]
}

export interface CizelgeTatil {
  gun: number
  ad: string
}

export interface CizelgeYaniti {
  ay: Ay
  gunSayisi: number
  tur: PersonelTuru
  garId: number | null
  kontrol: boolean
  uretimZamani: Zaman
  ureten: string
  satirlar: CizelgeSatiri[]
  tatiller: CizelgeTatil[]
}

export interface CizelgeSuzgeci {
  tur?: PersonelTuru
  garId?: number | null
  /** Canlı kırmızı bayrağı (ağır hesap; varsayılan kapalı). */
  kontrol?: boolean
}

/** Aylık çizelge (salt okunur: yalnız GET). Makinist rolü bu uca giremez (403, OK-35). */
export const cizelgeApi = {
  getir: (ay: Ay, s: CizelgeSuzgeci = {}) =>
    api.get<CizelgeYaniti>(
      `/api/rapor/cizelge${sorguMetni({
        ay,
        tur: s.tur === 'KONDUKTOR' ? s.tur : undefined,
        garId: s.garId ?? undefined,
        kontrol: s.kontrol ? 'true' : undefined,
      })}`,
    ),
}
