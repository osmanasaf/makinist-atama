import type { SimgeAdi } from './simgeler'

/** Menü grupları: sıra sabit; başlıksız (null) grup en üsttedir (MK-133). */
export const MENU_GRUPLARI = ['Atama', 'Personel', 'Raporlar', 'Ayarlar'] as const
export type MenuGrup = (typeof MENU_GRUPLARI)[number]

export interface MenuRotasi {
  path: string
  meta: {
    rol?: string
    menu?: { ad: string; sira: number; grup?: string; simge?: SimgeAdi }
  }
}
export interface MenuOgesi {
  ad: string
  yol: string
  simge?: SimgeAdi
}
export interface MenuGrubu {
  /** null = başlıksız üst grup. */
  grup: string | null
  ogeler: MenuOgesi[]
}

/** Rotaları menü gruplarına ayırır: rol süzgeci (`rol:'Y'` yalnız yönetici), grup içinde `sira` (eşitlikte yol), boş grup yok. */
export function gruplaMenu(rotalar: readonly MenuRotasi[], yoneticiMi: boolean): MenuGrubu[] {
  const gorunur = rotalar
    .filter((r) => r.meta.menu && (r.meta.rol !== 'Y' || yoneticiMi))
    .sort((a, b) => a.meta.menu!.sira - b.meta.menu!.sira || a.path.localeCompare(b.path))
  const gruplar = new Map<string | null, MenuOgesi[]>()
  for (const r of gorunur) {
    const m = r.meta.menu!
    const anahtar = m.grup ?? null
    const liste = gruplar.get(anahtar) ?? []
    liste.push({ ad: m.ad, yol: r.path, simge: m.simge })
    gruplar.set(anahtar, liste)
  }
  const bilinen: (string | null)[] = [null, ...MENU_GRUPLARI]
  const sira = [...bilinen, ...[...gruplar.keys()].filter((g) => !bilinen.includes(g))]
  return sira.filter((g) => gruplar.has(g)).map((g) => ({ grup: g, ogeler: gruplar.get(g)! }))
}
