import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/belgeler',
    name: 'belgeler',
    component: () => import('./BelgePanosu.vue'),
    meta: { menu: { ad: 'Belgeler', sira: 28, grup: 'Personel', simge: 'belge' } },
  },
]

export default routes
