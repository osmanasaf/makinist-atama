<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import type { Ihlal } from '@/api/tipler'
import NobetNotu from './NobetNotu.vue'
import {
  atamaApi,
  NEDEN_ETIKETLERI,
  ROL_ETIKETLERI,
  type Aday,
  type AtamaGovde,
  type AtamaNedeni,
  type AtamaRolu,
  type GunMakinisti,
  type KaldirSonucu,
} from './atamaApi'

/**
 * Atama onayı. `aday` verilirse atama (rolde mevcut biri varsa "değiştir"), verilmezse `mevcut` makinistin kaldırılması.
 * API çağrısını kendisi yapar: kural ihlali (422) ve alan hataları (400) diyalog açıkken gösterilir.
 */
const props = defineProps<{
  seferId: number
  seferEtiketi: string
  rol: AtamaRolu
  aday?: Aday
  /** Rolde şu an görevli olan (değiştirilecek ya da kaldırılacak). */
  mevcut?: GunMakinisti | null
}>()
const emit = defineEmits<{
  /** `yenile`: hata aldık, sunucu durumu bizim bildiğimizden farklı olabilir; masa yeniden okumalı. */
  kapat: [yenile: boolean]
  tamam: [mesaj: string]
}>()

const kaldirKipi = computed(() => !props.aday)
const uyarilar = computed<Ihlal[]>(
  () => props.aday?.ihlaller.filter((i) => i.siddet === 'UYARI') ?? [],
)
const nedenGerekli = computed(() => kaldirKipi.value || !!props.mevcut)

const gerekce = ref('')
const neden = ref<AtamaNedeni | ''>('')
const nedenNotu = ref('')
const mesgul = ref(false)
const genelHata = ref('')
const ihlaller = ref<Ihlal[]>([])
const alanHatalari = ref<Record<string, string>>({})
let hataOldu = false

const nobetMetinleri = computed(() => [
  ...(props.aday?.ihlaller ?? []).map((i) => i.metin),
  ...ihlaller.value.map((i) => i.metin),
  genelHata.value,
])

const gerekceEksik = computed(() => uyarilar.value.length > 0 && gerekce.value.trim() === '')
const nedenEksik = computed(() => nedenGerekli.value && neden.value === '')
const notEksik = computed(
  () => nedenGerekli.value && neden.value === 'DIGER' && nedenNotu.value.trim() === '',
)
const gecerli = computed(() => !gerekceEksik.value && !nedenEksik.value && !notEksik.value)

const baslik = computed(() => {
  if (kaldirKipi.value) return 'Atamayı kaldır'
  if (props.rol === 'KONDUKTOR') return props.mevcut ? 'Kondüktörü değiştir' : 'Kondüktör ata'
  return props.mevcut ? 'Makinisti değiştir' : 'Makinist ata'
})

const dialog = ref<HTMLDialogElement | null>(null)
onMounted(() => dialog.value?.showModal())

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

/** Kaldırma başka atamayı kırmızı yaptıysa (ör. nöbet bölünür, ardışık gece haftası K-18) atamacıya söylenir. */
function kaldirMesaji(sonuc?: KaldirSonucu): string {
  const gorevler = [
    ...(sonuc?.yeniKirmizi ?? []).map((y) => ({ ad: y.trenNo, neden: y.nedenler[0] })),
    ...(sonuc?.yeniKirmiziIntikal ?? []).map((y) => ({ ad: y.ozet, neden: y.nedenler[0] })),
  ]
  if (gorevler.length === 0) return 'Atama kaldırıldı.'
  const ozet = gorevler
    .slice(0, 3)
    .map((y) => `${y.ad} (${y.neden ?? 'kural ihlali'})`)
    .join('; ')
  return `Atama kaldırıldı. Dikkat: bu makinistin ${gorevler.length} başka görevi kırmızı oldu: ${ozet}${gorevler.length > 3 ? ' …' : ''}`
}

async function onayla() {
  if (mesgul.value || !gecerli.value) return
  mesgul.value = true
  genelHata.value = ''
  ihlaller.value = []
  alanHatalari.value = {}
  const nedenAlanlari = nedenGerekli.value
    ? { neden: neden.value as AtamaNedeni, nedenNotu: nedenNotu.value.trim() || undefined }
    : {}
  try {
    if (props.aday) {
      const govde: AtamaGovde = {
        makinistId: props.aday.makinistId,
        rol: props.rol,
        gerekce: gerekce.value.trim() || undefined,
        ...nedenAlanlari,
      }
      await atamaApi.ata(props.seferId, govde)
      emit('tamam', 'Atama yapıldı.')
    } else {
      const sonuc = await atamaApi.kaldir(props.seferId, {
        rol: props.rol,
        makinistId: props.rol === 'YOLCU' ? props.mevcut?.makinistId : undefined, // bir seferde birden çok yolcu olabilir
        neden: neden.value as AtamaNedeni,
        nedenNotu: nedenNotu.value.trim() || undefined,
      })
      emit('tamam', kaldirMesaji(sonuc))
    }
  } catch (e) {
    hataOldu = true
    if (e instanceof ApiHata && e.kod === 'KURAL_IHLALI') {
      // detail başlıktır; metni içinde geçen ihlali listede ikinci kez basma.
      ihlaller.value = (e.ihlaller ?? []).filter((i) => !e.detail.includes(i.metin))
      genelHata.value = e.detail
    } else if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) {
      alanHatalari.value = e.alanlar
    } else {
      genelHata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
    }
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <dialog ref="dialog" aria-labelledby="atama-dlg-baslik" @cancel.prevent="vazgec">
    <form @submit.prevent="onayla">
      <h2 id="atama-dlg-baslik">{{ baslik }}</h2>
      <p>
        {{ seferEtiketi }} · <strong>{{ ROL_ETIKETLERI[rol] }}</strong>
      </p>
      <p v-if="aday">
        {{ mevcut ? 'Göreve gelecek' : 'Atanacak' }}:
        <strong>{{ aday.adSoyad }} ({{ aday.sicil }})</strong>
      </p>
      <p v-if="mevcut">
        {{ kaldirKipi ? 'Kaldırılacak' : 'Görevden çıkacak' }}:
        <strong>{{ mevcut.ad }} ({{ mevcut.sicil }})</strong>
      </p>

      <template v-if="uyarilar.length > 0">
        <div class="bildirim bildirim-uyari" role="group" aria-label="Uyarılar">
          <ul class="ihlal-listesi">
            <li v-for="(u, i) in uyarilar" :key="i">
              <span aria-hidden="true">⚠</span> {{ u.metin }}
            </li>
          </ul>
        </div>
        <div class="alan">
          <label for="atama-gerekce">Gerekçe (zorunlu)</label>
          <textarea
            id="atama-gerekce"
            v-model="gerekce"
            required
            :aria-invalid="!!alanHatalari.gerekce"
            :aria-describedby="alanHatalari.gerekce ? 'atama-gerekce-hata' : undefined"
          ></textarea>
          <p v-if="alanHatalari.gerekce" id="atama-gerekce-hata" class="alan-hata">
            {{ alanHatalari.gerekce }}
          </p>
        </div>
      </template>

      <template v-if="nedenGerekli">
        <div class="alan">
          <label for="atama-neden">{{
            kaldirKipi ? 'Kaldırma nedeni' : 'Değiştirme nedeni'
          }}</label>
          <select
            id="atama-neden"
            v-model="neden"
            required
            :aria-invalid="!!alanHatalari.neden"
            :aria-describedby="alanHatalari.neden ? 'atama-neden-hata' : undefined"
          >
            <option value="">Seçin…</option>
            <option v-for="(etiket, k) in NEDEN_ETIKETLERI" :key="k" :value="k">
              {{ etiket }}
            </option>
          </select>
          <p v-if="alanHatalari.neden" id="atama-neden-hata" class="alan-hata">
            {{ alanHatalari.neden }}
          </p>
        </div>
        <div v-if="neden === 'DIGER'" class="alan">
          <label for="atama-neden-notu">Neden notu (zorunlu)</label>
          <textarea
            id="atama-neden-notu"
            v-model="nedenNotu"
            required
            :aria-invalid="!!alanHatalari.nedenNotu"
            :aria-describedby="alanHatalari.nedenNotu ? 'atama-neden-notu-hata' : undefined"
          ></textarea>
          <p v-if="alanHatalari.nedenNotu" id="atama-neden-notu-hata" class="alan-hata">
            {{ alanHatalari.nedenNotu }}
          </p>
        </div>
      </template>

      <div role="alert">
        <p v-if="genelHata" class="hata-mesaji">{{ genelHata }}</p>
        <ul v-if="ihlaller.length > 0" class="ihlal-listesi hata-mesaji">
          <li v-for="(ih, i) in ihlaller" :key="i">
            <span aria-hidden="true">{{ ih.siddet === 'ENGEL' ? '⛔' : '⚠' }}</span> {{ ih.metin }}
          </li>
        </ul>
      </div>
      <NobetNotu :metinler="nobetMetinleri" />

      <div class="eylemler">
        <button
          type="submit"
          class="btn"
          :class="kaldirKipi ? 'btn-tehlike' : 'btn-birincil'"
          :disabled="!gecerli || mesgul"
        >
          {{ kaldirKipi ? 'Kaldır' : 'Onayla' }}
        </button>
        <button type="button" class="btn" :disabled="mesgul" @click="vazgec">Vazgeç</button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.ihlal-listesi {
  margin: 0;
  padding-left: 0;
  list-style: none;
}
</style>
