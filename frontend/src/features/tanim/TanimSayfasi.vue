<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { yoneticiMi } from '@/composables/oturum'
import { TANIM_ETIKETLERI, tanimApi, type TanimDto, type TanimTipi } from './tanimApi'
import GarSureleri from './GarSureleri.vue'

const tipler = Object.keys(TANIM_ETIKETLERI) as TanimTipi[]
const sekme = ref<TanimTipi>('GAR')
const liste = ref<TanimDto[]>([])
const yeniAd = ref('')
const duzenlenen = ref<number | null>(null)
const duzenAd = ref('')
const hata = ref('')
const mesgul = ref(false)

function hataYaz(e: unknown) {
  hata.value = e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.'
}

async function yukle() {
  hata.value = ''
  try {
    liste.value = await tanimApi.liste(sekme.value)
  } catch (e) {
    liste.value = []
    hataYaz(e)
  }
}

async function sekmeSec(t: TanimTipi) {
  sekme.value = t
  duzenlenen.value = null
  yeniAd.value = ''
  await yukle()
}

async function ekle() {
  const ad = yeniAd.value.trim()
  if (!ad || mesgul.value) return
  mesgul.value = true
  hata.value = ''
  try {
    await tanimApi.ekle(sekme.value, ad)
    yeniAd.value = ''
    await yukle()
  } catch (e) {
    hataYaz(e)
  } finally {
    mesgul.value = false
  }
}

function duzenle(t: TanimDto) {
  duzenlenen.value = t.id
  duzenAd.value = t.ad
}

async function kaydet(t: TanimDto) {
  const ad = duzenAd.value.trim()
  if (!ad || mesgul.value) return
  mesgul.value = true
  hata.value = ''
  try {
    await tanimApi.adlandir(t.id, ad)
    duzenlenen.value = null
    await yukle()
  } catch (e) {
    hataYaz(e)
  } finally {
    mesgul.value = false
  }
}

onMounted(yukle)
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>Tanımlar</h1></div>
    <div class="arac-cubugu" role="tablist" aria-label="Tanım türü">
      <button
        v-for="t in tipler"
        :key="t"
        type="button"
        role="tab"
        class="btn"
        :class="{ 'btn-birincil': t === sekme }"
        :aria-selected="t === sekme"
        @click="sekmeSec(t)"
      >
        {{ TANIM_ETIKETLERI[t] }}
      </button>
    </div>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <form v-if="yoneticiMi" class="arac-cubugu" @submit.prevent="ekle">
      <div class="alan">
        <label for="yeni-ad">Yeni ad</label>
        <input id="yeni-ad" v-model="yeniAd" maxlength="100" />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul || !yeniAd.trim()">
        Ekle
      </button>
    </form>
    <div class="tablo-kap">
      <table class="tablo">
        <thead>
          <tr>
            <th>Ad</th>
            <th v-if="yoneticiMi"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in liste" :key="t.id">
            <td>
              <input
                v-if="duzenlenen === t.id"
                v-model="duzenAd"
                :aria-label="`${t.ad} yeni adı`"
                maxlength="100"
                @keydown.enter.prevent="kaydet(t)"
              />
              <template v-else>{{ t.ad }}</template>
            </td>
            <td v-if="yoneticiMi">
              <template v-if="duzenlenen === t.id">
                <button
                  type="button"
                  class="btn btn-birincil"
                  :disabled="mesgul"
                  @click="kaydet(t)"
                >
                  Kaydet
                </button>
                <button type="button" class="btn" @click="duzenlenen = null">Vazgeç</button>
              </template>
              <button v-else type="button" class="btn" @click="duzenle(t)">Yeniden adlandır</button>
            </td>
          </tr>
          <tr v-if="liste.length === 0">
            <td :colspan="yoneticiMi ? 2 : 1" class="bos-durum">Kayıt yok.</td>
          </tr>
        </tbody>
      </table>
    </div>
    <GarSureleri v-if="sekme === 'GAR'" />
  </section>
</template>
