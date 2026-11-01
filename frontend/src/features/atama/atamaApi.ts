import { api, sorguMetni, UZUN_ISLEM_ZAMAN_ASIMI_MS } from '@/api/istemci'
import type { Ihlal, Tarih, Zaman } from '@/api/tipler'
import type { Sefer } from '@/features/sefer/seferApi'

/** YOLCU (F2b): makinist o seferde görevsiz yolcu olarak gider (intikal); sürüş değildir, bir seferde birden çok olabilir. */
/** KONDUKTOR (F3b): seferde en çok bir kondüktör (tren şefi); yalnız kondüktör türündeki personel atanır. */
export type AtamaRolu = 'ASIL' | 'YARDIMCI' | 'KONDUKTOR' | 'YOLCU'
export const ROL_ETIKETLERI: Record<AtamaRolu, string> = {
  ASIL: 'Asıl',
  YARDIMCI: 'Yardımcı',
  KONDUKTOR: 'Kondüktör',
  YOLCU: 'Yolcu',
}
/** Masa satırındaki ad önekü (Asıl öneksiz). */
export const ROL_ONEKLERI: Record<AtamaRolu, string> = {
  ASIL: '',
  YARDIMCI: 'Yrd: ',
  KONDUKTOR: 'Kond: ',
  YOLCU: 'Yolcu: ',
}

export type AtamaNedeni = 'IPTAL' | 'GECIKME' | 'HASTALIK' | 'IZIN' | 'BELGE' | 'DIGER'
export const NEDEN_ETIKETLERI: Record<AtamaNedeni, string> = {
  IPTAL: 'Sefer iptali',
  GECIKME: 'Gecikme',
  HASTALIK: 'Hastalık',
  IZIN: 'İzin',
  BELGE: 'Belge sorunu',
  DIGER: 'Diğer',
}

export interface GunMakinisti {
  atamaId: number
  rol: AtamaRolu
  makinistId: number
  sicil: string
  ad: string
  /** Atama artık kuralları sağlamıyor ya da sefer iptal (sunucu her okumada hesaplar). */
  kirmizi: boolean
  kirmiziNedenler: string[]
  /** Kırmızı olmayan UYARI düzeyi ihlal metinleri. */
  uyarilar: string[]
  /**
   * "Gördüm" bilgisi (EK alan, yazdırma kullanmaz): GORDU / GORMEDI / HESAP_YOK; kalkışı geçmiş ya da iptal seferde null.
   * `gorduUyari`: görmedi ve kalkışa uyarı eşiğinden (varsayılan 12 saat) az kalmış.
   */
  gordu?: 'GORDU' | 'GORMEDI' | 'HESAP_YOK' | null
  gorduZamani?: Zaman | null
  gorduUyari?: boolean
}

export interface GunSatiri {
  seferId: number
  trenNo: string
  kalkisGar: string
  varisGar: string
  planlananKalkis: Zaman
  planlananVaris: Zaman
  guncelKalkis: Zaman
  guncelVaris: Zaman
  kalkisGecikmeDk: number
  varisGecikmeDk: number
  aracTipi: string
  hat: string
  km: number | null
  durum: 'PLANLI' | 'IPTAL'
  kirmizi: boolean
  makinistler: GunMakinisti[]
  /** Kimsenin kurallara tam uymadığı boş seferde "0/N uygun: ..." özeti (UX-13); yoksa null. */
  bosNeden: string | null
  /** Ara istasyonda makinist değişen trenin parçası (F2c; satır sıradan bir sefer satırıdır); parçasızda null. */
  parcaNo?: number | null
  parcaSayisi?: number | null
  /** Ayar `konduktor.yolcu_trenine_gerekli` açıkken kondüktörü atanmamış yolcu treni (F3b, MK-87): bilgi rozeti, kırmızı değil. */
  kondukturEksik?: boolean
}

export interface GunListesi {
  tarih: Tarih
  uretimZamani: Zaman
  satirlar: GunSatiri[]
  kondukturEksikSayisi?: number
}

export interface Aday {
  makinistId: number
  sicil: string
  adSoyad: string
  bagliGar: string
  uygun: boolean
  ihlaller: Ihlal[]
  buAySaat: number
  buAyKm: number
  buSeferdeRol: AtamaRolu | null
}

export interface AtamaGovde {
  makinistId: number
  rol?: AtamaRolu
  gerekce?: string
  neden?: AtamaNedeni
  nedenNotu?: string
}

export interface AtamaSonucu {
  atamaId: number
  seferId: number
  rol: AtamaRolu
  makinistId: number
  sicil: string
  ad: string
  atayan: string
  atamaZamani: Zaman
  gerekce: string | null
  uyarilar: Ihlal[]
}

/** F2b: bir makinist için hedef seferden önce yolcu olarak gidilebilecek sefer (en çok 3). `uyarilar`: ENGEL olmayan ihlaller (varsa gerekçe zorunlu). */
export interface YolculukOnerisi {
  seferId: number
  trenNo: string
  kalkisGar: string
  varisGar: string
  kalkis: Zaman
  varis: Zaman
  aktarmaDk: number
  uyarilar: Ihlal[]
  gerekce: string
}

/** İ-2 (MK-110): gar süre tablosuna göre TEK serbest intikal önerisi; saatler sunucuda hesaplanır. `null`/yok = öneri yok. */
export interface IntikalOnerisi {
  kalkisGar: string
  varisGar: string
  kalkis: Zaman
  varis: Zaman
  sureDk: number
  aktarmaDk: number
  uyarilar: Ihlal[]
  gerekce: string
}

export interface YolculukOnerisiSonucu {
  oneriler: YolculukOnerisi[]
  intikal?: IntikalOnerisi | null
}

/** "İntikalle ata": gar çifti ve saatler İSTEMCİDEN GİTMEZ (sunucu süre tablosundan hesaplar). */
export interface IntikalleGovde {
  makinistId: number
  aracTuru: 'SERVIS' | 'OTOBUS' | 'TAKSI' | 'DIGER'
  notu?: string
  gerekce?: string
  neden?: AtamaNedeni
  nedenNotu?: string
}

export interface YolculuklaGovde {
  makinistId: number
  yolculukSeferId: number
  gerekce?: string
  neden?: AtamaNedeni
  nedenNotu?: string
}

export interface YolculuklaSonucu {
  yolcu: AtamaSonucu
  asil: AtamaSonucu
}

export interface KaldirParametre {
  rol: AtamaRolu
  /** Yalnız rol=YOLCU için zorunlu (bir seferde birden çok yolcu olabilir). */
  makinistId?: number
  neden: AtamaNedeni
  nedenNotu?: string
  /** OK-37 (Geri al yarışı): verilirse ve rolün güncel ataması bu kimlik değilse sunucu 409 (ATAMA_DEGISTI) verir, hiçbir şey silinmez. */
  atamaId?: number
}

/** Kaldırma, aynı makinistin BAŞKA atamalarını ya da serbest intikalini yeni kırmızı yaptıysa sunucu 200 + bu gövdeyi döner (kural gereği; atamacı bilsin). */
export interface KaldirSonucu {
  yeniKirmizi: { atamaId: number; seferId: number; trenNo: string; nedenler: string[] }[]
  yeniKirmiziIntikal?: { intikalId: number; ozet: string; nedenler: string[] }[]
}

/** Kaldırma başka atamayı kırmızı yaptıysa (ör. nöbet bölünür, ardışık gece haftası K-18) atamacıya söylenir. */
export function kaldirMesaji(sonuc?: KaldirSonucu): string {
  const gorevler = [
    ...(sonuc?.yeniKirmizi ?? []).map((y) => ({ ad: y.trenNo, neden: y.nedenler[0] })),
    ...(sonuc?.yeniKirmiziIntikal ?? []).map((y) => ({ ad: y.ozet, neden: y.nedenler[0] })),
  ]
  if (gorevler.length === 0) return 'Atama kaldırıldı.'
  const ozet = gorevler
    .slice(0, 3)
    .map((y) => `${y.ad} (${y.neden ?? 'kural ihlali'})`)
    .join('; ')
  return `Atama kaldırıldı. Dikkat: bu makinistin ${gorevler.length} başka görevi kırmızı oldu: ${ozet}${gorevler.length > 3 ? ' …' : ''}`
}

/** Seferi böl (MK-128): ara istasyonlar; saatler "SS:dd" (gün sunucuda çözülür). `hatId`/`aracTipiId` boşsa önceki parçadan miras. */
export interface BolNokta {
  garId: number
  varisSaat: string
  kalkisSaat: string
  /** Yalnız seferin km'si doluysa zorunlu; boşsa gönderilmez. */
  km?: number
  hatId?: number
  aracTipiId?: number
}

export interface SeferBolGovde {
  versiyon: number
  noktalar: BolNokta[]
  /** true = sunucu aynı hesabı yapıp geri alır (yazmaz); varsayılan false. */
  onizle?: boolean
}

/** Önizlemede ve uygulamada aynı gövde; `yeniKirmizi` boş olsa da liste döner. */
export interface BolSonucu {
  parcalar: Sefer[]
  yeniKirmizi: KaldirSonucu['yeniKirmizi']
}

/** Böl'ün tersi (MK-126/128): `versiyon` = çağrılan satırın sürümü. Önizlemede 409/422 de koşul sonucunu verir. */
export interface SeferBirlestirGovde {
  versiyon: number
  onizle?: boolean
}

export interface BirlestirSonucu {
  sefer: Sefer
  silinenSeferIdler: number[]
  yeniKirmizi: KaldirSonucu['yeniKirmizi']
}

export interface KirmiziSayisi {
  bas: Tarih
  bit: Tarih
  kirmiziSefer: number
  kirmiziAtama: number
  /** Canlı kırmızı serbest intikal sayısı (OK-28): atama sayacına KATILMAZ, ayrı gösterilir; eski yanıtlarda yok. */
  kirmiziIntikal?: number
  /** Aralıktaki en erken kırmızı SEFER günü (B-2); kırmızı yoksa null, eski yanıtlarda yok → ana sayfa bugüne düşer. */
  ilkKirmiziGun?: Tarih | null
}

/** Atamacı "kim gördü" sayaçları: yalnız kalkışı geçmemiş, iptal olmayan atamalar; hesapsız ayrı sayaç. */
export interface GordumOzeti {
  bas: Tarih
  bit: Tarih
  toplam: number
  gorulen: number
  gorulmeyen: number
  hesapsiz: number
  uyari: number
}

export interface Oneri {
  makinistId: number
  sicil: string
  adSoyad: string
  bagliGar: string
  buAySaat: number
  buAyKm: number
  uyarilar: Ihlal[]
  gerekce: string
}

export interface OneriSonucu {
  oneriler: Oneri[]
  uygunAdayYok: boolean
  mesaj: string | null
}

export interface UygunSefer {
  seferId: number
  trenNo: string
  kalkisGar: string
  varisGar: string
  guncelKalkis: Zaman
  guncelVaris: Zaman
  aracTipi: string
  hat: string
  uyarilar: Ihlal[]
}

export interface OtomatikAtamaGovde {
  bas: Tarih
  bit: Tarih
  /** false = önizleme (yazmaz), true = aynı sonucu tek işlemde yazar. */
  uygula: boolean
  /** F2b: garda uygun aday yoksa yolcu olarak gidecek aday da aranır (varsayılan kapalı; kapalıyken alan hiç gönderilmez). */
  yolculuk?: boolean
  /** F2d: daha çok sefer dolduran plan aranır (varsayılan kapalı; kapalıyken alan hiç gönderilmez). Yolculukla birlikte kullanılamaz. */
  iyilestir?: boolean
  /** Uygula'da zorunlu: önizlemenin `iyilestirme.anahtar` değeri aynen geri gönderilir. */
  anahtar?: IyilestirmeAnahtari
}

/** İyileştirmenin en iyi turunun girdisi (sefer kimliği -> ağırlık, kaydırma dakikası). */
export interface IyilestirmeAnahtari {
  kaydirDk: number
  oncelik: Record<string, number>
  /** Sunucunun bastığı imza (HMAC): anahtar aynen geri gönderilir, değiştirilirse 400. */
  imza?: string | null
}

interface OtomatikSefer {
  seferId: number
  trenNo: string
  tarih: Tarih
  kalkis: Zaman
  varis: Zaman
  kalkisGar: string
  varisGar: string
}

export interface OtomatikAtanan extends OtomatikSefer {
  makinistId: number
  sicil: string
  ad: string
  /** Doluysa makinist önce bu seferde yolcu olarak gider (yolculuk), sonra bu sefere asıl atanır (F2b). */
  yolculuk?: {
    seferId: number
    trenNo: string
    kalkis: Zaman
    varis: Zaman
    kalkisGar: string
    varisGar: string
  } | null
}

export interface OtomatikBosKalan extends OtomatikSefer {
  /** "0/3 uygun: hat yetkisi 1, izin 1, dinlenme 1" */
  neden: string
  ornekler: string[]
}

export interface OtomatikAtamaSonucu {
  bas: Tarih
  bit: Tarih
  uygulandi: boolean
  atananSayisi: number
  bosSayisi: number
  /** Kalkışı geçmişte kalıp atlanan sefer sayısı (boş sayılmaz, UX-11). */
  gecmisSayisi: number
  atananlar: OtomatikAtanan[]
  bosKalanlar: OtomatikBosKalan[]
  /** Yalnız iyileştirme açıkken dolu. `tabanBosSayisi` yalnız önizlemede (bugünkü planın boş sayısı); uygulamada null. */
  iyilestirme?: {
    tabanBosSayisi: number | null
    tur: number
    sureMs: number
    anahtar: IyilestirmeAnahtari
    /** Aramanın bitiş nedeni (yalnız önizleme): BOS_YOK, BOS_SIFIR, TUR_SINIRI, SURE; uygulamada null. */
    durma?: 'BOS_YOK' | 'BOS_SIFIR' | 'TUR_SINIRI' | 'SURE' | null
  } | null
}

export interface SeferIptalGovde {
  neden: AtamaNedeni
  nedenNotu?: string
  /** Parçalı trende: bu ve sonraki parçalara da uygula (boşsa sunucu true sayar; parçasızda yok sayılır). */
  devam?: boolean
}

export interface SeferGecikmeGovde extends SeferIptalGovde {
  /** Planlanana göre TOPLAM gecikme (0..1440). */
  kalkisGecikmeDk: number
  /** Boşsa sunucu kalkışınkini kullanır. */
  varisGecikmeDk?: number | null
}

export const atamaApi = {
  gun: (tarih: Tarih) => api.get<GunListesi>(`/api/atama/gun${sorguMetni({ tarih })}`),
  adaylar: (seferId: number, rol: AtamaRolu) =>
    api.get<Aday[]>(`/api/atama/sefer/${seferId}/adaylar${sorguMetni({ rol })}`),
  ata: (seferId: number, govde: AtamaGovde) =>
    api.post<AtamaSonucu>(`/api/atama/sefer/${seferId}`, govde),
  kaldir: (seferId: number, p: KaldirParametre) =>
    api.sil<KaldirSonucu | undefined>(`/api/atama/sefer/${seferId}${sorguMetni({ ...p })}`),
  kirmiziSayisi: (bas: Tarih, bit: Tarih) =>
    api.get<KirmiziSayisi>(`/api/atama/kirmizi-sayisi${sorguMetni({ bas, bit })}`),
  gordumOzeti: (bas: Tarih, bit: Tarih) =>
    api.get<GordumOzeti>(`/api/atama/gordum-ozeti${sorguMetni({ bas, bit })}`),
  oneri: (seferId: number) => api.get<OneriSonucu>(`/api/atama/sefer/${seferId}/oneri`),
  yolculukOner: (seferId: number, makinistId: number) =>
    api.get<YolculukOnerisiSonucu>(
      `/api/atama/sefer/${seferId}/yolculuk-oner${sorguMetni({ makinistId })}`,
    ),
  yolculukla: (seferId: number, govde: YolculuklaGovde) =>
    api.post<YolculuklaSonucu>(`/api/atama/sefer/${seferId}/yolculukla`, govde),
  intikalle: (seferId: number, govde: IntikalleGovde) =>
    api.post<{ intikal: unknown; atama: unknown }>(`/api/atama/sefer/${seferId}/intikalle`, govde),
  uygunSeferler: (makinistId: number, gun: Tarih) =>
    api.get<UygunSefer[]>(`/api/atama/makinist/${makinistId}/uygun-seferler${sorguMetni({ gun })}`),
  otomatik: (g: OtomatikAtamaGovde) =>
    api.post<OtomatikAtamaSonucu>('/api/atama/otomatik', g, UZUN_ISLEM_ZAMAN_ASIMI_MS),
  seferIptal: (seferId: number, g: SeferIptalGovde) =>
    api.post<Sefer>(`/api/sefer/${seferId}/iptal`, g),
  seferGecikme: (seferId: number, g: SeferGecikmeGovde) =>
    api.post<Sefer>(`/api/sefer/${seferId}/gecikme`, g),
  bol: (seferId: number, g: SeferBolGovde) =>
    api.post<BolSonucu>(`/api/atama/sefer/${seferId}/bol`, g),
  birlestir: (seferId: number, g: SeferBirlestirGovde) =>
    api.post<BirlestirSonucu>(`/api/atama/sefer/${seferId}/birlestir`, g),
}
