import {
  createRouter,
  createWebHashHistory,
  type RouteRecordRaw,
  type Router,
  type RouterHistory,
} from 'vue-router'
import { cikisKorumasiniBagla } from '@/composables/cikis-korumasi'
import { kullanici, makinistMi, oturumYukle, yoneticiMi, yuklendi } from '@/composables/oturum'

/** G = giriş yapmış personel, Y = yalnız yönetici, M = yalnız makinist (yalnız UX; gerçek koruma sunucuda). */
type ErisimRolu = 'G' | 'Y' | 'M'

declare module 'vue-router' {
  interface RouteMeta {
    /** Oturum gerektirmez (giriş sayfası). */
    acik?: boolean
    /** 'Y' = yalnız yönetici, 'M' = yalnız makinist. Yalnız UX koruması; gerçek koruma sunucuda. */
    rol?: ErisimRolu
    /** Üst menüde görünür (rol uymayan çizilmez). */
    menu?: { ad: string; sira: number }
    /** Sayfa başlığı (<title>); yoksa menü adı, o da yoksa uygulama adı. Sayfa kendi başlığını (ör. makinist adı) kendisi yazabilir. */
    baslik?: string
    /** Yazdırma sayfası: üst çubuk ve sayfa kenar boşluğu gösterilmez (menüsüz düzen). */
    yazdir?: boolean
  }
}

// Route'lar features/<ad>/routes.ts dosyalarından otomatik toplanır.
const moduller = import.meta.glob<{ default: RouteRecordRaw[] }>('../features/*/routes.ts', {
  eager: true,
})
const kayitliRoutelar = Object.values(moduller).flatMap((m) => m.default)

const UYGULAMA_ADI = 'Makinist Atama'

/** Oturum düştü (herhangi bir istekte 401): giriş sayfasına dönüş adresiyle gider. Zaten giriş sayfasındaysa hiçbir şey yapmaz (iç içe `git` olmaz). */
export async function oturumDustu(router: Router): Promise<void> {
  const simdi = router.currentRoute.value
  if (simdi.name === 'giris') return
  await router.replace({ name: 'giris', query: { git: simdi.fullPath, oturum: 'bitti' } })
}

export function yonlendiriciKur(
  routelar: RouteRecordRaw[],
  history: RouterHistory = createWebHashHistory(),
) {
  const router = createRouter({
    history,
    routes: [...routelar, { path: '/:yol(.*)*', redirect: '/' }],
  })

  cikisKorumasiniBagla(router) // kaydedilmemiş form/tablo verisi varken gezinme onay ister

  // Sayfa başlığı. Aynı sayfada yalnız sorgu değişince (sekme geçişi) dokunulmaz: sayfa kendi başlığını (makinist adı) yazmış olabilir.
  router.afterEach((to, from) => {
    if (from.matched.length > 0 && to.path === from.path) return
    const baslik = to.meta.baslik ?? to.meta.menu?.ad
    document.title = baslik ? `${baslik} · ${UYGULAMA_ADI}` : UYGULAMA_ADI
  })

  router.beforeEach(async (to) => {
    if (to.meta.acik) {
      if (to.name !== 'giris') return true
      if (!yuklendi.value) await oturumYukle()
      return kullanici.value ? { path: '/' } : true
    }
    if (!yuklendi.value) await oturumYukle()
    if (!kullanici.value) return { name: 'giris', query: { git: to.fullPath } }
    if (makinistMi.value) {
      // makinist yalnız kendi ekranlarını görür; geçici parola bayrağı açıksa yalnız parola değiştirme ekranı
      if (kullanici.value.parolaDegistirmeli)
        return to.name === 'benim-parola' ? true : { name: 'benim-parola' }
      return to.meta.rol === 'M' ? true : { path: '/benim' }
    }
    if (to.meta.rol === 'M') return { path: '/' }
    if (to.meta.rol === 'Y' && !yoneticiMi.value) return { path: '/' }
    return true
  })
  return router
}

export default yonlendiriciKur(kayitliRoutelar)
