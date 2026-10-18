import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/degisiklik',
    name: 'degisiklik',
    component: () => import('./DegisiklikRaporu.vue'),
    meta: {
      menu: { ad: 'Denetim Raporu', sira: 50, grup: 'Raporlar', simge: 'ara' },
      baslik: 'Denetim raporu',
    },
  },
]

export default routes
