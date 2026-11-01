import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/ayarlar',
    name: 'ayarlar',
    component: () => import('./AyarlarSayfasi.vue'),
    meta: { menu: { ad: 'Ayarlar', sira: 40, grup: 'Ayarlar', simge: 'ayarlar' }, rol: 'Y' },
  },
  {
    path: '/profil',
    name: 'profil',
    component: () => import('./ProfilSayfasi.vue'),
    meta: { menu: { ad: 'Kural profili', sira: 47, grup: 'Ayarlar', simge: 'indir' }, rol: 'Y' },
  },
  // Eski adres (yer imleri): menüde görünmez.
  { path: '/parametreler', redirect: '/ayarlar' },
  {
    path: '/ozel-kurallar',
    name: 'ozel-kurallar',
    component: () => import('./OzelKuralSayfasi.vue'),
    meta: {
      menu: { ad: 'Özel kurallar', sira: 45, grup: 'Ayarlar', simge: 'kural' },
      baslik: 'Özel kurallar',
    },
  },
]

export default routes
