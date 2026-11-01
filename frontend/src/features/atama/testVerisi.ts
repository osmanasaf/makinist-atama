// Yalnız testler için ortak örnek veri (üretim kodu içe aktarmaz).
import type { DOMWrapper } from '@vue/test-utils'
import type { GunMakinisti, GunSatiri } from './atamaApi'

export const makinist = (ek: Partial<GunMakinisti> = {}): GunMakinisti => ({
  atamaId: 10,
  rol: 'ASIL',
  makinistId: 1,
  sicil: 'D001',
  ad: 'Ali Veli',
  kirmizi: false,
  kirmiziNedenler: [],
  uyarilar: [],
  ...ek,
})

export const satir = (seferId: number, ek: Partial<GunSatiri> = {}): GunSatiri => ({
  seferId,
  trenNo: `T${1000 + seferId}`,
  kalkisGar: 'Ankara',
  varisGar: 'Eskişehir',
  planlananKalkis: '2026-10-12T05:30:00Z',
  planlananVaris: '2026-10-12T07:00:00Z',
  guncelKalkis: '2026-10-12T05:30:00Z', // İstanbul 08:30
  guncelVaris: '2026-10-12T07:00:00Z',
  kalkisGecikmeDk: 0,
  varisGecikmeDk: 0,
  aracTipi: 'YHT',
  hat: 'Ankara–Eskişehir',
  km: 230,
  durum: 'PLANLI',
  kirmizi: false,
  makinistler: [],
  bosNeden: null,
  ...ek,
})

/** K7 (MK-148): menüye taşınan satır eylemini tetikler. ⋯ menüsünü açar, `aria-label` sonu `son` olan öğeye tıklar (ör. 'gecikme gir', 'iptal et', 'seferini böl'). */
export async function eylemTetikle(satirKabi: DOMWrapper<Element>, son: string): Promise<void> {
  await satirKabi.get('button[aria-haspopup="menu"]').trigger('click')
  await satirKabi.get(`[role="menuitem"][aria-label$="${son}"]`).trigger('click')
}
