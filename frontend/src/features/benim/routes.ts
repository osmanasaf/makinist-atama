import type { RouteRecordRaw } from 'vue-router'

// Makinistin kendi ekranları (rol 'M': yalnız MAKINIST; gerçek koruma sunucuda). Menüsü BenimKabuk'un alt sekmeleridir (masaüstü menüsüne girmez).
const routes: RouteRecordRaw[] = [
  { path: '/benim', redirect: '/benim/gorevler' },
  {
    path: '/benim/gorevler',
    name: 'benim-gorevler',
    component: () => import('./BenimGorevler.vue'),
    meta: { rol: 'M', baslik: 'Görevlerim' },
  },
  {
    path: '/benim/belgeler',
    name: 'benim-belgeler',
    component: () => import('./BenimBelgeler.vue'),
    meta: { rol: 'M', baslik: 'Belgelerim' },
  },
  {
    path: '/benim/izin',
    name: 'benim-izin',
    component: () => import('./BenimIzinler.vue'),
    meta: { rol: 'M', baslik: 'İzinlerim' },
  },
  {
    path: '/benim/ozet',
    name: 'benim-ozet',
    component: () => import('./BenimOzet.vue'),
    meta: { rol: 'M', baslik: 'Aylık özetim' },
  },
  {
    path: '/benim/takas',
    name: 'benim-takas',
    component: () => import('./BenimTakas.vue'),
    meta: { rol: 'M', baslik: 'Takas taleplerim' },
  },
  {
    path: '/benim/konaklama',
    name: 'benim-konaklama',
    component: () => import('./BenimKonaklama.vue'),
    meta: { rol: 'M', baslik: 'Konaklamam' },
  },
  {
    path: '/benim/parola',
    name: 'benim-parola',
    component: () => import('./BenimParola.vue'),
    meta: { rol: 'M', baslik: 'Parolam' },
  },
]

export default routes
