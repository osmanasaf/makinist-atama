<script setup lang="ts">
/** Sekme şeridi (ARIA tabs): dolaşan tabindex, ok tuşları + Home/End, otomatik etkinleştirme. İçerik `panel-<kimlik>` slotlarıdır. */
import { nextTick, useId, useTemplateRef } from 'vue'

export interface Sekme {
  kimlik: string
  ad: string
  sayac?: number
}

const props = defineProps<{ sekmeler: Sekme[]; modelValue: string; etiket: string }>()
const emit = defineEmits<{ 'update:modelValue': [kimlik: string] }>()

const on = useId()
const dugmeler = useTemplateRef<HTMLButtonElement[]>('dugmeler')

function sec(i: number) {
  const s = props.sekmeler[i]
  if (!s) return
  emit('update:modelValue', s.kimlik)
  void nextTick(() => dugmeler.value?.[i]?.focus())
}

function tus(e: KeyboardEvent, i: number) {
  const n = props.sekmeler.length
  let hedef = -1
  if (e.key === 'ArrowRight') hedef = (i + 1) % n
  else if (e.key === 'ArrowLeft') hedef = (i - 1 + n) % n
  else if (e.key === 'Home') hedef = 0
  else if (e.key === 'End') hedef = n - 1
  if (hedef < 0) return
  e.preventDefault()
  sec(hedef)
}
</script>

<template>
  <div class="sekmeler">
    <div class="sekme-serit" role="tablist" :aria-label="etiket">
      <button
        v-for="(s, i) in sekmeler"
        :id="`${on}-sekme-${s.kimlik}`"
        :key="s.kimlik"
        ref="dugmeler"
        type="button"
        role="tab"
        class="sekme"
        :aria-selected="s.kimlik === modelValue"
        :aria-controls="`${on}-panel-${s.kimlik}`"
        :tabindex="s.kimlik === modelValue ? 0 : -1"
        @click="sec(i)"
        @keydown="tus($event, i)"
      >
        {{ s.ad }}<span v-if="s.sayac !== undefined" class="sekme-sayac">{{ s.sayac }}</span>
      </button>
    </div>
    <template v-for="s in sekmeler" :key="s.kimlik">
      <div
        v-if="s.kimlik === modelValue"
        :id="`${on}-panel-${s.kimlik}`"
        class="sekme-panel"
        role="tabpanel"
        :aria-labelledby="`${on}-sekme-${s.kimlik}`"
      >
        <slot :name="`panel-${s.kimlik}`" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.sekme-serit {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-2);
  border-bottom: 1px solid var(--renk-cizgi);
}
.sekme {
  font: inherit;
  padding: var(--aralik-3) var(--aralik-5);
  min-height: var(--kontrol-yukseklik);
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: var(--renk-metin-ikincil);
  cursor: pointer;
}
.sekme:hover {
  background: var(--renk-n-100);
}
/* etkin: dolgu + alt çizgi + kalın metin (renk tek başına değil) */
.sekme[aria-selected='true'] {
  background: var(--renk-bilgi-zemin);
  border-bottom-color: var(--renk-birincil);
  color: var(--renk-bilgi-metin);
  font-weight: var(--yazi-agirlik-kalin);
}
.sekme-sayac {
  margin-left: var(--aralik-3);
  font-variant-numeric: tabular-nums;
}
.sekme-panel {
  padding-top: var(--aralik-4);
}
</style>
