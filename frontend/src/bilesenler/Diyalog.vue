<script setup lang="ts">
/**
 * Modal diyalog (MK-140): yerel `<dialog>` + showModal (odak tuzağı ve Esc tarayıcıdan). Ebeveyn `v-if` ile açar/kapatır; `kapat` olayı
 * yalnız istektir (Esc, ×). Arka plan tıklaması KAPATMAZ (uzun formda veri kaybı olmasın). Kapanınca odak açan öğeye döner; öğe kalktıysa sayfa başlığına (`odakYedegi`).
 * İçerideki odaklı düğme devre dışı kalırsa odak diyalog içinde bir etkin düğmeye geçer.
 */
import { nextTick, onBeforeUnmount, onMounted, useId, useTemplateRef } from 'vue'
import { odakYedegi } from '@/lib/odak'
import Dugme from './Dugme.vue'

const props = withDefaults(
  defineProps<{
    baslik: string
    boyut?: 'dar' | 'orta' | 'genis' | 'tam'
    kapatilabilir?: boolean
    /** true: gövde + alt eylem çubuğu tek `<form>` içinde olur; Enter / "submit" düğmesi `gonder` olayı verir. */
    form?: boolean
  }>(),
  { boyut: 'orta', kapatilabilir: true, form: false },
)
const emit = defineEmits<{ kapat: []; gonder: [] }>()

const kimlik = useId()
const diyalog = useTemplateRef<HTMLDialogElement>('diyalog')
let onceki: Element | null = null

let gozcu: MutationObserver | null = null

/** Odaklı düğme `disabled` olunca tarayıcı odağı BODY'ye bırakır (QA YENİ-2): etkin ilk düğmeye (eylemler, yoksa başlık) taşı. */
function odakKaybiniOnar() {
  const d = diyalog.value
  const a = document.activeElement
  if (!d || (a && a !== document.body && !(d.contains(a) && (a as HTMLButtonElement).disabled)))
    return
  d.querySelector<HTMLElement>(
    '.diyalog-eylemler :is(button, a, input, select):not(:disabled)',
  )?.focus()
  if (document.activeElement === document.body)
    d.querySelector<HTMLElement>('button:not(:disabled)')?.focus()
}

onMounted(() => {
  onceki = document.activeElement
  const d = diyalog.value
  if (d && !d.hasAttribute('open')) d.showModal()
  if (d) {
    gozcu = new MutationObserver(odakKaybiniOnar)
    gozcu.observe(d, { attributes: true, attributeFilter: ['disabled'], subtree: true })
  }
})
onBeforeUnmount(() => {
  gozcu?.disconnect()
  // Sökülürken <dialog> hâlâ açık modaldır: dışındaki her öğe inert, focus() sessizce etkisiz kalır (QA BUG-1).
  // Önce yerel close() ile modal durumu kaldırılır, SONRA odak açan öğeye verilir.
  const d = diyalog.value
  if (d?.hasAttribute('open')) d.close()
  if (onceki instanceof HTMLElement && onceki.isConnected) onceki.focus()
  // Açan öğe aynı güncellemede ya da önceden DOM'dan kalkmış olabilir (ilk kural kaydı, listeden düşen satır): yedek hedef.
  void nextTick(odakYedegi)
})

function iptal(e: Event) {
  e.preventDefault() // kapatmaya karar ebeveynindir (v-if)
  if (props.kapatilabilir) emit('kapat')
}
</script>

<template>
  <dialog
    ref="diyalog"
    :class="['diyalog', `diyalog-${boyut}`]"
    :aria-labelledby="`${kimlik}-baslik`"
    :aria-describedby="$slots.neden ? `${kimlik}-neden` : undefined"
    @cancel="iptal"
  >
    <header class="diyalog-ust">
      <h2 :id="`${kimlik}-baslik`">{{ baslik }}</h2>
      <Dugme
        v-if="kapatilabilir"
        varyant="hayalet"
        boyut="sm"
        simge="kapat"
        etiket="Kapat"
        @click="emit('kapat')"
      />
    </header>
    <component :is="form ? 'form' : 'div'" class="diyalog-kap" @submit.prevent="emit('gonder')">
      <div class="diyalog-govde"><slot /></div>
      <footer v-if="$slots.eylemler || $slots.neden" class="diyalog-alt">
        <p v-if="$slots.neden" :id="`${kimlik}-neden`" class="diyalog-neden">
          <slot name="neden" />
        </p>
        <div v-if="$slots.eylemler" class="diyalog-eylemler"><slot name="eylemler" /></div>
      </footer>
    </component>
  </dialog>
</template>

<style scoped>
.diyalog {
  padding: 0;
  min-width: 0;
  max-height: calc(100dvh - 2rem);
  width: min(var(--diyalog-genislik, 32rem), 94vw);
  flex-direction: column;
  overflow: hidden;
}
.diyalog[open] {
  display: flex;
}
.diyalog-dar {
  --diyalog-genislik: 24rem;
}
.diyalog-orta {
  --diyalog-genislik: 32rem;
}
.diyalog-genis {
  --diyalog-genislik: 52rem;
}
.diyalog-tam {
  --diyalog-genislik: 80rem;
}
.diyalog-ust {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aralik-4);
  padding: var(--aralik-4) var(--aralik-5);
  border-bottom: 1px solid var(--renk-cizgi);
}
.diyalog-ust h2 {
  margin: 0;
  font-size: var(--yazi-xl);
}
.diyalog-kap {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}
.diyalog-govde {
  flex: 1 1 auto;
  padding: var(--aralik-5);
  overflow: auto;
}
.diyalog-alt {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--aralik-3) var(--aralik-5);
  padding: var(--aralik-4) var(--aralik-5);
  border-top: 1px solid var(--renk-cizgi);
  background: var(--renk-zemin);
}
.diyalog-neden {
  margin: 0;
  flex: 1 1 14rem;
  color: var(--renk-metin-ikincil);
}
.diyalog-eylemler {
  display: flex;
  gap: var(--aralik-3);
  margin-left: auto;
}
</style>
