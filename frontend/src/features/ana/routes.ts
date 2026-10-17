import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'ana',
    component: () => import('./AnaSayfa.vue'),
    meta: { menu: { ad: 'Ana Sayfa', sira: 0 } },
  },
]

export default routes
