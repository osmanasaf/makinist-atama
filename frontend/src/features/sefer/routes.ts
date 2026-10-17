import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/seferler',
    name: 'seferler',
    component: () => import('./SeferListesi.vue'),
    meta: { menu: { ad: 'Seferler', sira: 20 } },
  },
  {
    path: '/seferler/yukle',
    name: 'sefer-yukle',
    component: () => import('./SeferYukleme.vue'),
    meta: { baslik: 'Sefer yükleme' },
  },
]

export default routes
