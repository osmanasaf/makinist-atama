<script setup lang="ts">
import { computed } from 'vue'
import Rozet from '@/bilesenler/Rozet.vue'
import type { YakinlikEsikleri, YakinlikSatiri } from './atamaApi'
import { yorgunlukAlanlari } from './yakinlik'

/**
 * Aday satırındaki "Sınıra yakın" işareti ve (yalnız odaklı adayda) ayrıntılar (F3, MK-176; OK-47a, OK-48, OK-49). Yalnız BİLGİ: ton `bilgi` + simge + metin;
 * kırmızı/uyarı sınıfı YOK, atamayı etkilemez. İki bölüm, tek veri:
 * - `rozet`: adayın sicil · gar satırında kısa rozet ("Sınıra yakın" / "Sınıra yakın (n)"); o satırda yer boştur, satıra yükseklik katmaz (OK-49). Erişilebilir ad tüm
 *   ölçüm metinlerini taşır (gizli metin).
 * - `ayrinti`: durum hücresinde, yalnız odaklı adayda görünür ölçüm metinleri ve yorgunluk eşikleri.
 * Yorgunluğun kısa özeti satır içinde `YorgunlukOzeti`dedir.
 */
const props = defineProps<{
  bolum: 'rozet' | 'ayrinti'
  satir: YakinlikSatiri
  esikler: YakinlikEsikleri
  acik?: boolean
}>()
const alanlar = computed(() => yorgunlukAlanlari(props.satir.yorgunluk, props.esikler))
const n = computed(() => props.satir.yakinlik.length)
const rozetMetni = computed(() => (n.value > 1 ? `Sınıra yakın (${n.value})` : 'Sınıra yakın'))
const metinler = computed(() => props.satir.yakinlik.map((i) => i.metin).join('; '))
</script>

<template>
  <span
    v-if="bolum === 'rozet' && n > 0"
    class="yakinlik-notu yakinlik-rozeti"
    data-test="yakinlik-notu"
  >
    <span data-test="yakinlik-isareti" :title="metinler"
      ><Rozet ton="bilgi" simge="bilgi" :metin="rozetMetni" boyut="sm" /><span class="gorunmez"
        >: {{ metinler }}</span
      ></span
    >
  </span>
  <span v-else-if="bolum === 'ayrinti' && acik" class="yakinlik-notu">
    <!-- Görünür ölçüm metni yalnız odaklı adayda; gizli metin rozette zaten okunduğu için çift okunmaz. -->
    <span v-if="n > 0" class="yakinlik-ayrinti" data-test="yakinlik-ayrinti" aria-hidden="true">
      <span v-for="i in satir.yakinlik" :key="i.kod + i.metin">{{ i.metin }}</span>
    </span>
    <span class="yorgunluk-detay" data-test="yorgunluk-detay" aria-hidden="true">
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
/* sicil · gar satırında rozet: satır kutusunu büyütmez (dikey hizalı, satır yüksekliği 1), sağdaki boş saat/km alanına taşabilir */
.yakinlik-rozeti {
  display: inline-block;
  margin: -0.1rem 0 -0.1rem var(--aralik-2); /* rozet (17 px) satır kutusundan (~16 px) yüksek: dikey kenar boşluğu eksi, satıra yükseklik katmaz */
  line-height: 1;
  vertical-align: middle;
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
