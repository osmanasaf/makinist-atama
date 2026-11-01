import { api, indir, sorguMetni } from '@/api/istemci'
import type { Tarih, Zaman } from '@/api/tipler'
import type { Ay } from './raporApi'

/** Ad + adet satırı (uyarı gerekçesi ya da değişiklik nedeni → adet). */
export interface OzetAdet {
  ad: string
  adet: number
}

/**
 * Yönetim özeti (F4, MK-180): her sayı mevcut raporlardan gelir (adalet, puantaj, kırmızı sayısı, denetim raporu, yedek, belge panosu, gün listesi); ikinci hesap yok.
 * Veri yoksa dağılım alanları `null`'dır (sıfıra bölme yok).
 */
export interface YonetimOzeti {
  ay: Ay
  uretimZamani: Zaman
  ureten: string
  /** Veritabanında demo makinist var: ekran "DEMO – gerçek değil" der. */
  demo: boolean
  doluluk: { toplamSefer: number; dolu: number; bos: number; yuzde: number | null }
  kirmizi: { atama: number; sefer: number; intikal: number }
  uyarili: { atama: number; gerekceler: OzetAdet[] }
  adalet: {
    n: number
    ortalamaSaat: number | null
    standartSapmaSaat: number | null
    minSaat: number | null
    maxSaat: number | null
  }
  fazlaMesai: { toplamSaat: number; makinistSayisi: number }
  belge: {
    etiket: string
    bugun: Tarih
    gecerli: number
    yaklasan: number
    dolmus: number
    girilmemis: number
  }
  degisiklik: { kayitSayisi: number; nedenler: OzetAdet[] }
  yedek: { acikGunSayisi: number; toplamAcik: number }
}

export const ozetApi = {
  getir: (ay: Ay) => api.get<YonetimOzeti>(`/api/rapor/ozet${sorguMetni({ ay })}`),
  denetimPaketi: (ay: Ay) => indir(`/api/rapor/denetim-paketi/excel${sorguMetni({ ay })}`),
}

/** Özet kaynak raporlarda hiçbir kayıt bulunmayan ay mı (boş dönem). */
export function bosDonem(o: YonetimOzeti): boolean {
  return (
    o.doluluk.toplamSefer === 0 && o.degisiklik.kayitSayisi === 0 && o.fazlaMesai.toplamSaat === 0
  )
}
