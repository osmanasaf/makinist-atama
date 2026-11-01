import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/makinistler',
    name: 'makinistler',
    component: () => import('./MakinistListesi.vue'),
    meta: { menu: { ad: 'Personel', sira: 10, grup: 'Personel', simge: 'kisi' } },
  },
  {
    path: '/makinistler/yeni',
    name: 'makinist-yeni',
    component: () => import('./MakinistKarti.vue'),
    meta: { baslik: 'Yeni personel' },
  },
  {
    path: '/makinistler/yukle',
    name: 'makinist-yukle',
    component: () => import('./MakinistYukleme.vue'),
    meta: { rol: 'Y', baslik: 'Personel yükleme' },
  },
  {
    path: '/makinistler/:id([0-9]+)',
    name: 'makinist-kart',
    component: () => import('./MakinistKarti.vue'),
    meta: { baslik: 'Personel kartı' },
  },
]

export default routes
