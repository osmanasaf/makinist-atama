<script setup lang="ts">
/** Sayfa başına tek `h1`. `.sayfa-baslik` sınıfı korunur. */
defineProps<{ baslik: string; ust?: string }>()
</script>

<template>
  <div class="sayfa-baslik">
    <div class="sayfa-baslik-metin">
      <p v-if="ust" class="sayfa-baslik-ust">{{ ust }}</p>
      <h1>{{ baslik }}</h1>
      <slot name="sayaclar" />
    </div>
    <div v-if="$slots.eylemler" class="sayfa-baslik-eylemler"><slot name="eylemler" /></div>
  </div>
</template>
