<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { computed, onMounted, ref, useTemplateRef } from 'vue'
import { ApiHata } from '@/api/istemci'
import Diyalog from '@/bilesenler/Diyalog.vue'
import Simge from '@/bilesenler/Simge.vue'
import { atamaApi, NEDEN_ETIKETLERI, type AtamaNedeni } from './atamaApi'

/**
 * Sefer iptali ya da gecikme girişi (tek bileşen, iki kip). API çağrısını kendisi yapar:
 * 400/409 hataları diyalog açıkken gösterilir.
 */
const props = defineProps<{
  kip: 'iptal' | 'gecikme'
  seferId: number
  seferEtiketi: string
  /** Gecikme kipinde alanların başlangıç değeri (planlanana göre toplam gecikme). */
  kalkisGecikmeDk?: number
  varisGecikmeDk?: number
  /** Ara istasyonda makinist değişen trenin parçası (F2c); sonraki parça varsa "devam" kutusu çıkar. */
  parcaNo?: number | null
  parcaSayisi?: number | null
}>()
const emit = defineEmits<{
  /** `yenile`: hata aldık, sunucu durumu bizim bildiğimizden farklı olabilir; masa yeniden okumalı. */
  kapat: [yenile: boolean]
  tamam: [mesaj: string]
}>()

const EN_COK_DK = 1440
const iptalMi = props.kip === 'iptal'
// "Sefer iptali" nedeni yalnız iptal akışında anlamlı.
const nedenler = Object.entries(NEDEN_ETIKETLERI).filter(([k]) => iptalMi || k !== 'IPTAL')

// Gecikme kipinde neden varsayılan "Gecikme" (H4: yazma/seçim gerekmesin); iptalde bilinçli seçim gerekir.
const neden = ref<AtamaNedeni | ''>(iptalMi ? '' : 'GECIKME')
const nedenNotu = ref('')
// type=number girdisinde v-model sayıya çevirir (boşsa '' kalır).
const kalkis = ref<string | number>(props.kalkisGecikmeDk ?? 0)
// Varış kalkışla aynıysa boş bırakılır ("boşsa kalkışla aynı"): kalkışı değiştirmek varışı eski değerde bırakıp varışı kalkıştan öne düşürmesin (BUG-11).
const varis = ref<string | number>(
  props.varisGecikmeDk && props.varisGecikmeDk !== props.kalkisGecikmeDk
    ? props.varisGecikmeDk
    : '',
)
// Zincir kutusu yalnız sonraki parçası olan parçalı seferde görünür; varsayılan işaretli (sunucu varsayılanıyla aynı). Aksi halde istek gövdesi eskisiyle aynıdır.
const sonrakiVar =
  props.parcaNo != null && props.parcaSayisi != null && props.parcaNo < props.parcaSayisi
const devam = ref(true)

// Hızlı gecikme (H4): toplam gecikme çipleri; tıklayınca değer yazılır, odak dk alanına döner (Enter gönderir).
const CIPLER = [15, 30, 60, 90, 120]
const kalkisAlani = useTemplateRef<HTMLInputElement>('kalkisAlani')
function cipSec(dk: number) {
  kalkis.value = dk
  kalkisAlani.value?.focus()
}
onMounted(() => {
  // Diyalog (alt bileşen) önce bağlanıp modalı açtı; açılışta odak ve seçim dk alanında.
  kalkisAlani.value?.focus()
  kalkisAlani.value?.select()
})
// Varış yalnız kalkıştan farklıysa önemlidir: kayıtlı varış ≠ kalkış ise ayrıntı açık başlar.
const varisAcik = ref(
  props.varisGecikmeDk != null && props.varisGecikmeDk !== props.kalkisGecikmeDk,
)
const mesgul = ref(false)
const genelHata = ref('')
const alanHatalari = ref<Record<string, string>>({})
let hataOldu = false

const dakikaGecerli = (deger: string | number) => {
  const n = Number(deger)
  return String(deger).trim() !== '' && Number.isInteger(n) && n >= 0 && n <= EN_COK_DK
}
const gecerli = computed(
  () =>
    neden.value !== '' &&
    !(neden.value === 'DIGER' && nedenNotu.value.trim() === '') &&
    (iptalMi ||
      (dakikaGecerli(kalkis.value) && (varis.value === '' || dakikaGecerli(varis.value)))),
)

// Birincil eylem kapalıyken nedeni söylenir (Diyalog neden slotu); ilk sağlanmayan koşul.
const nedenMetni = computed(() => {
  if (neden.value === '') return 'Önce neden seçin.'
  if (neden.value === 'DIGER' && nedenNotu.value.trim() === '') return 'Neden notu zorunlu.'
  if (iptalMi) return ''
  if (!dakikaGecerli(kalkis.value)) return `Kalkış gecikmesi 0–${EN_COK_DK} dk olmalı.`
  if (varis.value !== '' && !dakikaGecerli(varis.value))
    return `Varış gecikmesi 0–${EN_COK_DK} dk olmalı.`
  return ''
})

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

async function onayla() {
  if (mesgul.value || !gecerli.value) return
  mesgul.value = true
  genelHata.value = ''
  alanHatalari.value = {}
  const ortak = {
    neden: neden.value as AtamaNedeni,
    nedenNotu: nedenNotu.value.trim() || undefined,
  }
  try {
    if (iptalMi) {
      await atamaApi.seferIptal(
        props.seferId,
        sonrakiVar ? { ...ortak, devam: devam.value } : ortak,
      )
      emit('tamam', 'Sefer iptal edildi.')
    } else {
      await atamaApi.seferGecikme(props.seferId, {
        kalkisGecikmeDk: Number(kalkis.value),
        varisGecikmeDk: varis.value === '' ? undefined : Number(varis.value),
        ...ortak,
        ...(sonrakiVar ? { devam: devam.value } : {}),
      })
      const yeniVaris = varis.value === '' ? Number(kalkis.value) : Number(varis.value)
      const azaldi =
        Number(kalkis.value) < (props.kalkisGecikmeDk ?? 0) ||
        yeniVaris < (props.varisGecikmeDk ?? props.kalkisGecikmeDk ?? 0)
      emit(
        'tamam',
        sonrakiVar && devam.value && azaldi
          ? 'Gecikme kaydedildi. Sonraki parçalar değişmedi (yayılım yalnız artırır); gerekirse tek tek düzeltin.'
          : 'Gecikme kaydedildi.',
      )
    }
  } catch (e) {
    hataOldu = true
    if (e instanceof ApiHata && e.kod === 'DOGRULAMA' && e.alanlar) alanHatalari.value = e.alanlar
    else genelHata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <Diyalog
    :baslik="iptalMi ? 'Seferi iptal et' : 'Gecikme gir'"
    boyut="orta"
    form
    @kapat="vazgec"
    @gonder="onayla"
  >
    <p>{{ seferEtiketi }}</p>
    <p v-if="iptalMi" class="bildirim bildirim-uyari">
      <span><Simge ad="uyari" boyut="1em" /> Bu işlem geri alınamaz.</span>
    </p>

    <template v-if="!iptalMi">
      <div class="alan">
        <label for="sefer-kalkis-gecikme">Kalkış gecikmesi (dk, planlanana göre toplam)</label>
        <input
          id="sefer-kalkis-gecikme"
          ref="kalkisAlani"
          v-model="kalkis"
          autofocus
          type="number"
          min="0"
          :max="EN_COK_DK"
          step="1"
          required
          :aria-invalid="!!alanHatalari.kalkisGecikmeDk"
          :aria-describedby="alanHatalari.kalkisGecikmeDk ? 'sefer-kalkis-hata' : undefined"
        />
        <AlanHatasi id="sefer-kalkis-hata" :metin="alanHatalari.kalkisGecikmeDk" />
        <div class="suzgec" role="group" aria-label="Hızlı gecikme süresi (toplam, dk)">
          <button
            v-for="dk in CIPLER"
            :key="dk"
            type="button"
            class="btn btn-kucuk"
            :aria-pressed="Number(kalkis) === dk"
            @click="cipSec(dk)"
          >
            {{ dk }} dk
          </button>
        </div>
        <small v-if="(kalkisGecikmeDk ?? 0) > 0" class="soluk"
          >Şu an: {{ kalkisGecikmeDk }} dk</small
        >
      </div>
      <details
        class="varis-ayrinti"
        :open="varisAcik"
        @toggle="varisAcik = ($event.target as HTMLDetailsElement).open"
      >
        <summary>Varış farklıysa</summary>
        <div class="alan">
          <label for="sefer-varis-gecikme">Varış gecikmesi (dk, boşsa kalkışla aynı)</label>
          <input
            id="sefer-varis-gecikme"
            v-model="varis"
            type="number"
            min="0"
            :max="EN_COK_DK"
            step="1"
            :aria-invalid="!!alanHatalari.varisGecikmeDk"
            :aria-describedby="alanHatalari.varisGecikmeDk ? 'sefer-varis-hata' : undefined"
          />
          <AlanHatasi id="sefer-varis-hata" :metin="alanHatalari.varisGecikmeDk" />
        </div>
      </details>
    </template>

    <div v-if="sonrakiVar" class="alan alan-onay">
      <label for="sefer-devam">
        <input id="sefer-devam" v-model="devam" type="checkbox" />
        {{
          iptalMi
            ? 'Bu ve sonraki parçaları da iptal et'
            : 'Sonraki parçalara da yansıt (yalnız artırır)'
        }}
      </label>
    </div>

    <div class="alan">
      <label for="sefer-neden">Neden</label>
      <select
        id="sefer-neden"
        v-model="neden"
        required
        :aria-invalid="!!alanHatalari.neden"
        :aria-describedby="alanHatalari.neden ? 'sefer-neden-hata' : undefined"
      >
        <option value="">Seçin…</option>
        <option v-for="[k, etiket] in nedenler" :key="k" :value="k">{{ etiket }}</option>
      </select>
      <AlanHatasi id="sefer-neden-hata" :metin="alanHatalari.neden" />
    </div>
    <div v-if="neden === 'DIGER'" class="alan">
      <label for="sefer-neden-notu">Neden notu (zorunlu)</label>
      <textarea
        id="sefer-neden-notu"
        v-model="nedenNotu"
        required
        :aria-invalid="!!alanHatalari.nedenNotu"
        :aria-describedby="alanHatalari.nedenNotu ? 'sefer-neden-notu-hata' : undefined"
      ></textarea>
      <AlanHatasi id="sefer-neden-notu-hata" :metin="alanHatalari.nedenNotu" />
    </div>

    <div role="alert">
      <div v-if="genelHata" class="bildirim bildirim-hata">
        <p>{{ genelHata }}</p>
      </div>
    </div>

    <template v-if="nedenMetni" #neden>{{ nedenMetni }}</template>
    <template #eylemler>
      <button
        type="submit"
        class="btn"
        :class="iptalMi ? 'btn-tehlike' : 'btn-birincil'"
        :disabled="!gecerli || mesgul"
      >
        {{ iptalMi ? 'Seferi iptal et' : 'Kaydet' }}
      </button>
      <button type="button" class="btn" :disabled="mesgul" @click="vazgec">Vazgeç</button>
    </template>
  </Diyalog>
</template>

<style scoped>
.varis-ayrinti {
  margin-bottom: var(--aralik-4);
}
.varis-ayrinti summary {
  cursor: pointer;
  margin-bottom: var(--aralik-2);
}
</style>
