<script lang="ts">
import { ref } from 'vue'

// Tek açık menü: modül düzeyinde; yeni açılan öncekini kapatır.
const acikKimlik = ref<number | null>(null)
let sayac = 0

export interface EylemOgesi {
  kimlik: string
  ad: string
  /** Ekran okuyucu adı (yoksa `ad`). Görünen ad bunun içinde geçmelidir. */
  etiket?: string
  simge?: SimgeAdi
  /** Yıkıcı eylem: ayırıcıdan sonra, tehlike tonunda. */
  tehlike?: boolean
  gizli?: boolean
}
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, watch } from 'vue'
import Simge from './Simge.vue'
import type { SimgeAdi } from './simgeler'

/**
 * Eylem menüsü (⋯, MK-149): ARIA menu-button kalıbı. Kapalıyken öğeler DOM'da yoktur; açıkken `position: fixed`
 * (kaydırma kabı kırpmaz) ve alt kenara yakınsa yukarı açılır. Esc/Tab/dışa tık/kaydırma/yeniden boyut kapatır.
 */
const props = defineProps<{
  /** ⋯ düğmesinin erişilebilir adı. */
  etiket: string
  ogeler: EylemOgesi[]
  devreDisi?: boolean
  /** Yaylanan odak (roving tabindex) kullanan satırlar için. */
  sekmeSirasi?: number
}>()
const emit = defineEmits<{ sec: [kimlik: string] }>()

const kimlik = ++sayac
const menuKimligi = `eylem-menu-${kimlik}`
const acik = computed(() => acikKimlik.value === kimlik)
const gorunur = computed(() => props.ogeler.filter((o) => !o.gizli))
const ayiriciOnce = (i: number) =>
  i > 0 && !!gorunur.value[i]!.tehlike && !gorunur.value[i - 1]!.tehlike

const dugmeEl = ref<HTMLButtonElement | null>(null)
const menuEl = ref<HTMLElement | null>(null)
const yon = ref<'asagi' | 'yukari'>('asagi')
const stil = ref<Record<string, string>>({})

const BOSLUK = 4
async function ac(odak: 'ilk' | 'son') {
  if (props.devreDisi || !dugmeEl.value) return
  const r = dugmeEl.value.getBoundingClientRect()
  yon.value = 'asagi'
  stil.value = { top: `${r.bottom + BOSLUK}px`, right: `${window.innerWidth - r.right}px` }
  acikKimlik.value = kimlik
  await nextTick()
  const m = menuEl.value
  if (!m) return
  const yukseklik = m.getBoundingClientRect().height
  if (r.bottom + BOSLUK + yukseklik > window.innerHeight && r.top - BOSLUK - yukseklik >= 0) {
    yon.value = 'yukari'
    stil.value = {
      bottom: `${window.innerHeight - r.top + BOSLUK}px`,
      right: `${window.innerWidth - r.right}px`,
    }
  }
  const dugmeler = m.querySelectorAll<HTMLElement>('[role="menuitem"]')
  ;(odak === 'ilk' ? dugmeler[0] : dugmeler[dugmeler.length - 1])?.focus({ preventScroll: true })
}

function kapat(odakVer: boolean) {
  if (acikKimlik.value === kimlik) acikKimlik.value = null
  if (odakVer) dugmeEl.value?.focus({ preventScroll: true })
}

function degistir() {
  if (acik.value) kapat(false)
  else void ac('ilk')
}

function dugmeTus(e: KeyboardEvent) {
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    void ac(e.key === 'ArrowDown' ? 'ilk' : 'son')
  }
}

function menuTus(e: KeyboardEvent) {
  const liste = [...(menuEl.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
  const simdi = liste.indexOf(document.activeElement as HTMLElement)
  const git = (i: number) => {
    e.preventDefault()
    liste[(i + liste.length) % liste.length]?.focus({ preventScroll: true })
  }
  if (e.key === 'ArrowDown') git(simdi + 1)
  else if (e.key === 'ArrowUp') git(simdi - 1)
  else if (e.key === 'Home') git(0)
  else if (e.key === 'End') git(liste.length - 1)
  else if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    kapat(true)
  } else if (e.key === 'Tab') kapat(true) // odak ⋯'ya döner, doğal Tab oradan ilerler
}

function sec(o: EylemOgesi) {
  kapat(true) // diyalog açılırsa odak dönüş noktası ⋯ düğmesi olur (menü öğesi DOM'dan kalkar)
  emit('sec', o.kimlik)
}

const disaTik = (e: Event) => {
  if (!(e.target instanceof Node) || !(dugmeEl.value?.parentElement?.contains(e.target) ?? false))
    kapat(false)
}
const kapatSessiz = () => kapat(false)
function dinle(ekle: boolean) {
  const f = ekle ? 'addEventListener' : 'removeEventListener'
  document[f]('pointerdown', disaTik, true)
  document[f]('scroll', kapatSessiz, true)
  window[f]('resize', kapatSessiz)
}
watch(acik, (a) => dinle(a))
onBeforeUnmount(() => {
  dinle(false)
  kapat(false)
})
</script>

<template>
  <span class="eylem-menusu">
    <button
      ref="dugmeEl"
      type="button"
      class="btn btn-kucuk btn-kare"
      :tabindex="sekmeSirasi"
      :disabled="devreDisi"
      aria-haspopup="menu"
      :aria-expanded="acik"
      :aria-controls="acik ? menuKimligi : undefined"
      :aria-label="etiket"
      :title="etiket"
      @click.stop="degistir"
      @keydown="dugmeTus"
    >
      <Simge ad="daha" boyut="1.1em" />
    </button>
    <div
      v-if="acik"
      :id="menuKimligi"
      ref="menuEl"
      class="eylem-menu"
      role="menu"
      :aria-label="etiket"
      :data-yon="yon"
      :style="stil"
      @keydown="menuTus"
      @click.stop
    >
      <template v-for="(o, i) in gorunur" :key="o.kimlik">
        <div v-if="ayiriciOnce(i)" class="eylem-ayirici" role="separator" />
        <button
          type="button"
          role="menuitem"
          tabindex="-1"
          class="eylem-oge"
          :class="{ tehlike: o.tehlike }"
          :aria-label="o.etiket"
          @click="sec(o)"
        >
          <Simge v-if="o.simge" :ad="o.simge" boyut="1.1em" />{{ o.ad }}
        </button>
      </template>
    </div>
  </span>
</template>

<style scoped>
.eylem-menusu {
  display: inline-flex;
}
.eylem-menu {
  position: fixed;
  z-index: 30;
  display: flex;
  flex-direction: column;
  min-width: 11rem;
  padding: var(--aralik-1);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  background: var(--renk-yuzey);
  box-shadow: var(--golge-2);
}
.eylem-oge {
  display: flex;
  align-items: center;
  gap: var(--aralik-3);
  min-height: 2rem;
  padding: var(--aralik-1) var(--aralik-3);
  border: 0;
  border-radius: var(--yaricap);
  background: transparent;
  color: var(--renk-metin);
  font: inherit;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}
.eylem-oge:hover,
.eylem-oge:focus-visible {
  background: var(--renk-n-100);
}
.eylem-oge.tehlike {
  color: var(--renk-engel-metin);
}
.eylem-ayirici {
  height: 1px;
  margin: var(--aralik-1) 0;
  background: var(--renk-cizgi);
}
@media print {
  .eylem-menusu {
    display: none;
  }
}
</style>
