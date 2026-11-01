import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/kurulum',
    name: 'kurulum',
    component: () => import('./KurulumSihirbazi.vue'),
    meta: {
      menu: { ad: 'Kurulum', sira: 36, grup: 'Ayarlar', simge: 'ekle' },
      rol: 'Y',
      baslik: 'İlk kurulum',
    },
  },
]

export default routes
