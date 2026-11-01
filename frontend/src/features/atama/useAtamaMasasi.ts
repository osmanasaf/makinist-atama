// Atama masasının paylaşılan durumu (Pinia yok): gün listesi, seçili sefer, rol ve adaylar. Masa ve aday paneli aynı örneği kullanır.
import { computed, ref, watch, type Ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Tarih } from '@/api/tipler'
import { gunKaydir } from '@/lib/tarih'
import { takasApi } from '@/features/takas/takasApi'
import {
  atamaApi,
  type Aday,
  type AtamaRolu,
  type GordumOzeti,
  type GunListesi,
  type KirmiziSayisi,
} from './atamaApi'

const hataMetni = (e: unknown, varsayilan: string) => (e instanceof ApiHata ? e.detail : varsayilan)

export function useAtamaMasasi(tarih: Ref<Tarih>) {
  const gun = ref<GunListesi | null>(null)
  const gunHatasi = ref('')
  const seciliId = ref<number | null>(null)
  const rol = ref<AtamaRolu>('ASIL')
  const adaylar = ref<Aday[]>([])
  const adayHatasi = ref('')
  const adayYukleniyor = ref(false)
  /** `adaylar` listesinin ait olduğu "sefer:rol" (D1/B-01): seçim değişince bayat liste ile atama yapılmasın. */
  const adayAnahtari = ref('')
  /** Seçili gün + sonraki 7 gün için kırmızı sayaç; yüklenemezse null (masa yine çalışır). */
  const kirmizi = ref<KirmiziSayisi | null>(null)
  /** Seçili gün + sonraki 7 gün için "kim gördü" sayacı; yüklenemezse null. */
  const gordum = ref<GordumOzeti | null>(null)
  /** Atamacı onayı bekleyen takas / bırakma talebi sayısı (rozet "Bekleyen talep"); yüklenemezse null. */
  const takasBekleyen = ref<number | null>(null)

  const satirlar = computed(() => gun.value?.satirlar ?? [])
  const seciliSatir = computed(
    () => satirlar.value.find((s) => s.seferId === seciliId.value) ?? null,
  )
  /** İptal edilmiş sefere makinist gerekmez: sayaca girmez. */
  const atanmamisSayisi = computed(
    () =>
      satirlar.value.filter(
        (s) => s.durum !== 'IPTAL' && !s.makinistler.some((m) => m.rol === 'ASIL'),
      ).length,
  )

  // Geç dönen eski yanıt yeni seçimi ezmesin.
  let gunSayac = 0
  let adaySayac = 0
  let kirmiziSayac = 0
  let gordumSayac = 0
  let takasSayac = 0

  async function gunuYukle() {
    const sira = ++gunSayac
    gunHatasi.value = ''
    try {
      const sonuc = await atamaApi.gun(tarih.value)
      if (sira === gunSayac) gun.value = sonuc
    } catch (e) {
      if (sira !== gunSayac) return
      gun.value = null
      gunHatasi.value = hataMetni(e, 'Gün listesi yüklenemedi.')
    }
  }

  async function adaylariYukle() {
    const sira = ++adaySayac
    adayHatasi.value = ''
    if (seciliId.value === null) {
      adaylar.value = []
      adayAnahtari.value = ''
      adayYukleniyor.value = false
      return
    }
    adayYukleniyor.value = true
    const anahtar = `${seciliId.value}:${rol.value}`
    try {
      const sonuc = await atamaApi.adaylar(seciliId.value, rol.value)
      if (sira === adaySayac) {
        adaylar.value = sonuc
        adayAnahtari.value = anahtar
      }
    } catch (e) {
      if (sira !== adaySayac) return
      adaylar.value = []
      adayAnahtari.value = ''
      adayHatasi.value = hataMetni(e, 'Adaylar yüklenemedi.')
    } finally {
      if (sira === adaySayac) adayYukleniyor.value = false
    }
  }

  async function kirmiziYukle() {
    const sira = ++kirmiziSayac
    try {
      const sonuc = await atamaApi.kirmiziSayisi(tarih.value, gunKaydir(tarih.value, 7))
      if (sira === kirmiziSayac) kirmizi.value = sonuc
    } catch {
      if (sira === kirmiziSayac) kirmizi.value = null
    }
  }

  async function gordumYukle() {
    const sira = ++gordumSayac
    try {
      const sonuc = await atamaApi.gordumOzeti(tarih.value, gunKaydir(tarih.value, 7))
      if (sira === gordumSayac) gordum.value = sonuc
    } catch {
      if (sira === gordumSayac) gordum.value = null
    }
  }

  async function takasYukle() {
    const sira = ++takasSayac
    try {
      const sonuc = await takasApi.liste('ATAMACI_ONAYI_BEKLIYOR') // yalnız rozet: süzgeçli (küçük) yanıt
      if (sira === takasSayac) takasBekleyen.value = sonuc.bekleyen
    } catch {
      if (sira === takasSayac) takasBekleyen.value = null
    }
  }

  /**
   * Yoklama (60 sn, makinistler "gördüm" dedikçe masa kendiliğinden güncellensin): gün listesi ve sayaçlar yenilenir; geçici hata mevcut ekranı SİLMEZ (yükleme hatası göstermez).
   */
  async function sessizYenile() {
    const sira = ++gunSayac
    try {
      const sonuc = await atamaApi.gun(tarih.value)
      if (sira === gunSayac) gun.value = sonuc
    } catch {
      // bir sonraki yoklamada yeniden denenir
    }
    await Promise.all([kirmiziYukle(), gordumYukle(), takasYukle()])
  }

  /** Atama/kaldırma/iptal/gecikme sonrası: gün listesi, sayaçlar ve (seçili sefer varsa) aday listesi yeniden okunur. */
  const yenile = () =>
    Promise.all([gunuYukle(), adaylariYukle(), kirmiziYukle(), gordumYukle(), takasYukle()])

  const sec = (seferId: number) => (seciliId.value = seferId)

  watch(tarih, () => {
    seciliId.value = null
    void gunuYukle()
    void kirmiziYukle()
    void gordumYukle()
  })
  watch([seciliId, rol], () => void adaylariYukle())

  return {
    gun,
    gunHatasi,
    satirlar,
    seciliId,
    seciliSatir,
    rol,
    adaylar,
    adayHatasi,
    adayYukleniyor,
    adayAnahtari,
    atanmamisSayisi,
    kirmizi,
    gordum,
    takasBekleyen,
    takasYukle,
    sessizYenile,
    gunuYukle,
    kirmiziYukle,
    gordumYukle,
    adaylariYukle,
    yenile,
    sec,
  }
}
