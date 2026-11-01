// Geri al (MK-152, OK-37): hızlı atamadan sonra 10 sn görünen tek adımlık geri alma. Command/undo yığını YOK (tek atama).
import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { atamaApi, kaldirMesaji, type AtamaRolu } from './atamaApi'

export const GERI_AL_SURESI_MS = 10_000
const DEGISTI_MESAJI = 'Atama bu arada değişti; geri alınmadı.'

export interface BekleyenAtama {
  atamaId: number
  seferId: number
  rol: AtamaRolu
  /** Şeritte görünen metin ("Atandı: …"). */
  mesaj: string
}
export interface GeriAlSonucu {
  tur: 'tamam' | 'degisti' | 'hata'
  mesaj: string
}

export function useGeriAl() {
  const bekleyen = ref<BekleyenAtama | null>(null)
  let zamanlayici: ReturnType<typeof setTimeout> | undefined
  let suruyor = false

  const durdur = () => clearTimeout(zamanlayici)
  const surdur = () => {
    durdur()
    if (bekleyen.value) zamanlayici = setTimeout(temizle, GERI_AL_SURESI_MS)
  }
  function temizle() {
    durdur()
    bekleyen.value = null
  }
  function baslat(b: BekleyenAtama) {
    bekleyen.value = b
    surdur()
  }

  /**
   * Atamayı `atamaId` ile kaldırır: araya başka atama girdiyse sunucu 409 verir ve hiçbir şey silinmez (yarış sunucuda kapalı).
   * İşlem sürerken ya da bekleyen yokken `null`.
   */
  async function geriAl(): Promise<GeriAlSonucu | null> {
    const b = bekleyen.value
    if (!b || suruyor) return null
    suruyor = true
    durdur()
    try {
      const sonuc = await atamaApi.kaldir(b.seferId, {
        rol: b.rol,
        neden: 'DIGER',
        nedenNotu: 'Atama geri alındı (hızlı atama sonrası)',
        atamaId: b.atamaId,
      })
      return { tur: 'tamam', mesaj: kaldirMesaji(sonuc) }
    } catch (e) {
      if (e instanceof ApiHata && e.kod === 'ATAMA_DEGISTI')
        return { tur: 'degisti', mesaj: DEGISTI_MESAJI }
      return { tur: 'hata', mesaj: e instanceof ApiHata ? e.detail : 'Atama geri alınamadı.' }
    } finally {
      suruyor = false
      if (bekleyen.value === b) temizle() // araya giren YENİ atamanın şeridi silinmez (D1/B-04)
    }
  }

  if (getCurrentScope()) onScopeDispose(durdur)
  return { bekleyen, baslat, temizle, duraklat: durdur, surdur, geriAl }
}
