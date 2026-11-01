import { api } from '@/api/istemci'
import type { KurumAcik } from '@/composables/kurum'

/** Kurum adı + logo yazma uçları (yalnız yönetici). Okuma anonim uçtur ve `composables/kurum.ts`'tedir. */
export const kurumApi = {
  adYaz: (ad: string) => api.put<KurumAcik>('/api/kurum', { ad }),
  /** Ham dosya gövdesi (PNG/JPEG); tür ve boyutu sunucu içerikten doğrular. */
  logoYaz: (dosya: File) => api.put<KurumAcik>('/api/kurum/logo', dosya),
  logoSil: () => api.sil<KurumAcik>('/api/kurum/logo'),
}
