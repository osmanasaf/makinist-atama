<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { yoneticiMi } from '@/composables/oturum'
import { tanimApi, type GarIntikalSuresi, type TanimDto } from './tanimApi'

/**
 * Gar çifti intikal süreleri (İ-2, MK-109): "yolculukla ata"da servis ile intikal satırını besler. Yönsüz (Ankara–Eskişehir = Eskişehir–Ankara). Boşken ya da çift tanımsızken intikal
 * önerilmez (varsayılan süre yok: yanlış saat üretmez). Yönetici düzenler, personel salt okur.
 */
const sureler = ref<GarIntikalSuresi[]>([])
const garlar = ref<TanimDto[]>([])
const hata = ref('')
const bilgi = ref('')
const mesgul = ref(false)
const garA = ref(0)
const garB = ref(0)
const dakika = ref<number | ''>('')

const mevcut = computed(() => {
  const [a, b] = [Math.min(garA.value, garB.value), Math.max(garA.value, garB.value)]
  return sureler.value.find((s) => s.garAId === a && s.garBId === b) ?? null
})
const gecerli = computed(
  () =>
    garA.value > 0 &&
    garB.value > 0 &&
    garA.value !== garB.value &&
    typeof dakika.value === 'number' &&
    dakika.value >= 1 &&
    dakika.value <= 1440,
)

const hataYaz = (e: unknown, yedek: string) =>
  (hata.value = e instanceof ApiHata ? e.detail : yedek)

async function yukle() {
  try {
    sureler.value = await tanimApi.intikalSureleri()
  } catch (e) {
    sureler.value = []
    hataYaz(e, 'Gar süreleri yüklenemedi.')
  }
}

onMounted(async () => {
  hata.value = ''
  await yukle()
  if (yoneticiMi.value) {
    try {
      garlar.value = await tanimApi.liste('GAR')
    } catch (e) {
      hataYaz(e, 'Gar listesi yüklenemedi.')
    }
  }
})

function duzenle(s: GarIntikalSuresi) {
  garA.value = s.garAId
  garB.value = s.garBId
  dakika.value = s.dakika
  bilgi.value = ''
}

async function kaydet() {
  if (!gecerli.value || mesgul.value) return
  mesgul.value = true
  hata.value = ''
  bilgi.value = ''
  try {
    const kayit = mevcut.value
    await tanimApi.intikalSuresiYaz({
      garAId: garA.value,
      garBId: garB.value,
      dakika: dakika.value as number,
      ...(kayit ? { versiyon: kayit.versiyon } : {}),
    })
    bilgi.value = kayit ? 'Süre güncellendi.' : 'Süre eklendi.'
    garA.value = 0
    garB.value = 0
    dakika.value = ''
    await yukle()
  } catch (e) {
    hataYaz(e, 'Süre kaydedilemedi.')
    if (e instanceof ApiHata && e.cakismaMi) await yukle()
  } finally {
    mesgul.value = false
  }
}

async function sil(s: GarIntikalSuresi) {
  if (mesgul.value || !window.confirm(`${s.garA} – ${s.garB} süresi silinsin mi?`)) return
  mesgul.value = true
  hata.value = ''
  bilgi.value = ''
  try {
    await tanimApi.intikalSuresiSil(s.garAId, s.garBId)
    bilgi.value = 'Süre silindi.'
    await yukle()
  } catch (e) {
    hataYaz(e, 'Süre silinemedi.')
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section class="gar-sureleri" aria-labelledby="gar-sure-baslik" data-test="gar-sureleri">
    <h2 id="gar-sure-baslik">Gar süreleri</h2>
    <p class="soluk">
      İki gar arası servis / otobüs / taksi süresi (dakika). Tanımlıysa "yolculukla ata"da tek bir
      serbest intikal önerilir; tanımsız çiftte öneri yoktur.
    </p>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>
    <form v-if="yoneticiMi" class="arac-cubugu" data-test="sure-formu" @submit.prevent="kaydet">
      <div class="alan">
        <label for="sure-gar-a">Gar</label>
        <select id="sure-gar-a" v-model="garA">
          <option :value="0" disabled>Seçin…</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="sure-gar-b">Gar</label>
        <select id="sure-gar-b" v-model="garB">
          <option :value="0" disabled>Seçin…</option>
          <option v-for="g in garlar" :key="g.id" :value="g.id">{{ g.ad }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="sure-dk">Süre (dk)</label>
        <input id="sure-dk" v-model.number="dakika" type="number" min="1" max="1440" />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul || !gecerli">
        {{ mevcut ? 'Güncelle' : 'Ekle' }}
      </button>
      <p v-if="garA > 0 && garA === garB" class="soluk" data-test="ayni-gar-ipucu">
        Süre iki farklı gar arasında tanımlanır.
      </p>
    </form>
    <div class="tablo-kap">
      <table class="tablo" aria-label="Gar süreleri">
        <thead>
          <tr>
            <th>Gar çifti</th>
            <th class="sayisal">Süre</th>
            <th v-if="yoneticiMi"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sureler" :key="`${s.garAId}-${s.garBId}`" data-test="sure-satiri">
            <td>{{ s.garA }} – {{ s.garB }}</td>
            <td class="sayisal">{{ s.dakika }} dk</td>
            <td v-if="yoneticiMi">
              <button
                type="button"
                class="btn btn-kucuk"
                :aria-label="`${s.garA} – ${s.garB} süresini düzenle`"
                @click="duzenle(s)"
              >
                Düzenle
              </button>
              <button
                type="button"
                class="btn btn-kucuk btn-tehlike"
                :aria-label="`${s.garA} – ${s.garB} süresini sil`"
                :disabled="mesgul"
                @click="sil(s)"
              >
                Sil
              </button>
            </td>
          </tr>
          <tr v-if="sureler.length === 0">
            <td :colspan="yoneticiMi ? 3 : 2" class="bos-durum">Tanımlı süre yok.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.gar-sureleri {
  margin-top: 1.5rem;
}
.btn-kucuk {
  padding: 0.1rem 0.5rem;
  font-size: 0.85rem;
  margin-left: 0.25rem;
}
</style>
