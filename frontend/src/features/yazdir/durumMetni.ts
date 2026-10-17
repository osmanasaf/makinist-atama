import { saatBicimle } from '@/lib/tarih'
import type { GunSatiri } from '@/features/atama/atamaApi'

type DurumGirdisi = Pick<GunSatiri, 'durum' | 'makinistler' | 'kalkisGecikmeDk' | 'guncelKalkis'>

/** "+90 dk (yeni kalkış 08:30)"; gecikme yoksa ''. */
export function gecikmeMetni(s: Pick<GunSatiri, 'kalkisGecikmeDk' | 'guncelKalkis'>): string {
  return s.kalkisGecikmeDk > 0
    ? `+${s.kalkisGecikmeDk} dk (yeni kalkış ${saatBicimle(s.guncelKalkis)})`
    : ''
}

/** Renksiz okunur durum metni: "İPTAL" ya da " · " ile "KONTROL", "BOŞ", "+90 dk (yeni kalkış 08:30)"; hiçbiri yoksa ''. */
export function durumMetni(s: DurumGirdisi): string {
  if (s.durum === 'IPTAL') return 'İPTAL'
  const parcalar: string[] = []
  if (s.makinistler.some((m) => m.kirmizi)) parcalar.push('KONTROL')
  if (!s.makinistler.some((m) => m.rol === 'ASIL')) parcalar.push('BOŞ')
  const gecikme = gecikmeMetni(s)
  if (gecikme) parcalar.push(gecikme)
  return parcalar.join(' · ')
}
