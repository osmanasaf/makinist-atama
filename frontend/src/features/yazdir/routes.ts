import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/yazdir/gun/:tarih([0-9]{4}-[0-9]{2}-[0-9]{2})',
    name: 'yazdir-gun',
    component: () => import('./GunListesiYazdir.vue'),
    props: true,
    meta: { baslik: 'Günlük atama listesi', yazdir: true },
  },
  {
    path: '/yazdir/degisiklik',
    name: 'yazdir-degisiklik',
    component: () => import('./DegisiklikYazdir.vue'),
    // Süzgeç adres sorgusundan gelir: ?bas=&bit=&trenNo=&kullanici=&tur=
    props: (r) => {
      const q = (k: string) => (typeof r.query[k] === 'string' ? (r.query[k] as string) : undefined)
      return {
        bas: q('bas'),
        bit: q('bit'),
        trenNo: q('trenNo'),
        kullanici: q('kullanici'),
        tur: q('tur'),
      }
    },
    meta: { baslik: 'Değişiklik (denetim) raporu', yazdir: true },
  },
  {
    path: '/yazdir/cizelge',
    name: 'yazdir-cizelge',
    component: () => import('./CizelgeYazdir.vue'),
    // F5 ilan baskısı: ?ay=YYYY-MM&boyut=A3|A4&tur=KONDUKTOR&garId=
    props: (r) => {
      const q = (k: string) => (typeof r.query[k] === 'string' ? (r.query[k] as string) : undefined)
      return { ay: q('ay'), boyut: q('boyut'), tur: q('tur'), garId: q('garId') }
    },
    meta: { baslik: 'Aylık çizelge', yazdir: true },
  },
  {
    path: '/yazdir/ozet',
    name: 'yazdir-ozet',
    component: () => import('./YonetimOzetiYazdir.vue'),
    // F4 yönetim özeti baskısı: ?ay=YYYY-MM (yalnız yönetici; sunucu da 403 verir)
    props: (r) => ({ ay: typeof r.query.ay === 'string' ? r.query.ay : undefined }),
    meta: { baslik: 'Yönetim özeti', yazdir: true, rol: 'Y' },
  },
]

export default routes
