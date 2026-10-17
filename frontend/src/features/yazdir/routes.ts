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
]

export default routes
