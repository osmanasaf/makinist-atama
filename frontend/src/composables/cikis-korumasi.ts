import { onBeforeUnmount, onMounted } from 'vue'
import type { Router } from 'vue-router'

interface Koruma {
  kirliMi: () => boolean
  kaydet?: () => Promise<void>
}

// Modül düzeyi kayıt: yalnız ekranda olan, kaydedilmemiş veri tutabilen bileşenler.
const korumalar = new Set<Koruma>()

export const CIKIS_SORUSU =
  'Kaydedilmemiş değişiklikleriniz var. Sayfadan ayrılırsanız kaybolacak. Ayrılmak istiyor musunuz?'

/**
 * Kaydedilmemiş form/tablo verisi için çıkış koruması (review FE-W7): sayfa kapanışı/yenilemede tarayıcı uyarısı (`beforeunload`)
 * ve uygulama içi gezinmede (menü, sekme geçişi, geri düğmesi) onay. `kaydet` verilirse (otomatik kaydeden
 * düzenleme tablosu) gezinmeden önce bekleyen veri kaydedilir; yalnız hâlâ kaydedilemeyen veri varsa kullanıcıya sorulur.
 */
export function useCikisKorumasi(kirliMi: () => boolean, kaydet?: () => Promise<void>): void {
  const koruma: Koruma = { kirliMi, kaydet }
  const uyari = (e: BeforeUnloadEvent) => {
    if (kirliMi()) {
      e.preventDefault()
      e.returnValue = ''
    }
  }
  onMounted(() => {
    korumalar.add(koruma)
    window.addEventListener('beforeunload', uyari)
  })
  onBeforeUnmount(() => {
    korumalar.delete(koruma)
    window.removeEventListener('beforeunload', uyari)
  })
}

/** true = ayrılınabilir. Kirli birimler önce kaydetmeyi dener; hâlâ kirli veri varsa tek onay sorulur. */
export async function ayrilabilirMi(): Promise<boolean> {
  const liste = [...korumalar]
  for (const k of liste) if (k.kirliMi()) await k.kaydet?.()
  if (!liste.some((k) => k.kirliMi())) return true
  return window.confirm(CIKIS_SORUSU)
}

/** Her gezinmeyi korur (sekme geçişi de gezinmedir). Giriş sayfasına gidiş (oturum düştü, çıkış) korunmaz: kaydedecek oturum kalmadı. */
export function cikisKorumasiniBagla(router: Router): void {
  router.beforeEach((to) => (to.name === 'giris' ? true : ayrilabilirMi()))
}
