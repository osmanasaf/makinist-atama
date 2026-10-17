import { api, indir, sorguMetni } from '@/api/istemci'
import type { Zaman } from '@/api/tipler'

export interface PuantajSuzgeci {
  /** "YYYY-MM" */
  ay: string
  makinist?: number | string | null
  /** F3b: yalnız kondüktör için gönderilir (boşsa sunucu makinist grubunu verir). */
  tur?: string | null
}

export interface PuantajOranlari {
  saatUcreti: number | null
  fazlaMesaiCarpani: number
  geceTazminatOrani: number
  kmBirimTutari: number | null
  asgariGunlukSaat: number
  normalHaftalikSaat: number
  /** örn. "20:00–07:00" */
  gecePenceresi: string
}

export interface PuantajSatiri {
  makinistId: number
  sicil: string
  adSoyad: string
  calisilanGun: number
  fiiliSaat: number
  ucretlendirilenSaat: number
  fazlaMesaiSaat: number
  geceSaat: number
  km: number
  kmBilinmeyenSefer: number
  /** null: oran girilmemiş. */
  fazlaMesaiTl: number | null
  geceTl: number | null
  kmTl: number | null
}

export interface PuantajYaniti {
  ay: string
  uretimZamani: Zaman
  ureten: string
  etiket: string
  demo: boolean
  saatUcretiGirildi: boolean
  kmBirimGirildi: boolean
  oranlar: PuantajOranlari
  satirlar: PuantajSatiri[]
  /** F3b: raporun grubu; eski yanıtta yoksa makinist. */
  tur?: 'MAKINIST' | 'KONDUKTOR'
}

/** Boş makinist atılır (sorgu metni ve yazdırma adresi için). */
export function suzgecSorgusu(f: PuantajSuzgeci): Record<string, string> {
  const s: Record<string, string> = { ay: f.ay }
  if (f.makinist != null && String(f.makinist) !== '') s.makinist = String(f.makinist)
  if (f.tur === 'KONDUKTOR') s.tur = 'KONDUKTOR'
  return s
}

export const puantajApi = {
  rapor: (f: PuantajSuzgeci) =>
    api.get<PuantajYaniti>(`/api/rapor/puantaj${sorguMetni(suzgecSorgusu(f))}`),
  excel: (f: PuantajSuzgeci) => indir(`/api/rapor/puantaj/excel${sorguMetni(suzgecSorgusu(f))}`),
}
