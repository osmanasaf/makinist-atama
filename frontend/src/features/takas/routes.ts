import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/takas',
    name: 'takas',
    component: () => import('./TakasListesi.vue'),
    meta: {
      menu: { ad: 'Takas Talepleri', sira: 26, grup: 'Atama', simge: 'takas' },
      baslik: 'Takas talepleri',
    },
  },
]

export default routes
