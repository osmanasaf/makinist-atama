import { api, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { BelgeDto, BelgeDurumu } from '@/features/belge/belgeApi'
import type { AtamaNedeni } from '@/features/atama/atamaApi'
import type { IzinTuru } from '@/features/makinist/makinistApi'
import type { TakasDurumu, TakasNedeni, TakasTuru } from '@/features/takas/takasTipleri'

/** Makinistin kendi görevi (GET /api/benim/gorevler). `tarih` planlanan kalkış günüdür; iptal sefer listeden düşmez. */
export interface BenimGorev {
  atamaId: number
  seferId: number
  tarih: Tarih
  sira: number
  trenNo: string
  rol: 'ASIL' | 'YARDIMCI' | 'KONDUKTOR' | 'YOLCU'
  durum: 'PLANLI' | 'IPTAL'
  kalkisGar: string
  varisGar: string
  planlananKalkis: Zaman
  planlananVaris: Zaman
  guncelKalkis: Zaman
  guncelVaris: Zaman
  kalkisGecikmeDk: number
  varisGecikmeDk: number
  varisErtesiGun: boolean
  aracTipi: string
  hat: string
  km: number | null
  esMakinist: string | null
  /** "Gördüm" durumu: YENI hiç görülmedi, GORULDU güncel hali görüldü, DEGISTI görüldükten sonra değişti (yeniden onay). */
  gordu: 'YENI' | 'GORULDU' | 'DEGISTI'
  gorduZamani: Zaman | null
  /** Görevin güncel kısa özeti; DEGISTI iken eskiOzet onaylanan önceki özettir. */
  ozet: string
  eskiOzet: string | null
  parcaNo?: number | null
  parcaSayisi?: number | null
}

/** Elinden alınan görev kalemi (kaldırıldı / başkasına verildi); neden notu yok, yalnız etiket. */
export interface BenimKalem {
  olayId: number
  tur: 'KALDIRILDI' | 'DEGISTI_BASKASINA'
  seferId: number
  trenNo: string | null
  tarih: Tarih | null
  ozet: string
  nedenEtiketi: AtamaNedeni | null
  zaman: Zaman
}

/** Makinistin kendi serbest intikali (MK-108; `GET /api/benim/gorevler` → `intikaller`): atamacının notu yoktur. IPTAL satırı da gelir. */
export interface BenimIntikal {
  id: number
  kalkisGar: string
  varisGar: string
  kalkis: Zaman
  varis: Zaman
  aracTuru: 'SERVIS' | 'OTOBUS' | 'TAKSI' | 'DIGER'
  durum: 'PLANLI' | 'IPTAL'
  gordu: 'YENI' | 'GORULDU' | 'DEGISTI'
  gorduZamani: Zaman | null
  ozet: string
  eskiOzet: string | null
}

export interface BenimGorevler {
  bugun: Tarih
  ufukGun: number
  gorevler: BenimGorev[]
  kaldirilanlar: BenimKalem[]
  /** Benim kabul / reddetmem gereken gelen takas talepleri (rozet "Takas: n"). */
  bekleyenTakas?: number
  /** Açık talepteki kendi atama kimliklerim (kartta "takas bekleniyor"). */
  takasAcikAtamalar?: number[]
  /** Kendi serbest intikallerim (görev ufkunda); eski yanıtlarda yok. */
  intikaller?: BenimIntikal[]
}

export interface BenimBelgeler {
  bugun: Tarih
  uyariGun: number
  enKotu: BelgeDurumu
  belgeler: BelgeDto[]
}

export interface BenimOzet {
  ay: string
  adaleteDahil: boolean
  planlananDahil: boolean
  seferSayisi: number
  gorevSaat: number
  km: number
  kmBilinmeyenSefer: number
  geceSaat: number
  izinGunu: number
  ortalamaSaat: number
  farkYuzde: number | null
  /** Yolcu olarak gidilen sefer sayısı (F2b): görev saatine dahil, km'ye değil; eski yanıtlarda yok. */
  yolculukSayisi?: number
}

/** Makinistin kendi konaklaması (GET /api/benim/konaklama): not alanı yoktur. */
export interface BenimKonaklama {
  id: number
  gar: string
  yerTipi: 'YATAKHANE' | 'OTEL' | 'DIGER'
  giris: Zaman
  cikis: Zaman
  gece: number
}

export interface BenimIzin {
  id: number
  baslangic: Tarih
  bitis: Tarih
  tur: IzinTuru
  durum: 'TALEP' | 'ONAY' | 'RED'
}

/** Takas / bırakma talebim (GET /api/benim/takas). Karşı tarafın yalnız sicili ve görev özeti vardır; kural ayrıntısı, ad, belge yok. */
export interface BenimTakas {
  id: number
  tur: TakasTuru
  /** Okuma anındaki etkin durum (bayat / süresi dolmuş türetilir). */
  durum: TakasDurumu
  gelen: boolean
  yanitBekliyor: boolean
  benimGorev: string
  karsiSicil: string | null
  karsiGorev: string | null
  /** Yalnız kendi açtığım talepte dolu. */
  neden: TakasNedeni | null
  nedenNotu: string | null
  olusturma: Zaman
  sonKabul: Zaman | null
  /** REDDEDILDI ise kimin reddettiği (yalnız rol; kural ayrıntısı değil). */
  reddeden?: 'KARSI_MAKINIST' | 'ATAMACI' | null
}

/** Takas için seçilebilen başkasının görevi: kural ayrıntısı yok, listeden süzülmemiş. */
export interface TakasHedef {
  hedefAtamaId: number
  sicil: string
  gorev: string
  trenNo: string
  tarih: Tarih
  kalkisGar: string
  varisGar: string
  guncelKalkis: Zaman
}

export interface TakasAcIstegi {
  atamaId: number
  /** Dolu = karşılıklı takas, boş = görevi bırakma. */
  hedefAtamaId?: number
  neden: TakasNedeni
  nedenNotu?: string
}

export const benimApi = {
  gorevler: () => api.get<BenimGorevler>('/api/benim/gorevler'),
  /** "Gördüm": ekranda GÖRDÜĞÜNÜZ özetle birlikte; sunucudaki güncel özetle uyuşmazsa 409 OZET_DEGISTI (ApiHata.gorev = güncel görev). Güncel görevi döner (ikinci basış aynı yanıt). */
  gordum: (atamaId: number, ozet: string) =>
    api.post<BenimGorev>(`/api/benim/gorevler/${atamaId}/gordum`, { ozet }),
  /** Serbest intikal "gördüm": ekrandaki özetle; bayat özet 409 OZET_DEGISTI (ApiHata.gorev = güncel intikal). */
  intikalGordum: (id: number, ozet: string) =>
    api.post<BenimIntikal>(`/api/benim/intikal/${id}/gordum`, { ozet }),
  /** Elinden alınan görev kalemini kapatır (204). */
  olayiKapat: (olayId: number) => api.post(`/api/benim/olaylar/${olayId}/gordum`),
  belgeler: () => api.get<BenimBelgeler>('/api/benim/belgeler'),
  ozet: (ay?: string) => api.get<BenimOzet>(`/api/benim/ozet${sorguMetni({ ay })}`),
  konaklama: () => api.get<BenimKonaklama[]>('/api/benim/konaklama'),
  izinler: () => api.get<BenimIzin[]>('/api/benim/izinler'),
  izinTalepEt: (g: { baslangic: Tarih; bitis: Tarih; tur: IzinTuru }) =>
    api.post<BenimIzin>('/api/benim/izinler', g),
  takaslar: () => api.get<BenimTakas[]>('/api/benim/takas'),
  takasHedefler: (atamaId: number) =>
    api.get<TakasHedef[]>(`/api/benim/takas/hedefler${sorguMetni({ atamaId })}`),
  takasAc: (g: TakasAcIstegi) => api.post<BenimTakas>('/api/benim/takas', g),
  takasKabul: (id: number) => api.post<BenimTakas>(`/api/benim/takas/${id}/kabul`),
  takasRed: (id: number) => api.post<BenimTakas>(`/api/benim/takas/${id}/red`),
  takasIptal: (id: number) => api.post<BenimTakas>(`/api/benim/takas/${id}/iptal`),
  /** Kendi parolası: başarıda sunucu oturumu düşürür (204), yeniden giriş gerekir. */
  parolaDegistir: (eskiParola: string, yeniParola: string) =>
    api.post('/api/auth/parola', { eskiParola, yeniParola }),
}
