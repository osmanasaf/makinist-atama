// Takas / bırakma ortak tipleri ve etiketleri (makinist ve atamacı ekranları paylaşır). Durum metin + simgeyle gösterilir, yalnız renkle değil (NFR-M04).
import type { SimgeAdi } from '@/bilesenler/simgeler'
import type { AtamaNedeni } from '@/features/atama/atamaApi'

export type TakasTuru = 'TAKAS' | 'BIRAK'
export type TakasDurumu =
  | 'KARSI_ONAY_BEKLIYOR'
  | 'ATAMACI_ONAYI_BEKLIYOR'
  | 'ONAYLANDI'
  | 'REDDEDILDI'
  | 'IPTAL'
  | 'SURESI_DOLDU'
  | 'BAYAT'

/** Talep açarken seçilebilen nedenler (sunucu yalnız bunları kabul eder). */
export type TakasNedeni = Extract<AtamaNedeni, 'HASTALIK' | 'IZIN' | 'DIGER'>
export const TAKAS_NEDENLERI: { deger: TakasNedeni; ad: string }[] = [
  { deger: 'HASTALIK', ad: 'Hastalık' },
  { deger: 'IZIN', ad: 'İzin' },
  { deger: 'DIGER', ad: 'Diğer' },
]

export const TAKAS_TUR_ETIKETLERI: Record<TakasTuru, string> = {
  TAKAS: 'Takas',
  BIRAK: 'Görevi bırakma',
}

export const TAKAS_DURUM_ETIKETLERI: Record<TakasDurumu, { simge: SimgeAdi; ad: string }> = {
  KARSI_ONAY_BEKLIYOR: { simge: 'saat', ad: 'Karşı makinistin yanıtı bekleniyor' },
  ATAMACI_ONAYI_BEKLIYOR: { simge: 'saat', ad: 'Atamacı onayı bekleniyor' },
  ONAYLANDI: { simge: 'basari', ad: 'Onaylandı' },
  REDDEDILDI: { simge: 'iptal', ad: 'Reddedildi' },
  IPTAL: { simge: 'ok-sol', ad: 'Geri çekildi' },
  SURESI_DOLDU: { simge: 'gecikme', ad: 'Süresi doldu' },
  BAYAT: { simge: 'takas', ad: 'Görev değiştiği için geçersiz' },
}

/** Sunucudaki 422 KURAL_IHLALI gövdesinde takasın ihlali: hangi makinist, hangi kural (yalnız atamacı görür). */
export interface TakasIhlali {
  sicil: string
  ihlal: {
    kod: string
    siddet: 'ENGEL' | 'UYARI'
    olculen: number
    esik: number
    birim: string
    metin: string
  }
}
