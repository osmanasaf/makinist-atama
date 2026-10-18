<script setup lang="ts">
/** Yükleniyor / boş / hata için tek görünüm. Tablo içinde `<td colspan>` kapsayıcısıyla kullanılır. */
import { computed } from 'vue'
import Simge from './Simge.vue'

const props = defineProps<{
  durum: 'yukleniyor' | 'bos' | 'hata'
  metin: string
  ayrinti?: string
}>()

const rol = computed(() =>
  props.durum === 'yukleniyor' ? 'status' : props.durum === 'hata' ? 'alert' : undefined,
)
</script>

<template>
  <div
    :class="['veri-durumu', `veri-durumu-${durum}`]"
    :role="rol"
    :aria-live="durum === 'yukleniyor' ? 'polite' : undefined"
  >
    <span v-if="durum === 'yukleniyor'" class="donen" aria-hidden="true" />
    <Simge v-else :ad="durum === 'hata' ? 'engel' : 'bilgi'" boyut="1.5rem" />
    <p class="veri-durumu-metin">{{ metin }}</p>
    <p v-if="ayrinti" class="veri-durumu-ayrinti">{{ ayrinti }}</p>
    <div v-if="$slots.eylem" class="veri-durumu-eylem"><slot name="eylem" /></div>
  </div>
</template>
