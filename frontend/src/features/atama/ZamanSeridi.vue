<script setup lang="ts">
// Zaman çizelgesi hücresi (MK-159): yalnız sunum. Geometri `zamanCizelgesi.ts`'te hesaplanır; durum metinleri masada.
// Çubuk `role="img"` + erişilebilir ad taşır (sefer, güzergâh, saat, durum); renk tek başına anlam taşımaz: dolgu / kesik çerçeve / desen / üstü çizili.
import { computed } from 'vue'
import Simge from '@/bilesenler/Simge.vue'
import type { Bant, Cubuk } from './zamanCizelgesi'

export type CubukDurumu = 'atanmis' | 'bos' | 'kirmizi' | 'iptal'

const props = defineProps<{
  cubuk: Cubuk
  durum: CubukDurumu
  /** Çubuğun erişilebilir adı. */
  etiket: string
  /** Güzergâh yazısı: çubuk dar olduğundan (2 saatlik sefer ~%10) çubuğun yanında, kırpılmadan; iki yanda da yer yoksa çubuğun içinde. */
  yazi: string
  bantlar: Bant[]
  /** "Şimdi" çizgisinin yüzdesi; seçili gün bugün değilse null. */
  simdi: number | null
  /** İki saatte bir ızgara çizgisi: ilk çizginin ve adımın yüzdesi. */
  izgaraSol: number
  izgaraAdim: number
}>()

const YER_ESIGI = 35 // % — bir yanda bu kadar yer varsa yazı çubuğun yanına konur
const yer = computed(() => {
  const { solYuzde: sol, genYuzde: gen } = props.cubuk
  if (100 - sol - gen >= YER_ESIGI) return 'sag'
  return sol >= YER_ESIGI ? 'sol' : 'ici'
})
const disStil = computed(() => {
  const { solYuzde: sol, genYuzde: gen } = props.cubuk
  return yer.value === 'sag'
    ? { left: `calc(${sol + gen}% + 0.25rem)`, maxWidth: `calc(${100 - sol - gen}% - 0.25rem)` }
    : {
        right: `calc(${100 - sol}% + 0.25rem)`,
        maxWidth: `calc(${sol}% - 0.25rem)`,
        textAlign: 'right' as const,
      }
})
</script>

<template>
  <div class="zaman-seridi">
    <div
      v-for="(b, i) in bantlar"
      :key="i"
      class="gece-bandi"
      data-test="gece-bandi"
      aria-hidden="true"
      :style="{ left: b.solYuzde + '%', width: b.genYuzde + '%' }"
    ></div>
    <div
      class="zaman-izgara"
      aria-hidden="true"
      :style="{ left: izgaraSol + '%', '--adim': izgaraAdim + '%' }"
    ></div>
    <div
      v-if="simdi !== null"
      class="simdi-cizgisi"
      data-test="simdi-cizgisi"
      aria-hidden="true"
      :style="{ left: simdi + '%' }"
    ></div>
    <div
      v-if="cubuk.planlananYuzde !== null"
      class="cubuk-plan"
      aria-hidden="true"
      :style="{ left: cubuk.planlananYuzde + '%' }"
    ></div>
    <div
      class="cubuk"
      :class="[
        `cubuk-${durum}`,
        { 'cubuk-sol-kirpik': cubuk.solKirpik, 'cubuk-sag-kirpik': cubuk.sagKirpik },
      ]"
      data-test="cubuk"
      role="img"
      :aria-label="etiket"
      :style="{ left: cubuk.solYuzde + '%', width: cubuk.genYuzde + '%' }"
    >
      <Simge v-if="cubuk.solKirpik" ad="ok-sol" boyut="0.9em" class="cubuk-ok" />
      <span v-if="yer === 'ici'" class="cubuk-yazi">{{ yazi }}</span>
      <Simge v-if="cubuk.sagKirpik" ad="ok-sag" boyut="0.9em" class="cubuk-ok" />
    </div>
    <span
      v-if="yer !== 'ici'"
      class="cubuk-yazi cubuk-yazi-dis"
      :class="`cubuk-yazi-${durum}`"
      aria-hidden="true"
      :style="disStil"
      >{{ yazi }}</span
    >
  </div>
</template>

<style>
/* Çubuk durum görünümü (renk + biçim): atanmış = dolu; boş = açık zemin + kesik çerçeve; kontrol = çapraz desen; iptal = soluk + üstü çizili. Yalnız belirteç. */
.zaman-seridi {
  position: relative;
  isolation: isolate; /* çubukların z-index'i şeritte kalır: kaydırırken sabit başlığın (eksen) üstüne çıkmaz */
  height: 1.75rem;
  min-width: 10rem;
}
.gece-bandi {
  position: absolute;
  top: -0.25rem;
  bottom: -0.25rem;
  background: var(--renk-cubuk-gece);
}
.zaman-izgara {
  position: absolute;
  top: -0.25rem;
  bottom: -0.25rem;
  right: 0;
  background-image: linear-gradient(to right, var(--renk-cubuk-izgara) 1px, transparent 1px);
  background-size: var(--adim) 100%;
  background-repeat: repeat-x;
}
.simdi-cizgisi {
  position: absolute;
  top: -0.25rem;
  bottom: -0.25rem;
  width: 2px;
  margin-left: -1px;
  background: var(--renk-cubuk-simdi);
  z-index: 1;
}
.cubuk-plan {
  position: absolute;
  top: 0.25rem;
  bottom: 0.25rem;
  width: 0;
  border-left: 2px dotted var(--renk-metin-ikincil);
  z-index: 1;
}
.cubuk {
  position: absolute;
  top: 0.125rem;
  bottom: 0.125rem;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: var(--aralik-2);
  min-width: 0;
  padding: 0 var(--aralik-2);
  overflow: hidden;
  border: 1.5px solid transparent;
  border-radius: var(--yaricap-sm);
  box-sizing: border-box;
  font-size: var(--yazi-xs);
  line-height: 1;
  white-space: nowrap;
}
.cubuk-yazi {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* Çubuğun yanındaki yazı: çubuk zemini yok, satır zemininde okunur; durum yine yazı biçimiyle de belli (kalın / üstü çizili). */
.cubuk-yazi-dis {
  position: absolute;
  top: 50%;
  z-index: 3;
  transform: translateY(-50%);
  font-size: var(--yazi-xs);
  line-height: 1;
  white-space: nowrap;
  color: var(--renk-metin);
}
.cubuk-yazi-kirmizi {
  font-weight: var(--yazi-agirlik-kalin);
}
.cubuk-yazi-iptal {
  color: var(--renk-metin-soluk);
  text-decoration: line-through;
}
.cubuk-ok {
  flex: none;
}
.cubuk-sol-kirpik {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}
.cubuk-sag-kirpik {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
  justify-content: space-between;
}
.cubuk-atanmis {
  background: var(--renk-cubuk-dolu);
  border-color: var(--renk-cubuk-dolu);
  color: var(--renk-cubuk-dolu-yazi);
}
.cubuk-bos {
  background: var(--renk-cubuk-bos-zemin);
  border-color: var(--renk-cubuk-bos-cizgi);
  border-style: dashed;
  color: var(--renk-cubuk-bos-yazi);
}
.cubuk-kirmizi {
  background: repeating-linear-gradient(
    135deg,
    var(--renk-cubuk-kirmizi) 0 6px,
    var(--renk-cubuk-kirmizi-desen) 6px 9px
  );
  border-color: var(--renk-cubuk-kirmizi-desen);
  color: var(--renk-cubuk-kirmizi-yazi);
  font-weight: var(--yazi-agirlik-kalin);
}
.cubuk-iptal {
  background: var(--renk-cubuk-iptal-zemin);
  border-color: var(--renk-cubuk-iptal-cizgi);
  border-style: dotted;
  color: var(--renk-cubuk-iptal-yazi);
  text-decoration: line-through;
}
/* Seçili satırın çubuğu halkalı (tablodaki iç halkaya ek; renk tek başına değil, kalın çift halka). */
.sefer-satiri[aria-selected='true'] .cubuk {
  box-shadow:
    0 0 0 2px var(--renk-yuzey),
    0 0 0 4px var(--renk-metin);
}
/* Açıklama örnekleri aynı sınıfları kullanır. */
.cubuk-ornek {
  position: static;
  display: inline-block;
  width: 1.5rem;
  height: 0.75rem;
  padding: 0;
  vertical-align: middle;
}
@media print {
  .zaman-seridi {
    display: none;
  }
}
</style>
