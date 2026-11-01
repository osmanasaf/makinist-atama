import type { YonetimOzeti } from './ozetApi'
import type { AdaletRaporu } from './raporApi'

export const adaletYaniti: AdaletRaporu = {
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z', // İstanbul 10:20
  ureten: 'Mehmet Demir',
  makinistSayisi: 3,
  ortalamaSaat: 10,
  ortalamaKm: 500.5,
  kmBilinmeyenSefer: 2,
  olcut: 'Atanmış sefer süresi (saat)',
  geceSaatiPenceresi: '20:00–06:00',
  satirlar: [
    {
      makinistId: 1,
      sicil: 'D001',
      adSoyad: 'Ali Veli',
      seferSayisi: 4,
      gorevSaat: 16,
      km: 800,
      kmBilinmeyenSefer: 1,
      geceSaat: 3.5,
      izinGunu: 0,
      farkYuzde: 60,
      sifirSaat: false,
    },
    {
      makinistId: 2,
      sicil: 'D002',
      adSoyad: 'Ayşe Kaya',
      seferSayisi: 0,
      gorevSaat: 0,
      km: 0,
      kmBilinmeyenSefer: 0,
      geceSaat: 0,
      izinGunu: 5,
      farkYuzde: -100,
      sifirSaat: true,
    },
    {
      makinistId: 3,
      sicil: 'D003',
      adSoyad: 'Can Aydın',
      seferSayisi: 2,
      gorevSaat: 14,
      km: 701.25,
      kmBilinmeyenSefer: 1,
      geceSaat: 0,
      izinGunu: 1,
      farkYuzde: null,
      sifirSaat: false,
    },
  ],
}

/** Yönetim özeti örneği (F4): 3 makinistlik adalet, 9 seferlik doluluk. */
export const ornek = (ek: Partial<YonetimOzeti> = {}): YonetimOzeti => ({
  ay: '2026-10',
  uretimZamani: '2026-10-12T07:20:00Z', // İstanbul 10:20
  ureten: 'Mehmet Demir',
  demo: false,
  doluluk: { toplamSefer: 9, dolu: 6, bos: 3, yuzde: 66.7 },
  kirmizi: { atama: 4, sefer: 3, intikal: 1 },
  uyarili: {
    atama: 3,
    gerekceler: [
      { ad: 'geçmiş kaydın düzeltilmesi', adet: 2 },
      { ad: 'başka uygun makinist yok', adet: 1 },
    ],
  },
  adalet: { n: 3, ortalamaSaat: 20, standartSapmaSaat: 8.16, minSaat: 10, maxSaat: 30 },
  fazlaMesai: { toplamSaat: 12.5, makinistSayisi: 2 },
  belge: {
    etiket: 'Bugünkü durum',
    bugun: '2026-10-12',
    gecerli: 5,
    yaklasan: 2,
    dolmus: 1,
    girilmemis: 0,
  },
  degisiklik: {
    kayitSayisi: 40,
    nedenler: [
      { ad: 'HASTALIK', adet: 3 },
      { ad: 'GECIKME', adet: 1 },
    ],
  },
  yedek: { acikGunSayisi: 4, toplamAcik: 7 },
  ...ek,
})
