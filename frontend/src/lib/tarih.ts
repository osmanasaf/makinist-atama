import type { Tarih, Zaman } from '@/api/tipler'

/** "2026-10-30" → "30.10.2026" (dize işler: saat dilimi kayması yok). */
export function tarihBicimle(tarih: Tarih | null | undefined): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(tarih ?? '')
  return m ? `${m[3]}.${m[2]}.${m[1]}` : ''
}

const zamanBicimleyici = new Intl.DateTimeFormat('tr-TR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Europe/Istanbul', // sunucu saat dilimiyle aynı: ofisteki herkes aynı saati görür
})

/** "2026-10-31T22:00:00Z" → "01.11.2026 01:00" (gg.aa.yyyy ss:dd, Europe/Istanbul). */
export function zamanBicimle(zaman: Zaman | null | undefined): string {
  if (!zaman) return ''
  const d = new Date(zaman)
  // tr-TR çıktısı "01.11.2026 01:00" ya da "01.11.2026, 01:00" olabilir: virgülü at.
  return Number.isNaN(d.getTime()) ? zaman : zamanBicimleyici.format(d).replace(',', '')
}

const gunBicimleyici = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Istanbul' })

/** Bugünün tarihi "2026-10-09" (Europe/Istanbul: sunucuyla aynı gün; tarih alanlarının varsayılanı). */
export function bugun(): Tarih {
  return gunBicimleyici.format(new Date())
}

const saatBicimleyici = new Intl.DateTimeFormat('tr-TR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Europe/Istanbul',
})

/** "2026-10-12T05:30:00Z" → "08:30" (ss:dd, Europe/Istanbul). Boşta '' , bozukta girdiyi döner. */
export function saatBicimle(zaman: Zaman | null | undefined): string {
  if (!zaman) return ''
  const d = new Date(zaman)
  return Number.isNaN(d.getTime()) ? zaman : saatBicimleyici.format(d)
}

/** "2026-10-31" + 1 → "2026-11-01" (UTC üzerinden: saat dilimi/yaz saati kayması yok). */
export function gunKaydir(tarih: Tarih, gun: number): Tarih {
  const d = new Date(`${tarih}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + gun)
  return d.toISOString().slice(0, 10)
}
