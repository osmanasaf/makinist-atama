// Görev listesini kendiliğinden tazeler (V14: WebSocket/SSE yok). Sekme görünürken her 60 sn yoklar; sekme gizliyken durur; yeniden görünür olunca hemen bir kez yeniler.
import { onBeforeUnmount, onMounted } from 'vue'

export const YOKLAMA_MS = 60_000

export function useYoklama(yenile: () => Promise<unknown> | void, aralikMs = YOKLAMA_MS) {
  let sayac: ReturnType<typeof setInterval> | null = null
  let suruyor = false

  async function tazele() {
    if (suruyor) return // önceki yoklama bitmeden ikincisi başlamaz
    suruyor = true
    try {
      await yenile()
    } finally {
      suruyor = false
    }
  }

  function baslat() {
    if (sayac === null) sayac = setInterval(() => void tazele(), aralikMs)
  }

  function durdur() {
    if (sayac !== null) {
      clearInterval(sayac)
      sayac = null
    }
  }

  function gorunurlukDegisti() {
    if (document.visibilityState === 'hidden') {
      durdur()
    } else {
      void tazele()
      baslat()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', gorunurlukDegisti)
    if (document.visibilityState !== 'hidden') baslat()
  })
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', gorunurlukDegisti)
    durdur()
  })
}
