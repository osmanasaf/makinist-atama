<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { ilkHataliyaOdaklan } from '@/lib/odak'
import { zamanBicimle } from '@/lib/tarih'
import type { TanimDto } from '@/features/tanim/tanimApi'
import { seferApi, type Sefer } from './seferApi'

const props = defineProps<{
  sefer: Sefer | null
  gun: string
  garlar: TanimDto[]
  hatlar: TanimDto[]
  aracTipleri: TanimDto[]
}>()
const emit = defineEmits<{ kaydedildi: []; iptal: [] }>()

/** "dd.mm.yyyy HH:mm" (zamanBicimle çıktısı, Europe/Istanbul) -> [yyyy-mm-dd, HH:mm] */
function ayir(zaman: string): [string, string] {
  const m = /^(\d{2})\.(\d{2})\.(\d{4}) (\d{2}:\d{2})$/.exec(zamanBicimle(zaman))
  return m ? [`${m[3]}-${m[2]}-${m[1]}`, m[4]!] : ['', '']
}

const s = props.sefer
const [, kalkisSaat] = s ? ayir(s.planlananKalkis) : ['', '']
const [varisTarihi, varisSaat] = s ? ayir(s.planlananVaris) : ['', '']

const f = reactive({
  trenNo: s?.trenNo ?? '',
  tarih: s?.tarih ?? props.gun,
  kalkisGarId: (s?.kalkisGarId ?? '') as number | '',
  kalkisSaat,
  varisGarId: (s?.varisGarId ?? '') as number | '',
  varisSaat,
  varisTarihi, // boşsa sunucu kalkıştan sonraki ilk varış saatini alır
  aracTipiId: (s?.aracTipiId ?? '') as number | '',
  hatId: (s?.hatId ?? '') as number | '',
  km: s?.km != null ? String(s.km) : '',
})
const alanHatalari = ref<Record<string, string>>({})
const hata = ref('')
const mesgul = ref(false)
const formKok = ref<HTMLFormElement | null>(null)

async function kaydet() {
  if (mesgul.value) return
  hata.value = ''
  const h: Record<string, string> = {}
  if (!f.trenNo.trim()) h.trenNo = 'Tren no zorunludur.'
  if (!f.tarih) h.tarih = 'Tarih zorunludur.'
  if (f.kalkisGarId === '') h.kalkisGarId = 'Kalkış garı seçin.'
  if (f.varisGarId === '') h.varisGarId = 'Varış garı seçin.'
  if (!f.kalkisSaat) h.kalkisSaat = 'Kalkış saati zorunludur.'
  if (!f.varisSaat) h.varisSaat = 'Varış saati zorunludur.'
  if (f.aracTipiId === '') h.aracTipiId = 'Araç tipi seçin.'
  if (f.hatId === '') h.hatId = 'Hat seçin.'
  alanHatalari.value = h
  if (Object.keys(h).length > 0) {
    await ilkHataliyaOdaklan(formKok.value)
    return
  }
  const govde = {
    trenNo: f.trenNo.trim(),
    tarih: f.tarih,
    kalkisGarId: f.kalkisGarId as number,
    kalkisSaat: f.kalkisSaat,
    varisGarId: f.varisGarId as number,
    varisSaat: f.varisSaat,
    varisTarihi: f.varisTarihi || undefined,
    aracTipiId: f.aracTipiId as number,
    hatId: f.hatId as number,
    km: f.km === '' ? undefined : Number(f.km),
    versiyon: s?.versiyon,
  }
  mesgul.value = true
  try {
    if (s) await seferApi.guncelle(s.id, govde)
    else await seferApi.olustur(govde)
    emit('kaydedildi')
  } catch (e) {
    if (e instanceof ApiHata) {
      alanHatalari.value = e.alanlar ?? {}
      hata.value = e.detail
    } else hata.value = 'Sefer kaydedilemedi.'
    await ilkHataliyaOdaklan(formKok.value)
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <form
    ref="formKok"
    class="kart sefer-formu"
    novalidate
    aria-labelledby="sefer-formu-baslik"
    @submit.prevent="kaydet"
  >
    <h2 id="sefer-formu-baslik">{{ sefer ? 'Seferi düzenle' : 'Yeni sefer' }}</h2>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <div class="form-izgara">
      <div class="alan alan-zorunlu">
        <label for="trenNo">Tren no</label>
        <input
          id="trenNo"
          v-model="f.trenNo"
          :aria-invalid="!!alanHatalari.trenNo"
          :aria-describedby="alanHatalari.trenNo ? 'trenNo-hata' : undefined"
        />
        <AlanHatasi id="trenNo-hata" :metin="alanHatalari.trenNo" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="s-tarih">Tarih</label>
        <input
          id="s-tarih"
          v-model="f.tarih"
          type="date"
          :aria-invalid="!!alanHatalari.tarih"
          :aria-describedby="alanHatalari.tarih ? 's-tarih-hata' : undefined"
        />
        <AlanHatasi id="s-tarih-hata" :metin="alanHatalari.tarih" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="kalkisGar">Kalkış gar</label>
        <select
          id="kalkisGar"
          v-model="f.kalkisGarId"
          :aria-invalid="!!alanHatalari.kalkisGarId"
          :aria-describedby="alanHatalari.kalkisGarId ? 'kalkisGar-hata' : undefined"
        >
          <option value="">Seçin</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <AlanHatasi id="kalkisGar-hata" :metin="alanHatalari.kalkisGarId" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="kalkisSaat">Kalkış saati</label>
        <input
          id="kalkisSaat"
          v-model="f.kalkisSaat"
          type="time"
          :aria-invalid="!!alanHatalari.kalkisSaat"
          :aria-describedby="alanHatalari.kalkisSaat ? 'kalkisSaat-hata' : undefined"
        />
        <AlanHatasi id="kalkisSaat-hata" :metin="alanHatalari.kalkisSaat" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="varisGar">Varış gar</label>
        <select
          id="varisGar"
          v-model="f.varisGarId"
          :aria-invalid="!!alanHatalari.varisGarId"
          :aria-describedby="alanHatalari.varisGarId ? 'varisGar-hata' : undefined"
        >
          <option value="">Seçin</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <AlanHatasi id="varisGar-hata" :metin="alanHatalari.varisGarId" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="varisSaat">Varış saati</label>
        <input
          id="varisSaat"
          v-model="f.varisSaat"
          type="time"
          :aria-invalid="!!alanHatalari.varisSaat"
          :aria-describedby="alanHatalari.varisSaat ? 'varisSaat-hata' : undefined"
        />
        <AlanHatasi id="varisSaat-hata" :metin="alanHatalari.varisSaat" />
      </div>
      <div class="alan">
        <label for="varisTarihi">Varış tarihi (boşsa otomatik)</label>
        <input
          id="varisTarihi"
          v-model="f.varisTarihi"
          type="date"
          :aria-invalid="!!alanHatalari.varisTarihi"
          :aria-describedby="alanHatalari.varisTarihi ? 'varisTarihi-hata' : undefined"
        />
        <AlanHatasi id="varisTarihi-hata" :metin="alanHatalari.varisTarihi" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="aracTipi">Araç tipi</label>
        <select
          id="aracTipi"
          v-model="f.aracTipiId"
          :aria-invalid="!!alanHatalari.aracTipiId"
          :aria-describedby="alanHatalari.aracTipiId ? 'aracTipi-hata' : undefined"
        >
          <option value="">Seçin</option>
          <option v-for="g in aracTipleri" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <AlanHatasi id="aracTipi-hata" :metin="alanHatalari.aracTipiId" />
      </div>
      <div class="alan alan-zorunlu">
        <label for="hat">Hat</label>
        <select
          id="hat"
          v-model="f.hatId"
          :aria-invalid="!!alanHatalari.hatId"
          :aria-describedby="alanHatalari.hatId ? 'hat-hata' : undefined"
        >
          <option value="">Seçin</option>
          <option v-for="g in hatlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <AlanHatasi id="hat-hata" :metin="alanHatalari.hatId" />
      </div>
      <div class="alan">
        <label for="km">Km</label>
        <input
          id="km"
          v-model="f.km"
          type="number"
          min="0"
          step="any"
          :aria-invalid="!!alanHatalari.km"
          :aria-describedby="alanHatalari.km ? 'km-hata' : undefined"
        />
        <AlanHatasi id="km-hata" :metin="alanHatalari.km" />
      </div>
    </div>
    <div class="eylemler">
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Kaydet</button>
      <button type="button" class="btn" @click="emit('iptal')">Vazgeç</button>
    </div>
  </form>
</template>

<style scoped>
.sefer-formu {
  margin-bottom: var(--aralik-5);
}
</style>
