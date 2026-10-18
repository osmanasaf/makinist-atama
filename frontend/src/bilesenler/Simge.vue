<script setup lang="ts">
// Dekoratif: anlam her zaman yanındaki metindedir (WCAG 1.1.1, 1.4.1)
import { computed } from 'vue'
import { SIMGELER, type SimgeAdi } from './simgeler'

const props = withDefaults(defineProps<{ ad: SimgeAdi; boyut?: string }>(), { boyut: '1.25em' })

const yol = computed<string | undefined>(() => {
  const d = (SIMGELER as Record<string, string>)[props.ad]
  if (d === undefined) console.warn(`Simge: bilinmeyen ad "${props.ad}"`)
  return d
})
</script>

<template>
  <svg
    class="simge"
    viewBox="0 0 20 20"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    :data-simge="ad"
    :style="{ width: boyut, height: boyut }"
  >
    <path v-if="yol" :d="yol" />
  </svg>
</template>
