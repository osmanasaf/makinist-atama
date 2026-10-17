// Yalnız testler için ortak örnek veri (üretim kodu içe aktarmaz).
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
