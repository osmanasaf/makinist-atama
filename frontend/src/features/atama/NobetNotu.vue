<script setup lang="ts">
import { computed } from 'vue'
import { NOBET_NOTU, nobetGeciyor } from '@/lib/nobetNotu'

/** Verilen ihlal/neden metinlerinden biri "nöbet" içeriyorsa kısa açıklamayı gösterir; yoksa hiçbir şey çizmez. */
const props = defineProps<{ metinler: ReadonlyArray<string | null | undefined> }>()
const goster = computed(() => nobetGeciyor(props.metinler))
</script>

<template>
  <p v-if="goster" class="nobet-notu" data-test="nobet-notu">{{ NOBET_NOTU }}</p>
</template>

<style scoped>
.nobet-notu {
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
  color: var(--renk-soluk);
}
@media print {
  .nobet-notu {
    color: inherit;
  }
}
</style>
