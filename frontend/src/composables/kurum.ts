// Kurum adı + logosu (F1, MK-167/168): müşterinin kendi verisi; paylaşılan tek durum (Pinia yok, modül düzeyi ref). Kabuk (üst çubuk, giriş, makinist kabuğu, yazdırma başlığı)
// buradan okur. Yükleme yalnız main.ts'te bir kez yapılır: bileşenler uç çağırmaz, bu yüzden ekran testleri ve oturumsuz giriş sayfası etkilenmez.
import { computed, ref } from 'vue'
import { api } from '@/api/istemci'

/** Kurum adı girilmemişken (boş kurulum) gösterilen ürün adı: hiçbir kurumun markası değildir (OK-44a). */
export const URUN_ADI = 'Makinist Atama'

export interface KurumAcik {
  /** Girilmemişse null. */
  ad: string | null
  logoVar: boolean
  /** Logo adresine eklenir (değişince tarayıcı yeni logoyu çeker). */
  logoSurum: number
}

const bilgi = ref<KurumAcik>({ ad: null, logoVar: false, logoSurum: 0 })

/** Ekranda gösterilecek ad: kurum adı, yoksa ürün adı. */
export const kurumAdi = computed(() => bilgi.value.ad?.trim() || URUN_ADI)
export const kurumAdiGirildi = computed(() => !!bilgi.value.ad?.trim())
/** Logo adresi (varsa); sürüm sorgu parametresi yalnız önbellek anahtarıdır. */
export const logoAdresi = computed(() =>
  bilgi.value.logoVar ? `/api/kurum/logo?s=${bilgi.value.logoSurum}` : null,
)

/** Anonim uçtan okur. Hata ekranı bozmaz: bilgi olduğu gibi kalır (ürün adı), hata yutulur (AK-1.8). */
export async function kurumYukle(): Promise<void> {
  try {
    kurumGuncelle(await api.get<KurumAcik>('/api/kurum/acik'))
  } catch {
    // kurum uç hatası: ürün adıyla devam
  }
}

/** Yönetici kurum bilgisini değiştirince sunucunun yanıtı doğrudan konur (yeniden istek yok). */
export function kurumGuncelle(yeni: KurumAcik): void {
  bilgi.value = { ad: yeni.ad ?? null, logoVar: !!yeni.logoVar, logoSurum: yeni.logoSurum ?? 0 }
}

export function kurumSifirla(): void {
  bilgi.value = { ad: null, logoVar: false, logoSurum: 0 }
}
