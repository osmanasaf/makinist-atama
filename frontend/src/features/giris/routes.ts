import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/giris',
    name: 'giris',
    component: () => import('./GirisSayfasi.vue'),
    meta: { acik: true, baslik: 'Giriş' },
  },
]

export default routes
