import { api, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { Ton } from '@/bilesenler/durum'
import type { SimgeAdi } from '@/bilesenler/simgeler'

export type MakinistDurumu = 'AKTIF' | 'ASKIDA' | 'PASIF'
export type IzinTuru = 'YILLIK' | 'RAPOR' | 'DIGER'
/** Kartın türü (F3b): kondüktör = tren şefi; kart ömür boyu aynı türdedir. */
export type PersonelTuru = 'MAKINIST' | 'KONDUKTOR'
export const TUR_ETIKETLERI: Record<PersonelTuru, string> = {
  MAKINIST: 'Makinist',
  KONDUKTOR: 'Kondüktör',
}

export const DURUM_ETIKETLERI: Record<MakinistDurumu, string> = {
  AKTIF: 'Aktif',
  ASKIDA: 'Askıda',
  PASIF: 'Pasif',
}
export const IZIN_ETIKETLERI: Record<IzinTuru, string> = {
  YILLIK: 'Yıllık',
  RAPOR: 'Rapor',
  DIGER: 'Diğer',
}

export const IZIN_DURUM_ETIKETLERI: Record<string, string> = {
  TALEP: 'Talep',
  ONAY: 'Onaylı',
  RED: 'Reddedildi',
}

/** İzin durumu -> rozet: anlam ton + simge + metinle birlikte taşınır (yalnız renk değil). */
export const IZIN_DURUM_GORUNUMU: Record<string, { ton: Ton; simge: SimgeAdi }> = {
  TALEP: { ton: 'uyari', simge: 'saat' },
  ONAY: { ton: 'basari', simge: 'basari' },
  RED: { ton: 'engel', simge: 'iptal' },
}

export interface MakinistOzet {
  id: number
  sicil: string
  adSoyad: string
  bagliGarId: number
  bagliGar: string
  durum: MakinistDurumu
  /** Sunucu her zaman gönderir; eski yanıtta yoksa makinisttir. */
  tur?: PersonelTuru
  ehliyetBitis: Tarih | null
  saglikBitis: Tarih | null
  psikoteknikBitis: Tarih | null
}

export interface BroveAracTipi {
  aracTipiId: number
  aracTipi?: string
  sinavTarihi: Tarih
  gecerlilikBitis?: Tarih | null
}
export interface BroveHat {
  hatId: number
  hat?: string
  sinavTarihi: Tarih
  sonSurus?: Tarih | null
  gecerlilikBitis?: Tarih | null
}
export interface Izin {
  id: number
  baslangic: Tarih
  bitis: Tarih
  tur: IzinTuru
  durum: string
  olusturan: string
}

export interface MakinistDetay extends MakinistOzet {
  sonSurus: Tarih | null
  versiyon: number
  broveAracTipleri: BroveAracTipi[]
  broveHatlar: BroveHat[]
  izinler: Izin[]
}

export interface MakinistGovde {
  sicil: string
  adSoyad: string
  bagliGarId: number
  durum: MakinistDurumu
  /** Yalnız oluşturmada anlamlı; yoksa MAKINIST. Güncellemede farklı değer 400 TUR_DEGISTIRILEMEZ. */
  tur?: PersonelTuru
  ehliyetBitis: Tarih | null
  saglikBitis: Tarih | null
  psikoteknikBitis: Tarih | null
  sonSurus: Tarih | null
  versiyon?: number
  broveAracTipleri: { aracTipiId: number; sinavTarihi: Tarih }[]
  broveHatlar: { hatId: number; sinavTarihi: Tarih; sonSurus: Tarih | null }[]
}

/** İzin aralığıyla çakışan atanmış sefer (GET .../izin-etkisi). */
export interface IzinEtkisiSatiri {
  /** Serbest intikalde (İ-1) yok: `null` (satır sefer değil, intikaldir; `trenNo` "İntikal"). */
  atamaId: number | null
  seferId: number
  tarih: Tarih
  trenNo: string
  rol: 'ASIL' | 'YARDIMCI' | 'KONDUKTOR' | 'YOLCU'
  kalkisGar: string
  varisGar: string
  kalkis: Zaman
  varis: Zaman
}

export interface HesapDurumu {
  var: boolean
  girisAdi: string | null
  aktif: boolean
  parolaDegistirmeli: boolean
  kilitli: boolean
}

/** Hesap açma / parola sıfırlama yanıtı: geçici parola YALNIZ bu yanıtta döner, bir daha okunamaz. */
export interface GeciciParolaYaniti {
  hesap: HesapDurumu
  geciciParola: string
}

export const makinistApi = {
  liste: (arama: string, durum: MakinistDurumu | '', tur: PersonelTuru | '' = '') =>
    api.get<MakinistOzet[]>(`/api/makinist${sorguMetni({ arama: arama.trim(), durum, tur })}`),
  getir: (id: number) => api.get<MakinistDetay>(`/api/makinist/${id}`),
  olustur: (g: MakinistGovde) => api.post<MakinistDetay>('/api/makinist', g),
  guncelle: (id: number, g: MakinistGovde) => api.put<MakinistDetay>(`/api/makinist/${id}`, g),
  izinEkle: (id: number, g: { baslangic: Tarih; bitis: Tarih; tur: IzinTuru }) =>
    api.post<Izin>(`/api/makinist/${id}/izin`, g),
  hesap: (id: number) => api.get<HesapDurumu>(`/api/makinist/${id}/hesap`),
  hesapAc: (id: number) => api.post<GeciciParolaYaniti>(`/api/makinist/${id}/hesap`),
  hesapParolaSifirla: (id: number) =>
    api.post<GeciciParolaYaniti>(`/api/makinist/${id}/hesap/parola-sifirla`),
  hesapKilitAc: (id: number) => api.post<HesapDurumu>(`/api/makinist/${id}/hesap/kilit-ac`),
  izinEtkisi: (id: number, bas: Tarih, bit: Tarih) =>
    api.get<IzinEtkisiSatiri[]>(`/api/atama/makinist/${id}/izin-etkisi${sorguMetni({ bas, bit })}`),
}
