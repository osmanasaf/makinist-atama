/** Simge seti (MK-132): ad -> SVG yol dizesi (20x20, çizgi 1,75). Tavan 36 simge (menü öğelerinin her birine benzersiz simge için 30 → 36); yeni simge eklemeden önce var olan kullanılır.
 * `temel.css` içindeki 4 bant simgesi (bilgi/uyari/engel/basari) bu dizelerin kopyasıdır (CSS mask data-URI). */
export const SIMGELER = {
  engel: 'M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 0 0 0-15zM4.7 4.7l10.6 10.6',
  uyari: 'M10 2.8L18 16.5H2zM10 8v4M10 14.3v.1',
  bilgi: 'M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 0 0 0-15zM10 9v5M10 6.3v.1',
  basari: 'M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 0 0 0-15zM6.5 10.2l2.4 2.4 4.6-4.9',
  saat: 'M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 0 0 0-15zM10 5.5V10l3 2',
  gecikme:
    'M5 2.5h10M5 17.5h10M6 2.5c0 4 4 4.5 4 7.5s-4 3.5-4 7.5M14 2.5c0 4-4 4.5-4 7.5s4 3.5 4 7.5',
  iptal: 'M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 0 0 0-15zM7.3 7.3l5.4 5.4M12.7 7.3l-5.4 5.4',
  ay: 'M16.5 11.5A7 7 0 1 1 8.5 3.5a5.5 5.5 0 0 0 8 8z',
  gar: 'M3 17.5h14M4.5 17.5V8.5L10 4l5.5 4.5v9M8 17.5v-4h4v4',
  tren: 'M5 3h10a1.5 1.5 0 0 1 1.5 1.5v8A2.5 2.5 0 0 1 14 15H6a2.5 2.5 0 0 1-2.5-2.5v-8A1.5 1.5 0 0 1 5 3zM3.5 9h13M6 18l1.7-3M14 18l-1.7-3M7 12v.1M13 12v.1',
  kisi: 'M10 9.5a3.2 3.2 0 1 0 0-6.4a3.2 3.2 0 0 0 0 6.4zM3.5 17c0-3.2 2.9-5.5 6.5-5.5s6.5 2.3 6.5 5.5',
  belge:
    'M5 2.5h6.5l4 4V17a.5.5 0 0 1-.5.5H5a.5.5 0 0 1-.5-.5V3a.5.5 0 0 1 .5-.5zM11.5 2.5v4h4M7 10h6M7 13h6',
  izin: 'M3.5 5h13a.5.5 0 0 1 .5.5v11a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-11a.5.5 0 0 1 .5-.5zM3 9h14M7 3v3M13 3v3',
  takas: 'M4 7h12l-3-3M16 13H4l3 3',
  yolcu: 'M3 6.5h14v2a1.5 1.5 0 0 0 0 3v2H3v-2a1.5 1.5 0 0 0 0-3zM12 6.5v7',
  yazdir: 'M6 7V3h8v4M6 14H3.5v-6h13v6H14M6 11.5h8v5.5H6z',
  indir: 'M10 3v9M6.5 8.5L10 12l3.5-3.5M3.5 16.5h13',
  ekle: 'M10 4v12M4 10h12',
  sil: 'M4 5.5h12M8 5.5V3.5h4v2M5.5 5.5l.7 11h7.6l.7-11M8.5 9v5M11.5 9v5',
  'ok-sol': 'M12 4l-6 6 6 6',
  'ok-sag': 'M8 4l6 6-6 6',
  kapat: 'M5 5l10 10M15 5L5 15',
  ara: 'M8.5 14.5a6 6 0 1 0 0-12a6 6 0 0 0 0 12zM13 13l4.5 4.5',
  ev: 'M3 10L10 3.5 17 10M5 8.5V17h10V8.5M8.5 17v-4.5h3V17',
  ayarlar: 'M4 6h7M15 6h1M4 14h1M9 14h7M13 3.5v5M7 11.5v5',
  rapor: 'M4 17V9M10 17V4M16 17v-6M2.5 17.5h15',
  menu: 'M3.5 5.5h13M3.5 10h13M3.5 14.5h13',
  bol: 'M10 3v14M5 7L2.5 10 5 13M15 7l2.5 3-2.5 3',
  birlestir: 'M3.5 4.5c3 0 6.5 1 6.5 5.5v7M16.5 4.5c-3 0-6.5 1-6.5 5.5M7.5 14.5L10 17l2.5-2.5',
  daire: 'M10 3.5a6.5 6.5 0 1 0 0 13a6.5 6.5 0 0 0 0-13z',
  ray: 'M6.5 2.5L4.5 17.5M13.5 2.5l2 15M5.4 7h9.2M4.8 12h10.4',
  yatak: 'M2.5 4v12M2.5 12.5h15V16M17.5 12.5V9a2 2 0 0 0-2-2H8.5v5.5M5.2 10.3a1.2 1.2 0 1 0 0-.1',
  kadro:
    'M7.5 9a2.8 2.8 0 1 0 0-5.6a2.8 2.8 0 0 0 0 5.6zM2.5 16.5c0-2.8 2.2-4.7 5-4.7s5 1.9 5 4.7M13 3.8a2.8 2.8 0 0 1 0 5.2M14.5 12c1.7.5 3 2 3 4.5',
  etiket: 'M3 3h6.5l7.5 7.5-6.5 6.5L3 9.5zM6.5 6.5v.1',
  'telefon-yok':
    'M7 2.5h6a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1zM9 15h2M3 3l14 14',
  kural: 'M10 2.5l6 2.2v5c0 3.8-2.6 6.2-6 7.8-3.4-1.6-6-4-6-7.8v-5zM7.3 10l2 2 3.4-3.6',
} as const

export type SimgeAdi = keyof typeof SIMGELER
