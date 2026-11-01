<script setup lang="ts">
import { computed, ref } from 'vue'
import { sayiBicimle } from '@/lib/sayi'
import Rozet from '@/bilesenler/Rozet.vue'
import Simge from '@/bilesenler/Simge.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import type { SimgeAdi } from '@/bilesenler/simgeler'
import type { Ton } from '@/bilesenler/durum'
import NobetNotu from './NobetNotu.vue'
import YakinlikNotu from './YakinlikNotu.vue'
import YorgunlukOzeti from './YorgunlukOzeti.vue'
import { ROL_ETIKETLERI, type Aday, type AtamaRolu, type YakinlikYaniti } from './atamaApi'
import { onerilenAday, ortalamayaFark } from './hizliDoldur'
import { adayHaritasi } from './yakinlik'

const props = defineProps<{
  adaylar: Aday[]
  yukleniyor?: boolean
  hata?: string
  /** F3: sınıra yakınlık + yorgunluk (ayrı uç; yoksa/kapalıysa hiçbir işaret çizilmez). Yalnız bilgi: sıralama, "Önerilen" ve seçim bundan etkilenmez. */
  yakinlik?: YakinlikYaniti | null
}>()
const yakinlikHaritasi = computed(() => adayHaritasi(props.yakinlik))
/** Engelli (uygun olmayan) ve bu seferde zaten görevli adayda işaret gösterilmez (AK-3.5). */
const yakinlikSatiri = (a: Aday) =>
  a.uygun && a.buSeferdeRol === null ? yakinlikHaritasi.value.get(a.makinistId) : undefined
/** Sunucu sırasındaki ilk temiz aday (MK-151): satırda "Önerilen" rozeti; masa hızlı atamada bu satıra odak verir. */
const onerilen = computed(() => onerilenAday(props.adaylar))
/** OK-35: aday havuzu ortalamasına göre fark (yalnız bilgi; yolcuda yok). */
const farklar = computed(() => ortalamayaFark(props.adaylar, rol.value))
const farkVar = computed(() => farklar.value.size > 0)
const farkMetni = (a: Aday) => {
  const f = farklar.value.get(a.makinistId)
  return f == null ? '—' : f > 0 ? `+%${f}` : f < 0 ? `-%${-f}` : '%0'
}
const ihlalMetinleri = computed(() => props.adaylar.flatMap((a) => a.ihlaller.map((i) => i.metin)))
const rol = defineModel<AtamaRolu>('rol', { required: true })
const emit = defineEmits<{ sec: [aday: Aday]; yolculukOner: [aday: Aday] }>()

/** Konum uyarısı (K-20) olan aday için seferden önce yolcu olarak o gara gitmek önerilebilir (F2b); yolcu ve kondüktör rolünde anlamsız (yolculuk önerisi v1'de yalnız makinist). */
const yolculukOnerilebilir = (a: Aday) =>
  rol.value !== 'YOLCU' &&
  rol.value !== 'KONDUKTOR' &&
  a.buSeferdeRol === null &&
  a.ihlaller.some((i) => i.kod === 'K-20')

/** Seçilebilir = liste yüklenmiyor (D1/B-01: eski seferin listesi), engeli yok ve bu seferde zaten görevli değil. */
const secilebilir = (a: Aday) => !props.yukleniyor && a.uygun && a.buSeferdeRol === null

function sec(a: Aday) {
  if (secilebilir(a)) emit('sec', a)
}

/** Satırın durumu: simge + ton + metin üçlüsü (renk tek başına anlam taşımaz). */
function durum(a: Aday): { simge: SimgeAdi; ton: Ton } {
  if (a.buSeferdeRol) return { simge: 'bilgi', ton: 'bilgi' }
  if (!a.uygun) return { simge: 'engel', ton: 'engel' }
  return a.ihlaller.length > 0
    ? { simge: 'uyari', ton: 'uyari' }
    : { simge: 'basari', ton: 'basari' }
}

function durumEtiketi(a: Aday): string {
  if (a.buSeferdeRol) return `Bu seferde: ${ROL_ETIKETLERI[a.buSeferdeRol]}`
  if (!a.uygun) return 'Uygun değil'
  return a.ihlaller.length > 0 ? 'Uyarı var' : 'Uygun'
}

const ihlalSimgesi = (i: Aday['ihlaller'][number]): SimgeAdi =>
  i.siddet === 'ENGEL' ? 'engel' : 'uyari'

/** OK-47a: yorgunluk ayrıntısı yalnız odaklı adayda açılır (satıra ya da içindeki öğeye odak). */
const acikSatir = ref<number | null>(null)
function odakCikti(e: FocusEvent, i: number) {
  const hedef = e.relatedTarget as Node | null
  if (acikSatir.value === i && !(hedef && (e.currentTarget as HTMLElement).contains(hedef)))
    acikSatir.value = null
}

// Yaylanan (roving) odak: satırlar ↑/↓ ile gezilir, Tab listeye tek durakla girer.
const odak = ref(0)
const govde = ref<HTMLElement | null>(null)

/** Önerilen (yoksa ilk) satıra odak: masa, kullanıcı panele geçmişken sefer ilerleyince çağırır (MK-154). */
function odakla(): boolean {
  const i = Math.max(
    0,
    props.adaylar.findIndex((a) => a === onerilen.value),
  )
  const satir = govde.value?.querySelectorAll<HTMLElement>('tr[data-aday]')[i]
  odak.value = i
  satir?.focus()
  return !!satir
}
defineExpose({ odakla })

/** Yalnız satırın KENDİSİNDEKİ tuşlar: satır içindeki özet (+N ihlal daha) ve düğmeler kendi Enter/Boşluk davranışını korur (W2). */
function tus(e: KeyboardEvent, i: number, a: Aday) {
  if (e.target !== e.currentTarget) return
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
      <!-- Satırlar CSS grid (ad | saat | km, altında durum): tablo anlamı rol öznitelikleriyle korunur. -->
      <table
        class="tablo aday-tablo"
        :class="{ 'fark-var': farkVar }"
        role="table"
        aria-label="Aday personel"
        :aria-busy="yukleniyor"
      >
        <thead role="rowgroup">
          <tr role="row">
            <th role="columnheader"><span class="gorunmez">Simge</span></th>
            <th role="columnheader">Personel</th>
            <th role="columnheader" class="sayisal">Bu ay saat</th>
            <th role="columnheader" class="sayisal">Bu ay km</th>
            <th role="columnheader" class="gorunmez">Durum</th>
            <th
              v-if="farkVar"
              role="columnheader"
              class="sayisal kol-fark"
              title="Bu ay saatinin aday havuzu ortalamasına göre farkı (yalnız bilgi; sıralamayı etkilemez)"
            >
              Fark
            </th>
          </tr>
        </thead>
        <tbody ref="govde" role="rowgroup">
          <tr
            v-for="(a, i) in adaylar"
            role="row"
            :key="a.makinistId"
            data-aday
            class="aday"
            :class="[
              `aday-${durum(a).ton}`,
              { soluk: !secilebilir(a), secilebilir: secilebilir(a) },
            ]"
            :tabindex="i === odak ? 0 : -1"
            :aria-disabled="secilebilir(a) ? undefined : 'true'"
            @focus="odak = i"
            @focusin="acikSatir = i"
            @focusout="odakCikti($event, i)"
            @click="sec(a)"
            @keydown="tus($event, i, a)"
          >
            <td role="cell" class="aday-simge" aria-hidden="true">
              <Simge :ad="durum(a).simge" boyut="1.1rem" />
            </td>
            <td role="cell">
              <span class="aday-ad">{{ a.adSoyad }}</span>
              <small class="alt-bilgi"
                >{{ a.sicil }} · {{ a.bagliGar
                }}<YakinlikNotu
                  v-if="yakinlik && yakinlikSatiri(a)"
                  bolum="rozet"
                  :satir="yakinlikSatiri(a)!"
                  :esikler="yakinlik.esikler"
              /></small>
            </td>
            <td role="cell" class="sayisal">{{ sayiBicimle(a.buAySaat) }}</td>
            <td role="cell" class="sayisal">{{ sayiBicimle(a.buAyKm) }}</td>
            <td role="cell" class="aday-durum">
              <Rozet
                :ton="durum(a).ton"
                :simge="durum(a).simge"
                :metin="durumEtiketi(a)"
                boyut="sm"
              />
              <Rozet v-if="a === onerilen" ton="basari" metin="Önerilen" boyut="sm" />
              <YakinlikNotu
                v-if="yakinlik && yakinlikSatiri(a)"
                bolum="ayrinti"
                :satir="yakinlikSatiri(a)!"
                :esikler="yakinlik.esikler"
                :acik="acikSatir === i"
              />
              <YorgunlukOzeti
                v-if="yakinlik && yakinlikSatiri(a)"
                :yorgunluk="yakinlikSatiri(a)!.yorgunluk"
                :esikler="yakinlik.esikler"
              />
              <ul v-if="a.ihlaller.length > 0" class="ihlal-listesi">
                <li>
                  <Simge :ad="ihlalSimgesi(a.ihlaller[0]!)" boyut="1em" />
                  {{ a.ihlaller[0]!.metin }}
                </li>
              </ul>
              <details v-if="a.ihlaller.length > 1" class="ihlal-daha" @click.stop>
                <summary>+{{ a.ihlaller.length - 1 }} ihlal daha</summary>
                <ul class="ihlal-listesi">
                  <li v-for="(ih, k) in a.ihlaller.slice(1)" :key="k">
                    <Simge :ad="ihlalSimgesi(ih)" boyut="1em" />
                    {{ ih.metin }}
                  </li>
                </ul>
              </details>
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
            <td v-if="farkVar" role="cell" class="sayisal aday-fark">
              {{ farkMetni(a) }}
            </td>
          </tr>
          <tr v-if="!yukleniyor && adaylar.length === 0" role="row" class="aday-bos">
            <td role="cell" colspan="5">
              <VeriDurumu
                durum="bos"
                :metin="`Aday personel yok.${
                  rol === 'KONDUKTOR' ? ' Önce Personel sayfasından kondüktör kartı açın.' : ''
                }`"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="farkVar" class="soluk fark-notu">
      Fark: bu ay saatinin aday havuzu ortalamasına göre farkı; yalnız bilgi.
    </p>
    <p
      v-if="yakinlik && yakinlikHaritasi.size > 0"
      class="soluk fark-notu"
      data-test="yakinlik-aciklama"
    >
      Sınıra yakınlık ve yorgunluk bilgi amaçlıdır; engel ya da uyarı üretmez, sıralamayı etkilemez.
      Yorgunluk göstergesi bilimsel bir yorgunluk modeli değildir: son
      {{ yakinlik.esikler.pencereGun }} günün üç ham değeridir (vurgu eşikleri: çalışma
      {{ yakinlik.esikler.toplamSaat }} s üstü, gece nöbeti {{ yakinlik.esikler.geceAdet }} ve üstü,
      en kısa dinlenme {{ yakinlik.esikler.dinlenmeSaat }} s altı).
    </p>
    <NobetNotu :metinler="ihlalMetinleri" />
  </div>
</template>

<style scoped>
.rol-secici {
  display: flex;
  flex-wrap: wrap;
  gap: var(--aralik-3) var(--aralik-5);
  border: 0;
  padding: 0;
  margin: 0 0 var(--aralik-3);
}
.rol-secici legend {
  font-weight: var(--yazi-agirlik-orta);
  font-size: var(--yazi-sm);
  padding: 0;
  margin-bottom: var(--aralik-2);
}
.rol-secici label {
  display: inline-flex;
  align-items: center;
  gap: var(--aralik-2);
}
.aday-tablo {
  font-size: var(--yazi-sm);
}
.aday.secilebilir {
  cursor: pointer;
}
.aday.secilebilir:hover td {
  background: var(--renk-n-100);
}
/* Seçilemeyen aday: opacity YOK (kontrast); ad koyu, ayrıntı soluk, imleç ve zemin farklı. */
.aday.soluk {
  cursor: not-allowed;
  color: var(--renk-metin-soluk);
}
.aday.soluk td {
  background: var(--renk-n-50);
}
.aday.soluk .aday-ad {
  color: var(--renk-metin-ikincil);
}
.aday-ad {
  font-weight: var(--yazi-agirlik-orta);
  white-space: nowrap;
}
.aday:focus-visible {
  outline-offset: -3px;
}
.aday-simge {
  padding-right: 0;
}
.aday-basari .aday-simge {
  color: var(--renk-basari-metin);
}
.aday-uyari .aday-simge {
  color: var(--renk-uyari-metin);
}
.aday-engel .aday-simge {
  color: var(--renk-engel-metin);
}
.aday-bilgi .aday-simge {
  color: var(--renk-bilgi-metin);
}
/* Aday = iki katmanlı kart: üstte simge | ad + sicil | saat | km, altında durum rozeti ve ihlal metni (ad dar sütunda üst üste binmez). */
.aday-tablo,
.aday-tablo thead,
.aday-tablo tbody {
  display: block;
}
.aday-tablo tr {
  display: grid;
  grid-template-columns: 1.4rem minmax(0, 1fr) 4.5rem 4rem;
  column-gap: var(--aralik-2);
  align-items: baseline;
  padding: var(--aralik-3);
  border-bottom: 1px solid var(--renk-cizgi);
}
.aday-tablo thead tr {
  padding-block: var(--aralik-2);
  background: var(--renk-n-100);
}
.aday-tablo tbody tr:last-child {
  border-bottom: 0;
}
.aday-tablo th,
.aday-tablo td {
  padding: 0;
  border: 0;
  background: none;
  white-space: nowrap;
}
.aday-tablo.fark-var tr {
  grid-template-columns: 1.4rem minmax(0, 1fr) 4.5rem 4rem 3.5rem;
}
.aday-tablo td.aday-fark,
.aday-tablo th.kol-fark {
  grid-column: 5;
  grid-row: 1;
}
.fark-notu {
  margin: var(--aralik-2) 0 0;
  font-size: var(--yazi-xs);
}
.aday-tablo td.aday-durum {
  grid-column: 1 / -1;
  grid-row: 2;
  white-space: normal;
  margin-top: var(--aralik-2);
}
.aday-tablo tr.aday-bos {
  display: block;
}
.aday-tablo tr.aday-bos td {
  white-space: normal;
}
.aday.soluk td {
  background: none;
}
.aday.soluk {
  background: var(--renk-n-50);
}
.aday.secilebilir:hover td {
  background: none;
}
.aday.secilebilir:hover {
  background: var(--renk-n-100);
}
.alt-bilgi {
  display: block;
  color: var(--renk-metin-soluk);
}
.ihlal-listesi {
  margin: var(--aralik-2) 0 0;
  padding-left: 0;
  list-style: none;
}
.ihlal-listesi li {
  display: flex;
  gap: var(--aralik-2);
  align-items: flex-start;
}
.ihlal-listesi .simge {
  flex: none;
  margin-top: 0.15em;
}
</style>
