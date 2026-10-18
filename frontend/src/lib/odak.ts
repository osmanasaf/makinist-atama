import { nextTick } from 'vue'

/** Doğrulama hatasından sonra odağı ilk `aria-invalid="true"` alana taşır (klavye ve ekran okuyucu kullanıcısı hatayı bulur). */
export async function ilkHataliyaOdaklan(kok: ParentNode | null | undefined): Promise<void> {
  await nextTick()
  kok?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
}

/** JS ile kaydırırken `prefers-reduced-motion: reduce` kullanıcısına yumuşak kaydırma uygulanmaz (WCAG 2.3.3; CSS kuralı yalnız CSS animasyonlarını durdurur). */
export function kaydirmaDavranisi(): ScrollBehavior {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
}

/**
 * Odak kaybolduysa (açan/odaklı öğe DOM'dan kalktı: `activeElement` = BODY) sayfanın ana başlığına taşır; klavye kullanıcısı
 * ~17 menü durağıyla başa dönmez (WCAG 2.4.3). Odak zaten bir yerdeyse dokunmaz. Başlık yoksa `main#icerik`.
 */
export function odakYedegi(): void {
  const a = document.activeElement
  if (a && a !== document.body && a.isConnected) return
  const hedef =
    document.querySelector<HTMLElement>('#icerik h1') ?? document.getElementById('icerik')
  if (!hedef) return
  if (!hedef.hasAttribute('tabindex')) hedef.tabIndex = -1
  hedef.focus()
}
