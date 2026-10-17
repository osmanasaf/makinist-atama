import { UZUN_ISLEM_ZAMAN_ASIMI_MS, api, indir, indirPost, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { IzinTuru, PersonelTuru } from '@/features/makinist/makinistApi'

/** "YYYY-MM" */
export type Ay = string

export interface AdaletSatiri {
  makinistId: number
  sicil: string
  adSoyad: string
  seferSayisi: number
  gorevSaat: number
  km: number
  kmBilinmeyenSefer: number
  geceSaat: number
  izinGunu: number
  /** Ortalama 0 ise null. */
  farkYuzde: number | null
  sifirSaat: boolean
  /** F2b: yolcu olarak gidilen sefer sayısı ("sefer sayısı" yalnız sürüş seferleridir). */
  yolculukSayisi?: number
}

export interface AdaletRaporu {
  ay: Ay
  /** F3b: rapor tek türün grubudur (adalet ortalaması türler arasında karışmaz); eski yanıtta yoksa makinist. */
  tur?: PersonelTuru
  uretimZamani: Zaman
  ureten: string
  makinistSayisi: number
  ortalamaSaat: number
  ortalamaKm: number
  kmBilinmeyenSefer: number
  olcut: string
  geceSaatiPenceresi: string
  satirlar: AdaletSatiri[]
}

export interface YedekGunu {
  tarih: Tarih
  planliSefer: number
  ihtiyac: number
  mevcut: number
  acik: number
  izinli: number
  gorevli: number
}

export interface YedekRaporu {
  ay: Ay
  uretimZamani: Zaman
  ureten: string
  aktifMakinist: number
  oran: number
  min: number
  eksikGunSayisi: number
  toplamAcik: number
  gunler: YedekGunu[]
}

export type IzinDurumu = 'TALEP' | 'ONAY' | 'RED'

export interface IzinKaydi {
  id: number
  makinistId: number
  sicil: string
  adSoyad: string
  baslangic: Tarih
  bitis: Tarih
  tur: IzinTuru
  durum: IzinDurumu
  olusturan: string
  kararVeren: string | null
}

export interface IzinTalebi {
  makinistId: number
  baslangic: Tarih
  bitis: Tarih
  tur: IzinTuru
}

// ---- Kadro ihtiyacı tahmini (F3a): POST /api/rapor/kadro (yalnız yönetici)

export interface KadroSanalSefer {
  hatId: number
  aracTipiId: number
  kalkisGarId: number
  varisGarId: number
  /** "HH:MM" */
  saatler: string[]
  sureDk: number
  /** İngilizce gün adları (MONDAY…); boşsa her gün. */
  gunler?: string[]
  km?: number
  trenTuru?: 'YUK' | 'YOLCU'
}

export interface KadroIstegi {
  bas: Tarih
  hafta: number
  devamsizlikYuzde: number
  yedekDahil: boolean
  yalnizAnalitik: boolean
  sureSn: number
  yolculuk: boolean
  seferler: KadroSanalSefer[]
}

/** Bir plan koşusunun özeti (mevcut kadro / K-20 kapalı / iyileştirmeli / yolculuk / senaryo). */
export interface KadroDoluluk {
  ad: string
  etiket: string
  seferSayisi: number
  bos: number
  dolulukYuzde: number
  sureMs: number
}

export interface KadroEgriNokta {
  ek: number
  bos: number
  dolulukYuzde: number
  /** Önceki noktaya göre eklenen kişi başına kapanan sefer; ilk nokta null. */
  marjinal: number | null
}

export interface KadroHedef {
  yuzde: number
  ulasildi: boolean
  ek: number | null
  bos: number
  dolulukYuzde: number
}

export interface KadroRaporu {
  varsayim: {
    bas: Tarih
    bit: Tarih
    hafta: number
    gunSayisi: number
    devamsizlikYuzde: number
    kisiBasinaSaat: number
    yedekOran: number
    yedekMin: number
    sureSn: number
    aktifMakinist: number
    seferSayisi: number
    haftalikTalepSaat: number
  }
  talep: { hat: string; aracTipi: string; seferSayisi: number; haftalikSaat: number }[]
  garlar: { gar: string; haftalikTalepSaat: number; mevcutMakinist: number; eklenen: number }[]
  analitik: {
    kisiBasinaSaat: number
    gerekliAltSinir: number
    tekBroveliGerekli: number
    mevcutAsil: number
    eksikSaat: number
    eksikAltSinir: number
    siniflar: {
      hat: string
      aracTipi: string
      haftalikSaat: number
      gerekliKisi: number
      mevcutYetkili: number
      eksikSaat: number
      eksikKisi: number
    }[]
  }
  kumeler: {
    aracTipleri: string[]
    hatlar: string[]
    mevcut: number
    kullanilanSaat: number
    kapasiteSaat: number
    kullanimYuzde: number
    durum: 'BOL' | 'NORMAL' | 'SIKISIK'
  }[]
  kumeKaynagi: 'PLAN' | 'AKIS'
  yedek: { ihtiyac: number; enYogunGunPlanliSefer: number } | null
  simulasyon: {
    mevcutAsil: number
    mevcut: KadroDoluluk
    konumsuz: KadroDoluluk | null
    iyilestirmeli: KadroDoluluk | null
    yolculuk: KadroDoluluk | null
    egri: KadroEgriNokta[]
    hedefler: KadroHedef[]
    egriDurma: 'BOS_SIFIR' | 'TAMAM' | 'EK_SINIRI'
    egriKosuSayisi: number
    dagilimEk: number
    dagilimEtiketi: string
    dagilim: {
      gar: string
      aracTipi: string
      hat: string
      kisi: number
      seferSayisi: number
      haftalikSaat: number
    }[]
    kalanBoslar: {
      seferId: number
      trenNo: string
      tarih: Tarih
      kalkisGar: string
      varisGar: string
      neden: string
    }[]
    kalanBosToplam: number
    kalanNedenler: { neden: string; adet: number }[]
    atlananlar: string[]
    tamamlandi: boolean
    sureMs: number
  } | null
  senaryo: {
    ekSeferSayisi: number
    ekHaftalikSaat: number
    taban: KadroDoluluk | null
    senaryo: KadroDoluluk | null
    bosFarki: number
    gurultuEsigi: number
    mevcutYeter: boolean
    gerekce: string
    egriFarki:
      | { yuzde: number; tabanEk: number | null; senaryoEk: number | null; fark: number | null }[]
      | null
    ekTalepKisiEsdegeri: number
    atilKapasiteKisiEsdegeri: number
    tabanEksikSaat: number
    senaryoEksikSaat: number
    tamamlandi: boolean
    uyari: string | null
  } | null
}

// İzin etkisi (atanmış seferler) için makinistApi.izinEtkisi yeniden kullanılır.
export const raporApi = {
  /** `tur` yalnız kondüktör için gönderilir; boşsa sunucu makinist grubunu verir. */
  adalet: (ay: Ay, tur?: PersonelTuru) =>
    api.get<AdaletRaporu>(`/api/rapor/adalet${sorguMetni({ ay, tur })}`),
  adaletExcel: (ay: Ay, tur?: PersonelTuru) =>
    indir(`/api/rapor/adalet/excel${sorguMetni({ ay, tur })}`),
  kadro: (g: KadroIstegi) =>
    api.post<KadroRaporu>('/api/rapor/kadro', g, UZUN_ISLEM_ZAMAN_ASIMI_MS),
  kadroExcel: (r: KadroRaporu) => indirPost('/api/rapor/kadro/excel', r),
  yedek: (ay: Ay) => api.get<YedekRaporu>(`/api/rapor/yedek${sorguMetni({ ay })}`),
  izinListe: (durum?: IzinDurumu) => api.get<IzinKaydi[]>(`/api/izin${sorguMetni({ durum })}`),
  izinTalep: (g: IzinTalebi) => api.post<IzinKaydi>('/api/izin', g),
  izinOnay: (id: number) => api.post<IzinKaydi>(`/api/izin/${id}/onay`),
  izinRed: (id: number) => api.post<IzinKaydi>(`/api/izin/${id}/red`),
}
