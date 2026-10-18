<script setup lang="ts">
/**
 * Doluluk eğrisi (MK-141): tek seri çizgi, hedef çizgileri doğrudan etiketli, fare/klavye ipucu.
 * Renk dışı kodlama: veri düz çizgi + nokta, hedefler kesikli çizgi + metin etiketi; aynı veri tablo görünümünde (`<details>`).
 * Renkler yalnız belirteçlerden (bu dosyanın stilinde).
 */
import { computed, ref } from 'vue'
import { sayiBicimle } from '@/lib/sayi'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import type { KadroEgriNokta, KadroHedef } from './raporApi'
import {
  eksenTikleri,
  enYakinIndeks,
  hedefCizgileri,
  noktaKonumu,
  olcekKur,
  ornekle,
  ozetMetni,
  yolDizesi,
} from './egriGeometri'

const props = withDefaults(defineProps<{ noktalar: KadroEgriNokta[]; hedefler?: KadroHedef[] }>(), {
  hedefler: () => [],
})

const yuz = (n: number) => `%${sayiBicimle(n)}`
/** Çizilen noktalar en çok 60; tablo tamamını gösterir. */
const cizilen = computed(() => ornekle(props.noktalar))
const olcek = computed(() =>
  olcekKur(
    cizilen.value,
    props.hedefler.map((h) => h.yuzde),
  ),
)
const c = computed(() => olcek.value.cerceve)
const yol = computed(() => yolDizesi(cizilen.value, olcek.value))
const konumlar = computed(() => cizilen.value.map((n) => noktaKonumu(n, olcek.value)))
const hedefCizgisi = computed(() => hedefCizgileri(props.hedefler, olcek.value))
const yTikleri = computed(() => eksenTikleri(olcek.value.yMin, olcek.value.yMax, 4))
const xTikleri = computed(() => eksenTikleri(olcek.value.xMin, olcek.value.xMax, 6, true))
const ozet = computed(() => ozetMetni(props.noktalar, props.hedefler))
/** Az noktada hepsi işaretlenir; çoğunda çizgi yeter, yalnız ipucu noktası ve son nokta işaretlenir. */
const hepsiIsaretli = computed(() => cizilen.value.length <= 24)

const aktif = ref<number | null>(null)
const svg = ref<SVGSVGElement | null>(null)
const aktifNokta = computed(() =>
  aktif.value == null ? null : (cizilen.value[aktif.value] ?? null),
)
const aktifKonum = computed(() =>
  aktif.value == null ? null : (konumlar.value[aktif.value] ?? null),
)
const ipucu = computed(() =>
  aktifNokta.value
    ? {
        sol: ((aktifKonum.value!.x / c.value.genislik) * 100).toFixed(2),
        ust: ((aktifKonum.value!.y / c.value.yukseklik) * 100).toFixed(2),
        sagda: aktifKonum.value!.x > c.value.genislik / 2,
      }
    : null,
)

function fareHareketi(e: PointerEvent) {
  const el = svg.value
  if (!el) return
  const kutu = el.getBoundingClientRect()
  if (kutu.width === 0) return
  const i = enYakinIndeks(
    cizilen.value,
    olcek.value,
    ((e.clientX - kutu.left) * c.value.genislik) / kutu.width,
  )
  aktif.value = i < 0 ? null : i
}

function odak() {
  if (aktif.value == null && cizilen.value.length) aktif.value = 0
}

function tus(e: KeyboardEvent) {
  const n = cizilen.value.length
  if (n === 0) return
  const simdiki = aktif.value ?? -1
  let yeni: number
  if (e.key === 'ArrowRight' || e.key === 'ArrowUp') yeni = Math.min(n - 1, simdiki + 1)
  else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') yeni = Math.max(0, simdiki - 1)
  else if (e.key === 'Home') yeni = 0
  else if (e.key === 'End') yeni = n - 1
  else if (e.key === 'Escape') {
    aktif.value = null
    return
  } else return
  e.preventDefault()
  aktif.value = yeni
}
</script>

<template>
  <div class="doluluk-egrisi">
    <VeriDurumu v-if="noktalar.length === 0" durum="bos" metin="Eğri verisi yok." />
    <template v-else>
      <div
        class="egri-kap"
        role="group"
        aria-label="Doluluk eğrisi noktaları: ok tuşlarıyla gezinin"
        tabindex="0"
        data-test="egri-grafik"
        @keydown="tus"
        @focus="odak"
        @blur="aktif = null"
        @pointerleave="aktif = null"
      >
        <svg
          ref="svg"
          class="egri-svg"
          :viewBox="`0 0 ${c.genislik} ${c.yukseklik}`"
          role="img"
          :aria-label="ozet"
          @pointermove="fareHareketi"
        >
          <g class="egri-izgara">
            <line
              v-for="t in yTikleri"
              :key="`yi${t}`"
              :x1="c.sol"
              :x2="c.genislik - c.sag"
              :y1="olcek.y(t)"
              :y2="olcek.y(t)"
            />
          </g>
          <g class="egri-eksen">
            <line
              :x1="c.sol"
              :x2="c.genislik - c.sag"
              :y1="c.yukseklik - c.alt"
              :y2="c.yukseklik - c.alt"
            />
            <text
              v-for="t in yTikleri"
              :key="`yt${t}`"
              :x="c.sol - 8"
              :y="olcek.y(t)"
              text-anchor="end"
              dominant-baseline="middle"
            >
              {{ yuz(t) }}
            </text>
            <text
              v-for="t in xTikleri"
              :key="`xt${t}`"
              :x="olcek.x(t)"
              :y="c.yukseklik - c.alt + 16"
              text-anchor="middle"
            >
              {{ t }}
            </text>
            <text
              class="egri-eksen-baslik"
              :x="(c.sol + c.genislik - c.sag) / 2"
              :y="c.yukseklik - 8"
              text-anchor="middle"
            >
              Ek genel makinist
            </text>
          </g>

          <g class="egri-hedefler">
            <template v-for="h in hedefCizgisi" :key="h.yuzde">
              <line
                class="egri-hedef"
                :x1="c.sol"
                :x2="c.genislik - c.sag"
                :y1="h.y"
                :y2="h.y"
                :data-hedef="h.yuzde"
              />
              <text
                class="egri-hedef-etiket"
                :x="c.genislik - c.sag + 8"
                :y="h.etiketY"
                dominant-baseline="middle"
              >
                {{ h.etiket }}
              </text>
            </template>
          </g>

          <path v-if="yol" class="egri-cizgi" :d="yol" />

          <g v-if="aktifKonum" class="egri-capraz">
            <line :x1="aktifKonum.x" :x2="aktifKonum.x" :y1="c.ust" :y2="c.yukseklik - c.alt" />
          </g>

          <g class="egri-noktalar">
            <template v-for="(k, i) in konumlar" :key="i">
              <template v-if="hepsiIsaretli || i === aktif || i === konumlar.length - 1 || i === 0">
                <circle class="egri-halka" :cx="k.x" :cy="k.y" r="6" />
                <circle class="egri-nokta" :cx="k.x" :cy="k.y" :r="i === aktif ? 5 : 4" />
              </template>
            </template>
          </g>
        </svg>
        <div
          v-if="ipucu && aktifNokta"
          class="egri-ipucu"
          :class="{ 'egri-ipucu-sol': ipucu.sagda }"
          :style="{ left: `${ipucu.sol}%`, top: `${ipucu.ust}%` }"
          role="status"
          data-test="egri-ipucu"
        >
          <strong>{{ yuz(aktifNokta.dolulukYuzde) }}</strong>
          <span>+{{ aktifNokta.ek }} makinist · {{ aktifNokta.bos }} boş sefer</span>
        </div>
      </div>

      <details class="egri-tablo" data-test="egri-tablo">
        <summary>Tablo görünümü</summary>
        <div class="tablo-kap">
          <table class="tablo" aria-label="Doluluk eğrisi">
            <thead>
              <tr>
                <th class="sayisal">Ek makinist</th>
                <th class="sayisal">Boş sefer</th>
                <th class="sayisal">Doluluk</th>
                <th class="sayisal">Eklenen kişi başına kapanan sefer</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in noktalar" :key="e.ek">
                <td class="sayisal">{{ e.ek }}</td>
                <td class="sayisal">{{ e.bos }}</td>
                <td class="sayisal">{{ yuz(e.dolulukYuzde) }}</td>
                <td class="sayisal">{{ e.marjinal == null ? '—' : sayiBicimle(e.marjinal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </div>
</template>

<style scoped>
.doluluk-egrisi {
  display: grid;
  gap: var(--aralik-4);
}
.egri-kap {
  position: relative;
  max-width: 45rem;
  border-radius: var(--yaricap);
}
.egri-svg {
  display: block;
  width: 100%;
  height: auto;
  font-size: var(--yazi-xs);
  font-variant-numeric: tabular-nums;
  touch-action: pan-y;
}
.egri-izgara line {
  stroke: var(--renk-cizgi);
  stroke-width: 1;
}
.egri-eksen line {
  stroke: var(--renk-kontrol-kenar);
  stroke-width: 1;
}
.egri-eksen text {
  fill: var(--renk-metin-soluk);
}
.egri-eksen-baslik {
  fill: var(--renk-metin-ikincil);
}
.egri-hedef {
  stroke: var(--renk-metin-soluk);
  stroke-width: 1.5;
  stroke-dasharray: 6 4;
}
.egri-hedef-etiket {
  fill: var(--renk-metin-ikincil);
  font-weight: var(--yazi-agirlik-orta);
}
.egri-cizgi {
  fill: none;
  stroke: var(--renk-birincil);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.egri-halka {
  fill: var(--renk-yuzey);
}
.egri-nokta {
  fill: var(--renk-birincil);
}
.egri-capraz line {
  stroke: var(--renk-metin-soluk);
  stroke-width: 1;
}
.egri-ipucu {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: var(--aralik-1);
  padding: var(--aralik-2) var(--aralik-3);
  transform: translate(0.75rem, -110%);
  background: var(--renk-yuzey);
  color: var(--renk-metin);
  border: 1px solid var(--renk-cizgi);
  border-radius: var(--yaricap);
  box-shadow: var(--golge-1);
  font-size: var(--yazi-sm);
  white-space: nowrap;
  pointer-events: none;
}
.egri-ipucu-sol {
  transform: translate(calc(-100% - 0.75rem), -110%);
}
.egri-ipucu strong {
  font-size: var(--yazi-md);
  font-variant-numeric: tabular-nums;
}
.egri-ipucu span {
  color: var(--renk-metin-ikincil);
  font-variant-numeric: tabular-nums;
}
.egri-tablo summary {
  cursor: pointer;
  font-weight: var(--yazi-agirlik-orta);
  margin-bottom: var(--aralik-3);
}
</style>
