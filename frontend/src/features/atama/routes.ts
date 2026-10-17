import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/atama',
    name: 'atama',
    component: () => import('./AtamaMasasi.vue'),
    meta: { menu: { ad: 'Atama Masası', sira: 25 }, baslik: 'Atama masası' },
  },
]

export default routes
