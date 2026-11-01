<script setup lang="ts">
import { computed } from 'vue'
import Rozet from '@/bilesenler/Rozet.vue'
import type { YakinlikEsikleri, YakinlikSatiri } from './atamaApi'
import { yorgunlukAlanlari } from './yakinlik'

/**
 * Aday satırındaki "Sınıra yakın" işareti ve (yalnız odaklı adayda) yorgunluk ayrıntısı (F3, MK-176; OK-47a). Yalnız BİLGİ: ton `bilgi` + simge + metin;
 * kırmızı/uyarı sınıfı YOK, atamayı etkilemez. Yorgunluğun kısa özeti satır içinde `YorgunlukOzeti`dedir.
 */
const props = defineProps<{ satir: YakinlikSatiri; esikler: YakinlikEsikleri; acik?: boolean }>()
const alanlar = computed(() => yorgunlukAlanlari(props.satir.yorgunluk, props.esikler))
</script>

<template>
  <div v-if="satir.yakinlik.length > 0 || acik" class="yakinlik-notu" data-test="yakinlik-notu">
    <ul v-if="satir.yakinlik.length > 0" class="yakinlik-listesi">
      <li v-for="i in satir.yakinlik" :key="i.kod + i.metin" data-test="yakinlik-isareti">
        <Rozet ton="bilgi" simge="bilgi" metin="Sınıra yakın" boyut="sm" />
        <span>{{ i.metin }}</span>
      </li>
    </ul>
    <p v-if="acik" class="yorgunluk-detay" data-test="yorgunluk-detay">
      Yorgunluk (bilgi), son {{ satir.yorgunluk.pencereGun }} g:
      <template v-for="(a, i) in alanlar" :key="a.ad"
        ><template v-if="i > 0"> · </template>{{ a.ad }} {{ a.deger }} (eşik:
        {{ a.esik }})</template
      >
    </p>
  </div>
</template>

<style scoped>
.yakinlik-notu {
  margin-top: var(--aralik-2);
  font-size: var(--yazi-xs);
}
.yakinlik-listesi {
  margin: 0;
  padding: 0;
  list-style: none;
}
.yakinlik-listesi li {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2);
  align-items: baseline;
}
.yorgunluk-detay {
  margin: var(--aralik-1) 0 0;
  color: var(--renk-metin-ikincil);
}
</style>
