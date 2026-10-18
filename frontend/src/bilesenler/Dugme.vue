<script setup lang="ts">
// yükleniyor: metin DEĞİŞMEZ (spinner CSS ::after); düğme devre dışı + aria-busy
import { onMounted, useSlots } from 'vue'
import Simge from './Simge.vue'
import type { SimgeAdi } from './simgeler'

const props = withDefaults(
  defineProps<{
    varyant?: 'birincil' | 'ikincil' | 'hayalet' | 'tehlike'
    boyut?: 'sm' | 'md' | 'dokunma'
    yukleniyor?: boolean
    disabled?: boolean
    simge?: SimgeAdi
    /** Yalnız simgeli düğmede erişilebilir ad (zorunlu). */
    etiket?: string
    tip?: 'button' | 'submit' | 'reset'
  }>(),
  { varyant: 'ikincil', boyut: 'md', tip: 'button' },
)

const slots = useSlots()
const yalnizSimge = () => !!props.simge && !slots.default

onMounted(() => {
  if (yalnizSimge() && !props.etiket) {
    console.warn('Dugme: yalnız simgeli düğme için `etiket` zorunludur')
  }
})

const sinif = () => [
  'btn',
  props.varyant !== 'ikincil' && `btn-${props.varyant}`,
  props.boyut === 'sm' && 'btn-kucuk',
  props.boyut === 'dokunma' && 'btn-dokunma',
  yalnizSimge() && 'btn-kare',
]
</script>

<template>
  <button
    :type="tip"
    :class="sinif()"
    :disabled="disabled || yukleniyor"
    :aria-busy="yukleniyor ? 'true' : undefined"
    :aria-label="yalnizSimge() ? etiket : undefined"
    :title="yalnizSimge() ? etiket : undefined"
  >
    <Simge v-if="simge" :ad="simge" boyut="1.1em" />
    <slot />
  </button>
</template>
