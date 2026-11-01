// Kural profili fark tablosunun saf yardımcıları (F2, MK-163): durum -> görünüm (durum.ts), varsayılan görünürlük/seçim, gruplama. Vue import etmez.
import { durumOzellikleri, type DurumOzellik } from '@/bilesenler/durum'
import type { ProfilSatiri, SatirDurumu, SatirTuru } from './profilApi'

export const TUR_BASLIKLARI: Record<SatirTuru, string> = {
  PARAMETRE: 'Kural değerleri',
  SIDDET: 'Kural şiddetleri',
  AYAR: 'Uygulama ayarları',
  OZEL_KURAL: 'Özel kurallar',
}
const TUR_SIRASI: SatirTuru[] = ['PARAMETRE', 'SIDDET', 'AYAR', 'OZEL_KURAL']

export const durumGorunumu = (durum: SatirDurumu): DurumOzellik => durumOzellikleri(durum)

/** Varsayılan görünüm yalnız işe yarayan satırlardır: değişecekler ve sorunlular ("Hepsini göster" gerisini açar). */
export const varsayilanGorunur = (durum: SatirDurumu): boolean =>
  durum === 'DEGISIR' || durum === 'GECERSIZ'

/** Yalnız değişecek satır seçilebilir (aynı/bilinmeyen/profilde olmayan için yazılacak bir şey yok; geçersiz uygulamayı engeller). */
export const secilebilir = (s: Pick<ProfilSatiri, 'durum'>): boolean => s.durum === 'DEGISIR'

/** İlk seçim: tüm DEGISIR satırlar işaretli gelir; kullanıcı tek tek çıkarır. */
export const varsayilanSecim = (satirlar: readonly ProfilSatiri[]): Set<string> =>
  new Set(satirlar.filter(secilebilir).map((s) => s.kimlik))

export function gorunenSatirlar(
  satirlar: readonly ProfilSatiri[],
  hepsiniGoster: boolean,
): ProfilSatiri[] {
  return hepsiniGoster ? [...satirlar] : satirlar.filter((s) => varsayilanGorunur(s.durum))
}

export interface SatirGrubu {
  tur: SatirTuru
  baslik: string
  satirlar: ProfilSatiri[]
}

export function gruplaSatirlar(satirlar: readonly ProfilSatiri[]): SatirGrubu[] {
  return TUR_SIRASI.map((tur) => ({
    tur,
    baslik: TUR_BASLIKLARI[tur],
    satirlar: satirlar.filter((s) => s.tur === tur),
  })).filter((g) => g.satirlar.length > 0)
}

/** Seçilenlerden uygulanamayanlar (sunucu 422 verir); ekran düğmeyi bunlarla devre dışı bırakır. */
export function secimGecersizMi(
  satirlar: readonly ProfilSatiri[],
  secili: ReadonlySet<string>,
): boolean {
  return satirlar.some((s) => secili.has(s.kimlik) && !secilebilir(s))
}

/** Boş metin gerçek bir değerdir (isteğe bağlı ayar boşaltılır): "(boş)"; olmayan değer "—". */
export const degerMetni = (v: string | null): string => (v === null ? '—' : v === '' ? '(boş)' : v)
