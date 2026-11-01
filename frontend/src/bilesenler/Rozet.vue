<script setup lang="ts">
import { computed } from 'vue'
import Simge from './Simge.vue'
import type { SimgeAdi } from './simgeler'
import type { RolKodu, Ton } from './durum'

/** `metin` zorunlu: renk ve simge tek başına anlam taşıyamaz (MK-138). */
const props = defineProps<{
  metin: string
  ton?: Ton
  rol?: RolKodu
  simge?: SimgeAdi
  boyut?: 'sm' | 'md'
}>()

const sinif = computed(() => [
  'rozet',
  props.rol ? `rozet-rol-${props.rol.toLowerCase()}` : `rozet-${props.ton ?? 'notr'}`,
  props.boyut === 'sm' && 'rozet-sm',
])
</script>

<template>
  <span :class="sinif"
    ><Simge v-if="simge" :ad="simge" boyut="1em" /><span class="rozet-metin">{{
      metin
    }}</span></span
  >
</template>
