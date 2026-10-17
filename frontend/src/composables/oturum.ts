// Paylaşılan tek durum: oturum. Pinia yok, modül düzeyi ref.
import { computed, ref } from 'vue'
import { api } from '@/api/istemci'
import type { KullaniciDto } from '@/api/tipler'

export const kullanici = ref<KullaniciDto | null>(null)
/** `/api/auth/ben` en az bir kez denendi mi (router koruması ilk gezinmede bekler). */
export const yuklendi = ref(false)
export const yoneticiMi = computed(() => kullanici.value?.rol === 'YONETICI')
export const makinistMi = computed(() => kullanici.value?.rol === 'MAKINIST')

export async function oturumYukle(): Promise<void> {
  try {
    kullanici.value = await api.get<KullaniciDto>('/api/auth/ben')
  } catch {
    kullanici.value = null // 401 OTURUM_YOK ya da sunucuya ulaşılamadı: giriş ekranı gösterilir
  } finally {
    yuklendi.value = true
  }
}

export async function girisYap(eposta: string, parola: string): Promise<void> {
  kullanici.value = await api.post<KullaniciDto>('/api/auth/giris', { eposta, parola })
  yuklendi.value = true
}

export async function cikisYap(): Promise<void> {
  try {
    await api.post('/api/auth/cikis')
  } finally {
    kullanici.value = null
  }
}

export function oturumuTemizle(): void {
  kullanici.value = null
}
