<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
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
  if (Object.keys(h).length > 0) return
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
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <form class="kart" style="margin-bottom: 1rem" novalidate @submit.prevent="kaydet">
    <h2>{{ sefer ? 'Seferi düzenle' : 'Yeni sefer' }}</h2>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <div class="form-izgara">
      <div class="alan">
        <label for="trenNo">Tren no</label>
        <input id="trenNo" v-model="f.trenNo" :aria-invalid="!!alanHatalari.trenNo" />
        <p v-if="alanHatalari.trenNo" class="alan-hata">{{ alanHatalari.trenNo }}</p>
      </div>
      <div class="alan">
        <label for="s-tarih">Tarih</label>
        <input id="s-tarih" v-model="f.tarih" type="date" />
        <p v-if="alanHatalari.tarih" class="alan-hata">{{ alanHatalari.tarih }}</p>
      </div>
      <div class="alan">
        <label for="kalkisGar">Kalkış gar</label>
        <select id="kalkisGar" v-model="f.kalkisGarId">
          <option value="">Seçin</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <p v-if="alanHatalari.kalkisGarId" class="alan-hata">{{ alanHatalari.kalkisGarId }}</p>
      </div>
      <div class="alan">
        <label for="kalkisSaat">Kalkış saati</label>
        <input id="kalkisSaat" v-model="f.kalkisSaat" type="time" />
        <p v-if="alanHatalari.kalkisSaat" class="alan-hata">{{ alanHatalari.kalkisSaat }}</p>
      </div>
      <div class="alan">
        <label for="varisGar">Varış gar</label>
        <select id="varisGar" v-model="f.varisGarId">
          <option value="">Seçin</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
        <p v-if="alanHatalari.varisGarId" class="alan-hata">{{ alanHatalari.varisGarId }}</p>
      </div>
      <div class="alan">
        <label for="varisSaat">Varış saati</label>
        <input id="varisSaat" v-model="f.varisSaat" type="time" />
        <p v-if="alanHatalari.varisSaat" class="alan-hata">{{ alanHatalari.varisSaat }}</p>
      </div>
      <div class="alan">
        <label for="varisTarihi">Varış tarihi (boşsa otomatik)</label>
        <input id="varisTarihi" v-model="f.varisTarihi" type="date" />
        <p v-if="alanHatalari.varisTarihi" class="alan-hata">{{ alanHatalari.varisTarihi }}</p>
      </div>
      <div class="alan">
        <label for="aracTipi">Araç tipi</label>
        <select id="aracTipi" v-model="f.aracTipiId">
          <option value="">Seçin</option>
          <option v-for="a in aracTipleri" :key="a.id" :value="a.id">{{ a.ad }}</option>
        </select>
        <p v-if="alanHatalari.aracTipiId" class="alan-hata">{{ alanHatalari.aracTipiId }}</p>
      </div>
      <div class="alan">
        <label for="hat">Hat</label>
        <select id="hat" v-model="f.hatId">
          <option value="">Seçin</option>
          <option v-for="h in hatlar" :key="h.id" :value="h.id">{{ h.ad }}</option>
        </select>
        <p v-if="alanHatalari.hatId" class="alan-hata">{{ alanHatalari.hatId }}</p>
      </div>
      <div class="alan">
        <label for="km">Km</label>
        <input id="km" v-model="f.km" type="number" min="0" step="any" />
        <p v-if="alanHatalari.km" class="alan-hata">{{ alanHatalari.km }}</p>
      </div>
    </div>
    <div class="eylemler">
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Kaydet</button>
      <button type="button" class="btn" @click="emit('iptal')">Vazgeç</button>
    </div>
  </form>
</template>
