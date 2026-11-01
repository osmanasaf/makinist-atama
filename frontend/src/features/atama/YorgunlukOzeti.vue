<script setup lang="ts">
import { computed } from 'vue'
import Simge from '@/bilesenler/Simge.vue'
import type { YakinlikEsikleri, YorgunlukOzeti } from './atamaApi'
import { yorgunlukAlanlari } from './yakinlik'

/**
 * Aday satırında yorgunluğun TEK satırlık kısa özeti (OK-47a): durum rozetinin yanında satır içi, ayrı satır eklemez.
 * Ayrıntı (eşikler) yalnız odaklı adayda `YakinlikNotu`nda açılır. Eşiği aşan değer yalnız vurgu alır (kalın + simge + gizli metin; renk yok).
 */
const props = defineProps<{ yorgunluk: YorgunlukOzeti; esikler: YakinlikEsikleri }>()
const alanlar = computed(() => yorgunlukAlanlari(props.yorgunluk, props.esikler))
const ipucu = computed(() =>
  alanlar.value.map((a) => `${a.ad}: ${a.deger} (vurgu eşiği: ${a.esik})`).join('\n'),
)
</script>

<template>
  <span class="yorgunluk-ozet" data-test="yorgunluk" :title="ipucu"
    ><span class="gorunmez">Yorgunluk (bilgi), </span>{{ `${yorgunluk.pencereGun} g: `
    }}<template v-for="(a, i) in alanlar" :key="a.ad">
      <template v-if="i > 0"
        ><span class="gorunmez">,</span><span aria-hidden="true"> · </span></template
      >
      <span :class="{ vurgulu: a.vurgulu }" :data-vurgulu="a.vurgulu ? '1' : undefined"
        ><Simge v-if="a.vurgulu" ad="bilgi" boyut="1em" />{{
          a.ad === 'En kısa dinlenme' ? '' : a.kisa
        }}<template v-if="a.ad === 'En kısa dinlenme'"
          >en kısa<span class="gorunmez"> dinlenme</span> {{ a.deger }}</template
        ><span v-if="a.vurgulu" class="gorunmez"> (eşik: {{ a.esik }})</span></span
      >
    </template></span
  >
</template>

<style scoped>
.yorgunluk-ozet {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  margin-left: var(--aralik-2);
  font-size: var(--yazi-xs);
  color: var(--renk-metin-ikincil);
}
/* Vurgu: kalın + simge (renk tek başına anlam taşımaz). */
.vurgulu {
  font-weight: var(--yazi-agirlik-kalin);
  color: var(--renk-metin);
}
</style>
