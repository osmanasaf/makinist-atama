<script setup lang="ts">
import { computed } from 'vue'
import Rozet from '@/bilesenler/Rozet.vue'
import Simge from '@/bilesenler/Simge.vue'
import type { YakinlikEsikleri, YakinlikSatiri } from './atamaApi'
import { yorgunlukAlanlari } from './yakinlik'

/**
 * Aday satırındaki "Sınıra yakın" işareti ve yorgunluk özeti (F3, MK-176). Yalnız BİLGİ: ton `bilgi` + simge + metin; kırmızı/uyarı sınıfı YOK, atamayı etkilemez.
 * Eşiği aşan yorgunluk değeri yalnız vurgu alır (kalın + simge + gizli metin; renk yok).
 */
const props = defineProps<{ satir: YakinlikSatiri; esikler: YakinlikEsikleri }>()
const alanlar = computed(() => yorgunlukAlanlari(props.satir.yorgunluk, props.esikler))
const ipucu = computed(() =>
  alanlar.value.map((a) => `${a.ad}: ${a.deger} (vurgu eşiği: ${a.esik})`).join('\n'),
)
</script>

<template>
  <div class="yakinlik-notu" data-test="yakinlik-notu">
    <ul v-if="satir.yakinlik.length > 0" class="yakinlik-listesi">
      <li v-for="i in satir.yakinlik" :key="i.kod + i.metin" data-test="yakinlik-isareti">
        <Rozet ton="bilgi" simge="bilgi" metin="Sınıra yakın" boyut="sm" />
        <span>{{ i.metin }}</span>
      </li>
    </ul>
    <p class="yorgunluk" data-test="yorgunluk" :title="ipucu">
      <span class="yorgunluk-ad">Yorgunluk (bilgi), {{ satir.yorgunluk.pencereGun }} g:</span
      >{{ ' ' }}
      <template v-for="(a, i) in alanlar" :key="a.ad">
        <template v-if="i > 0"
          ><span class="gorunmez">,</span><span aria-hidden="true"> · </span></template
        >
        <span :class="{ vurgulu: a.vurgulu }" :data-vurgulu="a.vurgulu ? '1' : undefined"
          ><Simge v-if="a.vurgulu" ad="bilgi" boyut="1em" />{{ a.kisa
          }}<span v-if="a.vurgulu" class="gorunmez"> (eşik: {{ a.esik }})</span></span
        >
      </template>
    </p>
  </div>
</template>

<style scoped>
.yakinlik-notu {
  margin-top: var(--aralik-2);
  font-size: var(--yazi-xs);
}
.yakinlik-listesi {
  margin: 0 0 var(--aralik-1);
  padding: 0;
  list-style: none;
}
.yakinlik-listesi li {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2);
  align-items: baseline;
}
.yorgunluk {
  margin: 0;
  color: var(--renk-metin-ikincil);
}
.yorgunluk-ad {
  margin-right: var(--aralik-1);
}
/* Vurgu: kalın + simge (renk tek başına anlam taşımaz). */
.vurgulu {
  font-weight: var(--yazi-agirlik-kalin);
  color: var(--renk-metin);
}
</style>
