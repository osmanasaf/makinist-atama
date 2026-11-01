import type { RouteLocationRaw } from 'vue-router'
import type { Tarih } from '@/api/tipler'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import type { GunSatiri } from '@/features/atama/atamaApi'
import { masaBaglantisi } from '@/features/atama/masaBaglantisi'
import { kalanMetni, tarihBicimle } from '@/lib/tarih'

/** Henüz bitmemiş ve asıl makinisti olmayan seferler: sabah listesinde asıl iş. Bitmiş seferler atanmamış sayılmaz. */
export function atanmamisMi(s: GunSatiri, simdi: number): boolean {
  return (
    s.durum === 'PLANLI' &&
    !s.makinistler.some((m) => m.rol === 'ASIL') &&
    new Date(s.guncelVaris).getTime() > simdi
  )
}

/** Kaynakların ham sayıları; `null` = o kaynak okunamadı (satır çıkmaz, ana sayfa kartı hatayı gösterir). */
export interface YapilacakGirdi {
  simdi: number
  tarih: Tarih
  satirlar: GunSatiri[] | null
  kirmizi: { sefer: number; atama: number; intikal: number; ilkKirmiziGun?: Tarih | null } | null
  /** Bugün kalkışı geçmemiş atamalardan makinistin henüz görmediği (gördüm özeti). */
  gorulmeyen: number | null
  takas: number | null
  izin: number | null
  belge: { dolmus: number; yaklasan: number } | null
}

export interface Yapilacak {
  kimlik: 'kirmizi' | 'atanmamis' | 'gorulmeyen' | 'takas' | 'izin' | 'belge'
  ton: 'engel' | 'uyari' | 'bilgi'
  simge: SimgeAdi
  baslik: string
  ayrinti: string
  /** Tek eylemin düğme metni; eylem bir bağlantıdır (`hedef`). */
  eylem: string
  hedef: RouteLocationRaw
}

/** "Bugün yapılacaklar" kuyruğu (MK-156). Sabit öncelik: kırmızı → atanmamış → görmeyen → takas → izin → belge; sıfır olan çıkmaz. */
export function yapilacaklariOlustur(g: YapilacakGirdi): Yapilacak[] {
  const l: Yapilacak[] = []
  const k = g.kirmizi
  if (k && (k.sefer > 0 || k.intikal > 0)) {
    const gun = k.ilkKirmiziGun ?? g.tarih
    const parcalar = [
      k.sefer > 0 ? `${k.sefer} sefer kontrol bekliyor` : '',
      k.intikal > 0 ? `${k.intikal} kırmızı intikal` : '',
    ].filter(Boolean)
    l.push({
      kimlik: 'kirmizi',
      ton: 'engel',
      simge: 'engel',
      baslik: parcalar.join(' · '),
      ayrinti:
        k.sefer > 0
          ? `${k.atama} atama · ilk kırmızı gün ${tarihBicimle(gun)}`
          : 'Serbest intikaller kural motoruna takıldı',
      eylem: 'Kırmızıları gör',
      hedef: masaBaglantisi(gun, k.sefer > 0 ? 'kirmizi' : undefined),
    })
  }
  const bos = (g.satirlar ?? []).filter((s) => atanmamisMi(s, g.simdi))
  if (bos.length > 0) {
    const kalkislar = bos.map((s) => new Date(s.guncelKalkis).getTime())
    const kalkisiGecen = kalkislar.filter((k) => k <= g.simdi).length
    const gelecek = kalkislar.filter((k) => k > g.simdi)
    // Sayı "henüz bitmeyen" seferlerdir: kalkışı geçenler yoldadır ama hâlâ atanmamıştır (OK-41a).
    const ayrinti = [
      kalkisiGecen > 0 ? `${kalkisiGecen} seferin kalkışı geçti, henüz bitmedi` : '',
      gelecek.length > 0 ? `En yakın kalkış ${kalanMetni(Math.min(...gelecek), g.simdi)}` : '',
    ]
      .filter(Boolean)
      .join(' · ')
    l.push({
      kimlik: 'atanmamis',
      ton: 'uyari',
      simge: 'daire',
      baslik: `${bos.length} sefer atanmamış`,
      ayrinti,
      eylem: 'Atamaya başla',
      hedef: masaBaglantisi(g.tarih, 'bitmeyen'), // panodaki sayıyla aynı küme (bitenler hariç)
    })
  }
  if (g.gorulmeyen) {
    l.push({
      kimlik: 'gorulmeyen',
      ton: 'uyari',
      simge: 'kisi',
      baslik: `${g.gorulmeyen} atamayı makinist henüz görmedi`,
      ayrinti: 'Bugün kalkışı geçmemiş atamalar',
      eylem: 'Masada gör',
      hedef: masaBaglantisi(g.tarih, 'gormeyen'),
    })
  }
  if (g.takas) {
    l.push({
      kimlik: 'takas',
      ton: 'uyari',
      simge: 'takas',
      baslik: `${g.takas} takas talebi onay bekliyor`,
      ayrinti: 'Atamacı onayı gerekiyor',
      eylem: 'Talepleri aç',
      hedef: { name: 'takas' },
    })
  }
  if (g.izin) {
    l.push({
      kimlik: 'izin',
      ton: 'bilgi',
      simge: 'izin',
      baslik: `${g.izin} izin talebi karar bekliyor`,
      ayrinti: 'Onay ya da red bekleyen talepler',
      eylem: 'İzinleri aç',
      hedef: { name: 'izin' },
    })
  }
  const b = g.belge
  if (b && (b.dolmus > 0 || b.yaklasan > 0)) {
    l.push({
      kimlik: 'belge',
      ton: b.dolmus > 0 ? 'engel' : 'uyari',
      simge: 'belge',
      baslik:
        b.dolmus > 0
          ? `${b.dolmus} belgenin süresi doldu`
          : `${b.yaklasan} belgenin süresi yaklaşıyor`,
      ayrinti: b.dolmus > 0 ? `Süresi yaklaşan: ${b.yaklasan}` : 'Aktif personel',
      eylem: 'Belge durumu',
      hedef: { name: 'belgeler' },
    })
  }
  return l
}
