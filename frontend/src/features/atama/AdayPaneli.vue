<script setup lang="ts">
import { computed, ref } from 'vue'
import { sayiBicimle } from '@/lib/sayi'
import NobetNotu from './NobetNotu.vue'
import { ROL_ETIKETLERI, type Aday, type AtamaRolu } from './atamaApi'

const props = defineProps<{
  adaylar: Aday[]
  yukleniyor?: boolean
  hata?: string
}>()
const ihlalMetinleri = computed(() => props.adaylar.flatMap((a) => a.ihlaller.map((i) => i.metin)))
const rol = defineModel<AtamaRolu>('rol', { required: true })
const emit = defineEmits<{ sec: [aday: Aday]; yolculukOner: [aday: Aday] }>()

/** Konum uyarısı (K-20) olan aday için seferden önce yolcu olarak o gara gitmek önerilebilir (F2b); yolcu ve kondüktör rolünde anlamsız (yolculuk önerisi v1'de yalnız makinist). */
const yolculukOnerilebilir = (a: Aday) =>
  rol.value !== 'YOLCU' &&
  rol.value !== 'KONDUKTOR' &&
  a.buSeferdeRol === null &&
  a.ihlaller.some((i) => i.kod === 'K-20')

/** Seçilebilir = engeli yok ve bu seferde zaten görevli değil. */
const secilebilir = (a: Aday) => a.uygun && a.buSeferdeRol === null

function sec(a: Aday) {
  if (secilebilir(a)) emit('sec', a)
}

function simge(a: Aday): string {
  if (a.buSeferdeRol) return 'ℹ'
  if (!a.uygun) return '⛔'
  return a.ihlaller.length > 0 ? '⚠' : '✓'
}

function durumEtiketi(a: Aday): string {
  if (a.buSeferdeRol) return `Bu seferde: ${ROL_ETIKETLERI[a.buSeferdeRol]}`
  if (!a.uygun) return 'Uygun değil'
  return a.ihlaller.length > 0 ? 'Uyarı var' : 'Uygun'
}

// Yaylanan (roving) odak: satırlar ↑/↓ ile gezilir, Tab listeye tek durakla girer.
const odak = ref(0)
const govde = ref<HTMLElement | null>(null)

function tus(e: KeyboardEvent, i: number, a: Aday) {
  if (e.key === 'Enter') {
    e.preventDefault()
    sec(a)
  } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    const satirlar = govde.value?.querySelectorAll<HTMLElement>('tr[data-aday]')
    satirlar?.[i + (e.key === 'ArrowDown' ? 1 : -1)]?.focus()
  }
}
</script>

<template>
  <div>
    <fieldset class="rol-secici">
      <legend>Rol</legend>
      <label v-for="(etiket, r) in ROL_ETIKETLERI" :key="r">
        <input v-model="rol" type="radio" name="atama-rol" :value="r" />
        {{ etiket }}
      </label>
    </fieldset>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <div class="tablo-kap">
      <table class="tablo aday-tablo" aria-label="Aday personel" :aria-busy="yukleniyor">
        <thead>
          <tr>
            <th><span class="gorunmez">Simge</span></th>
            <th>Personel</th>
            <th class="sayisal">Bu ay saat</th>
            <th class="sayisal">Bu ay km</th>
            <th>Durum</th>
          </tr>
        </thead>
        <tbody ref="govde">
          <tr
            v-for="(a, i) in adaylar"
            :key="a.makinistId"
            data-aday
            class="aday"
            :class="{ soluk: !secilebilir(a), secilebilir: secilebilir(a) }"
            :tabindex="i === odak ? 0 : -1"
            :aria-disabled="secilebilir(a) ? undefined : 'true'"
            @focus="odak = i"
            @click="sec(a)"
            @keydown="tus($event, i, a)"
          >
            <td aria-hidden="true">{{ simge(a) }}</td>
            <td>
              {{ a.adSoyad }}
              <small class="alt-bilgi">{{ a.sicil }} · {{ a.bagliGar }}</small>
            </td>
            <td class="sayisal">{{ sayiBicimle(a.buAySaat) }}</td>
            <td class="sayisal">{{ sayiBicimle(a.buAyKm) }}</td>
            <td>
              <strong>{{ durumEtiketi(a) }}</strong>
              <ul v-if="a.ihlaller.length > 0" class="ihlal-listesi">
                <li v-for="(ih, k) in a.ihlaller" :key="k">
                  <span aria-hidden="true">{{ ih.siddet === 'ENGEL' ? '⛔' : '⚠' }}</span>
                  {{ ih.metin }}
                </li>
              </ul>
              <button
                v-if="yolculukOnerilebilir(a)"
                type="button"
                class="btn btn-kucuk"
                :aria-label="`${a.adSoyad} için yolculuk öner`"
                @click.stop="emit('yolculukOner', a)"
              >
                Yolculuk öner
              </button>
            </td>
          </tr>
          <tr v-if="!yukleniyor && adaylar.length === 0">
            <td colspan="5" class="bos-durum">
              Aday personel yok.{{
                rol === 'KONDUKTOR' ? ' Önce Personel sayfasından kondüktör kartı açın.' : ''
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <NobetNotu :metinler="ihlalMetinleri" />
  </div>
</template>

<style scoped>
.rol-secici {
  display: flex;
  gap: 1rem;
  border: 0;
  padding: 0;
  margin: 0 0 var(--bosluk);
}
.rol-secici legend {
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0;
  margin-bottom: 0.25rem;
}
.aday.secilebilir {
  cursor: pointer;
}
.aday.secilebilir:hover td {
  background: var(--renk-bilgi-zemin);
}
.aday.soluk {
  opacity: 0.55;
  cursor: not-allowed;
}
.aday:focus-visible {
  outline-offset: -3px;
}
/* Dar panelde yatay kaydırma çıkmasın: sıkı hücre, başlıklar sarar, Durum sütunu esner ve satır sarar. */
.aday-tablo th,
.aday-tablo td {
  padding: 0.4rem 0.5rem;
}
.aday-tablo th {
  white-space: normal;
}
.aday-tablo td:first-child {
  padding-right: 0;
}
.alt-bilgi {
  display: block;
  color: var(--renk-soluk);
}
.ihlal-listesi {
  margin: 0.25rem 0 0;
  padding-left: 0;
  list-style: none;
}
</style>
