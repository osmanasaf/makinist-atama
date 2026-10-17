import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/rapor/puantaj',
    name: 'rapor-puantaj',
    component: () => import('./PuantajRaporu.vue'),
    meta: { menu: { ad: 'Puantaj', sira: 48 }, baslik: 'Puantaj ön hesabı' },
  },
  {
    path: '/yazdir/puantaj',
    name: 'yazdir-puantaj',
    component: () => import('./PuantajYazdir.vue'),
    // Süzgeç adres sorgusundan gelir: ?ay=YYYY-MM&makinist=<id>&tur=KONDUKTOR
    props: (r) => {
      const q = (k: string) => (typeof r.query[k] === 'string' ? (r.query[k] as string) : undefined)
      return { ay: q('ay'), makinist: q('makinist'), tur: q('tur') }
    },
    meta: { baslik: 'Puantaj ön hesabı', yazdir: true },
  },
]

export default routes
