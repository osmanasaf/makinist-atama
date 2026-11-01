<script setup lang="ts">
import { computed } from 'vue'
import Rozet from '@/bilesenler/Rozet.vue'
import type { YakinlikEsikleri, YakinlikSatiri } from './atamaApi'
import { yorgunlukAlanlari } from './yakinlik'

/**
 * Aday satırındaki "Sınıra yakın" işareti ve (yalnız odaklı adayda) ayrıntılar (F3, MK-176; OK-47a, OK-48). Yalnız BİLGİ: ton `bilgi` + simge + metin;
 * kırmızı/uyarı sınıfı YOK, atamayı etkilemez. Satırda yalnız kısa rozet ("Sınıra yakın" / "Sınıra yakın (n)") durur, ayrı satır eklemez;
 * erişilebilir ad tüm ölçüm metinlerini taşır (gizli metin), görünür ölçüm metinleri ve yorgunluk eşikleri yalnız odaklı adayda açılır.
 * Yorgunluğun kısa özeti satır içinde `YorgunlukOzeti`dedir.
 */
const props = defineProps<{ satir: YakinlikSatiri; esikler: YakinlikEsikleri; acik?: boolean }>()
const alanlar = computed(() => yorgunlukAlanlari(props.satir.yorgunluk, props.esikler))
const n = computed(() => props.satir.yakinlik.length)
const rozetMetni = computed(() => (n.value > 1 ? `Sınıra yakın (${n.value})` : 'Sınıra yakın'))
const metinler = computed(() => props.satir.yakinlik.map((i) => i.metin).join('; '))
</script>

<template>
  <span v-if="n > 0 || acik" class="yakinlik-notu" data-test="yakinlik-notu">
    <span v-if="n > 0" class="yakinlik-rozeti" data-test="yakinlik-isareti" :title="metinler"
      ><Rozet ton="bilgi" simge="bilgi" :metin="rozetMetni" boyut="sm" /><span class="gorunmez"
        >: {{ metinler }}</span
      ></span
    >
    <!-- Görünür ölçüm metni yalnız odaklı adayda; gizli metin zaten okunduğu için çift okunmaz. -->
    <span
      v-if="acik && n > 0"
      class="yakinlik-ayrinti"
      data-test="yakinlik-ayrinti"
      aria-hidden="true"
    >
      <span v-for="i in satir.yakinlik" :key="i.kod + i.metin">{{ i.metin }}</span>
    </span>
    <span v-if="acik" class="yorgunluk-detay" data-test="yorgunluk-detay">
      Yorgunluk (bilgi), son {{ satir.yorgunluk.pencereGun }} g:
      <template v-for="(a, i) in alanlar" :key="a.ad"
        ><template v-if="i > 0"> · </template>{{ a.ad }} {{ a.deger }} (eşik:
        {{ a.esik }})</template
      >
    </span>
  </span>
</template>

<style scoped>
.yakinlik-notu {
  font-size: var(--yazi-xs);
}
.yakinlik-rozeti {
  display: inline-block;
  margin-left: var(--aralik-2);
  vertical-align: bottom;
}
.yakinlik-ayrinti,
.yorgunluk-detay {
  display: block;
  margin-top: var(--aralik-1);
  color: var(--renk-metin-ikincil);
}
.yakinlik-ayrinti span {
  display: block;
}
</style>
