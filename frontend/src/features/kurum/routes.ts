import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/kurum',
    name: 'kurum',
    component: () => import('./KurumSayfasi.vue'),
    meta: { rol: 'Y', baslik: 'Kurum bilgisi' },
  },
]

export default routes
