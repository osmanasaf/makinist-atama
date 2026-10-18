import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/tanimlar',
    name: 'tanimlar',
    component: () => import('./TanimSayfasi.vue'),
    // okuma herkese açık; yazma düğmeleri sayfada rol ile gizlenir
    meta: { menu: { ad: 'Tanımlar', sira: 30, grup: 'Ayarlar', simge: 'etiket' } },
  },
]

export default routes
