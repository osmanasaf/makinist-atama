<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { saatBicimle } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import { seferApi, type Sefer } from '@/features/sefer/seferApi'
import { tanimApi, type TanimDto } from '@/features/tanim/tanimApi'
import { atamaApi, type BolNokta, type BolSonucu } from './atamaApi'

/**
 * Seferi ara istasyonlardan böl (MK-128/129). İki adım: form -> sunucu önizlemesi (`onizle:true`) -> uygula (`onizle:false`).
 * Parça hesabı istemcide YAPILMAZ; tablo sunucunun döndürdüğüdür. API çağrılarını kendisi yapar: 400/409/422 diyalog açıkken gösterilir.
 */
const props = defineProps<{
  seferId: number
  seferEtiketi: string
}>()
const emit = defineEmits<{
  /** `yenile`: hata aldık, sunucu durumu bizim bildiğimizden farklı olabilir; masa yeniden okumalı. */
  kapat: [yenile: boolean]
  tamam: [mesaj: string]
}>()

const EN_COK_NOKTA = 5

interface NoktaGirdisi {
  anahtar: number
  garId: number | ''
  varis: string
  kalkis: string
  km: number | string
  hatId: number | ''
  aracTipiId: number | ''
}
let sonAnahtar = 0
const yeniNokta = (): NoktaGirdisi => ({
  anahtar: ++sonAnahtar,
  garId: '',
  varis: '',
  kalkis: '',
  km: '',
  hatId: '',
  aracTipiId: '',
})

const sefer = ref<Sefer | null>(null)
const garlar = ref<TanimDto[]>([])
const hatlar = ref<TanimDto[]>([])
const aracTipleri = ref<TanimDto[]>([])
const noktalar = ref<NoktaGirdisi[]>([yeniNokta()])
const adim = ref<'form' | 'onizleme'>('form')
const onizleme = ref<BolSonucu | null>(null)
const mesgul = ref(false)
const genelHata = ref('')
let hataOldu = false

// Seferin uç garları ara istasyon olamaz (sunucu da reddeder).
const secenekGarlar = computed(() =>
  garlar.value.filter((g) => g.id !== sefer.value?.kalkisGarId && g.id !== sefer.value?.varisGarId),
)
// Nokta km'si yalnız sefer km'si doluyken girilir (doluyken zorunlu).
const kmVar = computed(() => sefer.value?.km != null)

const kmGecerli = (km: number | string) => String(km).trim() !== '' && Number(km) >= 0
const gecerli = computed(
  () =>
    sefer.value !== null &&
    noktalar.value.every(
      (n) =>
        n.garId !== '' && n.varis !== '' && n.kalkis !== '' && (!kmVar.value || kmGecerli(n.km)),
    ),
)

const dialog = ref<HTMLDialogElement | null>(null)
onMounted(async () => {
  dialog.value?.showModal()
  try {
    ;[sefer.value, garlar.value, hatlar.value, aracTipleri.value] = await Promise.all([
      seferApi.getir(props.seferId),
      tanimApi.liste('GAR'),
      tanimApi.liste('HAT'),
      tanimApi.liste('ARAC_TIPI'),
    ])
  } catch (e) {
    genelHata.value = e instanceof ApiHata ? e.detail : 'Sefer bilgisi okunamadı.'
  }
})

function vazgec() {
  if (!mesgul.value) emit('kapat', hataOldu)
}

function noktaEkle() {
  if (noktalar.value.length < EN_COK_NOKTA) noktalar.value.push(yeniNokta())
}
function noktaCikar(i: number) {
  if (noktalar.value.length > 1) noktalar.value.splice(i, 1)
}

function govde(onizle: boolean) {
  return {
    versiyon: sefer.value!.versiyon,
    noktalar: noktalar.value.map((n): BolNokta => ({
      garId: Number(n.garId),
      varisSaat: n.varis,
      kalkisSaat: n.kalkis,
      ...(kmVar.value ? { km: Number(n.km) } : {}),
      ...(n.hatId !== '' ? { hatId: Number(n.hatId) } : {}),
      ...(n.aracTipiId !== '' ? { aracTipiId: Number(n.aracTipiId) } : {}),
    })),
    onizle,
  }
}

function yeniKirmiziMetni(s: BolSonucu): string {
  if (s.yeniKirmizi.length === 0) return ''
  const liste = s.yeniKirmizi.map((k) => `${k.trenNo} (${k.nedenler.join('; ')})`).join(', ')
  return ` Dikkat, ${s.yeniKirmizi.length} atama kontrol bekliyor: ${liste}.`
}

async function ilerle() {
  if (mesgul.value || !gecerli.value) return
  mesgul.value = true
  genelHata.value = ''
  const uygula = adim.value === 'onizleme'
  try {
    const sonuc = await atamaApi.bol(props.seferId, govde(!uygula))
    if (uygula) {
      emit('tamam', `Sefer ${sonuc.parcalar.length} parçaya bölündü.${yeniKirmiziMetni(sonuc)}`)
    } else {
      onizleme.value = sonuc
      adim.value = 'onizleme'
    }
  } catch (e) {
    hataOldu = true
    genelHata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
  } finally {
    mesgul.value = false
  }
}

function geri() {
  genelHata.value = ''
  adim.value = 'form'
}

/** "5s30dk" */
function sureMetni(p: Sefer): string {
  const dk = Math.round((Date.parse(p.guncelVaris) - Date.parse(p.guncelKalkis)) / 60000)
  return `${Math.floor(dk / 60)}s${dk % 60}dk`
}
</script>

<template>
  <dialog ref="dialog" aria-labelledby="bol-dlg-baslik" @cancel.prevent="vazgec">
    <form @submit.prevent="ilerle">
      <h2 id="bol-dlg-baslik">Seferi böl</h2>
      <p>{{ seferEtiketi }}</p>
      <p class="bildirim bildirim-uyari">
        <span>
          <span aria-hidden="true">⚠</span> Mevcut atamalar 1. parçada kalır; 2. parça boş açılır;
          bu seferdeki açık takas/bırakma talepleri geçersiz olur.
        </span>
      </p>

      <template v-if="adim === 'form'">
        <fieldset v-for="(n, i) in noktalar" :key="n.anahtar" class="bol-nokta">
          <legend>{{ i + 1 }}. ara istasyon</legend>
          <div class="alan">
            <label :for="`bol-gar-${i}`">Gar</label>
            <select :id="`bol-gar-${i}`" v-model="n.garId" required>
              <option value="">Seçin…</option>
              <option v-for="g in secenekGarlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
            </select>
          </div>
          <div class="alan">
            <label :for="`bol-varis-${i}`">Varış saati</label>
            <input :id="`bol-varis-${i}`" v-model="n.varis" type="time" required />
          </div>
          <div class="alan">
            <label :for="`bol-kalkis-${i}`">Kalkış saati</label>
            <input :id="`bol-kalkis-${i}`" v-model="n.kalkis" type="time" required />
          </div>
          <div v-if="kmVar" class="alan">
            <label :for="`bol-km-${i}`">Bu noktaya kadar KM</label>
            <input :id="`bol-km-${i}`" v-model="n.km" type="number" min="0" step="any" required />
          </div>
          <div class="alan">
            <label :for="`bol-hat-${i}`">Hat (boşsa aynı)</label>
            <select :id="`bol-hat-${i}`" v-model="n.hatId">
              <option value="">Aynı</option>
              <option v-for="h in hatlar" :key="h.id" :value="h.id">{{ h.ad }}</option>
            </select>
          </div>
          <div class="alan">
            <label :for="`bol-arac-${i}`">Araç tipi (boşsa aynı)</label>
            <select :id="`bol-arac-${i}`" v-model="n.aracTipiId">
              <option value="">Aynı</option>
              <option v-for="a in aracTipleri" :key="a.id" :value="a.id">{{ a.ad }}</option>
            </select>
          </div>
          <button
            type="button"
            class="btn btn-kucuk"
            :disabled="noktalar.length <= 1 || mesgul"
            :aria-label="`${i + 1}. ara istasyonu çıkar`"
            @click="noktaCikar(i)"
          >
            Noktayı çıkar
          </button>
        </fieldset>
        <button
          type="button"
          class="btn"
          :disabled="noktalar.length >= EN_COK_NOKTA || mesgul"
          @click="noktaEkle"
        >
          + Nokta ekle
        </button>
      </template>

      <template v-else-if="onizleme">
        <div class="tablo-kap onizleme-kap">
          <table class="tablo onizleme-tablo" aria-label="Önizleme: oluşacak parçalar">
            <thead>
              <tr>
                <th>Parça</th>
                <th>Güzergâh</th>
                <th>Saat</th>
                <th>Süre</th>
                <th>KM</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in onizleme.parcalar" :key="p.id">
                <td>{{ p.parcaNo }}/{{ p.parcaSayisi }}</td>
                <td>{{ p.kalkisGar }}→{{ p.varisGar }}</td>
                <td>{{ saatBicimle(p.guncelKalkis) }}–{{ saatBicimle(p.guncelVaris) }}</td>
                <td>{{ sureMetni(p) }}</td>
                <td>{{ p.km != null ? `${sayiBicimle(p.km)} km` : '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div
          v-if="onizleme.yeniKirmizi.length > 0"
          data-test="yeni-kirmizi"
          class="bildirim bildirim-uyari"
        >
          <p><span aria-hidden="true">⛔</span> Bölme şu atamaları kontrol bekler hâle getirir:</p>
          <ul>
            <li v-for="k in onizleme.yeniKirmizi" :key="k.atamaId">
              {{ k.trenNo }}: {{ k.nedenler.join('; ') }}
            </li>
          </ul>
        </div>
      </template>

      <div role="alert">
        <p v-if="genelHata" class="hata-mesaji">{{ genelHata }}</p>
      </div>

      <div class="eylemler">
        <button type="submit" class="btn btn-birincil" :disabled="!gecerli || mesgul">
          {{ adim === 'form' ? 'Önizle' : 'Böl' }}
        </button>
        <button
          v-if="adim === 'onizleme'"
          type="button"
          class="btn"
          :disabled="mesgul"
          @click="geri"
        >
          Geri
        </button>
        <button type="button" class="btn" :disabled="mesgul" @click="vazgec">Vazgeç</button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.bol-nokta {
  margin: 0 0 var(--bosluk);
}
.onizleme-tablo th,
.onizleme-tablo td {
  padding: 0.25rem 0.4rem;
}
/* 375 px: KM sütunu kırpılmasın (tablo-kap yatay kaydırır, hücreler sıkılaşır). */
@media (max-width: 480px) {
  .onizleme-tablo {
    font-size: 0.85rem;
  }
}
</style>
