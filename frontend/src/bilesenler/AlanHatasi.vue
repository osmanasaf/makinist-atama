<script setup lang="ts">
/**
 * Form alanı hata metni (QA BUG-6, WCAG 3.3.1 + 4.1.3): ortak desen. Metin varken `role="alert"` ile duyurulur; alan `aria-invalid` ve
 * `aria-describedby="<id>"` ile bu öğeye bağlanır (`bilesenler/AlanHatasi.test.ts` her kullanımda bağın kurulduğunu kaynak taramasıyla doğrular).
 */
defineProps<{ id: string; metin?: string | null }>()
</script>

<template>
  <p v-if="metin" :id="id" class="alan-hata" role="alert">{{ metin }}</p>
</template>
