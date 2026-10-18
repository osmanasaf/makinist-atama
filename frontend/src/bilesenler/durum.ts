import type { SimgeAdi } from './simgeler'

/** Rozet tonları: anlam renge değil ton + simge + METİN üçlüsüne bağlıdır (WCAG 1.4.1). */
export type Ton = 'notr' | 'basari' | 'uyari' | 'engel' | 'bilgi' | 'gece'
export type RolKodu = 'ASIL' | 'YARDIMCI' | 'KONDUKTOR' | 'YOLCU'

export interface DurumOzellik {
  ton: Ton
  simge: SimgeAdi
  metin: string
}

/** Durum kodu -> görünüm: TEK kaynak (MK-138). BOŞ (uyarı) ile Kondüktör eksik (bilgi) artık farklı tondur. */
const DURUMLAR: Record<string, DurumOzellik> = {
  ENGEL: { ton: 'engel', simge: 'engel', metin: 'Engel' },
  KONTROL: { ton: 'engel', simge: 'engel', metin: 'Kontrol' },
  IPTAL: { ton: 'engel', simge: 'iptal', metin: 'İptal' },
  UYARI: { ton: 'uyari', simge: 'uyari', metin: 'Uyarı' },
  BOS: { ton: 'uyari', simge: 'daire', metin: 'Boş' },
  YAKLASIYOR: { ton: 'uyari', simge: 'saat', metin: 'Yaklaşıyor' },
  GORDU: { ton: 'basari', simge: 'basari', metin: 'Gördü' },
  GECERLI: { ton: 'basari', simge: 'basari', metin: 'Geçerli' },
  UYGULANDI: { ton: 'basari', simge: 'basari', metin: 'Uygulandı' },
  GORMEDI: { ton: 'notr', simge: 'daire', metin: 'Görmedi' },
  KONDUKTOR_EKSIK: { ton: 'bilgi', simge: 'kisi', metin: 'Kondüktör eksik' },
  BILGI: { ton: 'bilgi', simge: 'bilgi', metin: 'Bilgi' },
  GECE: { ton: 'gece', simge: 'ay', metin: 'Gece' },
}

export function durumOzellikleri(kod: string): DurumOzellik {
  return DURUMLAR[kod] ?? { ton: 'notr', simge: 'bilgi', metin: kod }
}

export const ROL_METINLERI: Record<RolKodu, string> = {
  ASIL: 'Asıl',
  YARDIMCI: 'Yardımcı',
  KONDUKTOR: 'Kondüktör',
  YOLCU: 'Yolcu',
}
