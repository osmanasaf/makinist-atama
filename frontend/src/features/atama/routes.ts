import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/atama',
    name: 'atama',
    component: () => import('./AtamaMasasi.vue'),
    meta: {
      menu: { ad: 'Atama Masası', sira: 25, grup: 'Atama', simge: 'tren' },
      baslik: 'Atama masası',
      tam: true,
    },
  },
]

export default routes
