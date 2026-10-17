// Kabuktaki aria-live="polite" bölgesine sayfaların kısa durum metni yazması için (ekran okuyucu "3 görev" gibi değişimleri duyurur).
import { ref } from 'vue'

export const rozetMetni = ref('')

export function rozetYaz(metin: string): void {
  rozetMetni.value = metin
}
