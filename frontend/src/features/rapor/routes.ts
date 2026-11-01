import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/rapor/adalet',
    name: 'rapor-adalet',
    component: () => import('./AdaletRaporu.vue'),
    meta: {
      menu: { ad: 'Adalet Raporu', sira: 42, grup: 'Raporlar', simge: 'rapor' },
      baslik: 'Adalet raporu',
    },
  },
  {
    path: '/rapor/ozet',
    name: 'rapor-ozet',
    component: () => import('./YonetimOzeti.vue'),
    // F4: mevcut raporların sayıları tek sayfada + denetim paketi; yalnız yönetici (sunucu da 403 verir)
    meta: {
      menu: { ad: 'Yönetim Özeti', sira: 40, grup: 'Raporlar', simge: 'bilgi' },
      baslik: 'Yönetim özeti',
      rol: 'Y',
    },
  },
  {
    path: '/rapor/cizelge',
    name: 'rapor-cizelge',
    component: () => import('./AylikCizelge.vue'),
    // F5: salt okunur aylık ızgara; yalnız personel (makinist kabuğunda bu rota yok, sunucu 403)
    meta: {
      menu: { ad: 'Aylık Çizelge', sira: 41, grup: 'Raporlar', simge: 'gar' },
      baslik: 'Aylık çizelge',
    },
  },
  {
    path: '/rapor/yedek',
    name: 'rapor-yedek',
    component: () => import('./YedekTakvimi.vue'),
    meta: {
      menu: { ad: 'Yedek Planı', sira: 44, grup: 'Raporlar', simge: 'saat' },
      baslik: 'Yedek planı',
    },
  },
  {
    path: '/rapor/kadro',
    name: 'rapor-kadro',
    component: () => import('./KadroTahmini.vue'),
    meta: {
      menu: { ad: 'Kadro İhtiyacı', sira: 49, grup: 'Raporlar', simge: 'kadro' },
      baslik: 'Kadro ihtiyacı tahmini',
      rol: 'Y',
    },
  },
  {
    path: '/izin',
    name: 'izin',
    component: () => import('./IzinSayfasi.vue'),
    meta: { menu: { ad: 'İzinler', sira: 46, grup: 'Personel', simge: 'izin' }, baslik: 'İzinler' },
  },
  {
    path: '/yazdir/adalet',
    name: 'yazdir-adalet',
    component: () => import('./AdaletYazdir.vue'),
    // Ay ve (kondüktör için) tür adres sorgusundan gelir: ?ay=YYYY-MM&tur=KONDUKTOR
    props: (r) => ({
      ay: typeof r.query.ay === 'string' ? r.query.ay : undefined,
      tur: typeof r.query.tur === 'string' ? r.query.tur : undefined,
    }),
    meta: { baslik: 'Adalet raporu', yazdir: true },
  },
]

export default routes
