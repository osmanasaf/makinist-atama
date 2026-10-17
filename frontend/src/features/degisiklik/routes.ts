import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/degisiklik',
    name: 'degisiklik',
    component: () => import('./DegisiklikRaporu.vue'),
    meta: { menu: { ad: 'Denetim Raporu', sira: 50 }, baslik: 'Denetim raporu' },
  },
]

export default routes
