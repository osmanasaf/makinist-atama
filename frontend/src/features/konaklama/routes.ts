import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/konaklama',
    name: 'konaklama',
    component: () => import('./KonaklamaSayfasi.vue'),
    meta: {
      menu: { ad: 'Konaklama', sira: 47, grup: 'Atama', simge: 'yatak' },
      baslik: 'Konaklama',
    },
  },
  {
    path: '/konaklama/rapor',
    name: 'konaklama-rapor',
    component: () => import('./KonaklamaRaporu.vue'),
    meta: {
      menu: { ad: 'Konaklama Raporu', sira: 48, grup: 'Raporlar', simge: 'yatak-belge' },
      baslik: 'Konaklama raporu',
    },
  },
]

export default routes
