<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import Sekmeler from '@/bilesenler/Sekmeler.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { yoneticiMi } from '@/composables/oturum'
import { TANIM_ETIKETLERI, tanimApi, type TanimDto, type TanimTipi } from './tanimApi'
import GarSureleri from './GarSureleri.vue'
import ResmiTatiller from './ResmiTatiller.vue'

const tipler = Object.keys(TANIM_ETIKETLERI) as TanimTipi[]
const sekmeler = tipler.map((t) => ({ kimlik: t, ad: TANIM_ETIKETLERI[t] }))
const panelAdi = (t: TanimTipi) => `panel-${t}`
const sekme = ref<TanimTipi>('GAR')
const liste = ref<TanimDto[]>([])
const yukleniyor = ref(true)
const yeniAd = ref('')
const duzenlenen = ref<number | null>(null)
const duzenAd = ref('')
/** Liste yüklenemedi (VeriDurumu); ekleme ve yeniden adlandırma hataları kendi alanının altında. */
const hata = ref('')
const ekleHata = ref('')
const duzenHata = ref('')
const mesgul = ref(false)

const hataMetni = (e: unknown) => (e instanceof ApiHata ? e.detail : 'İşlem başarısız oldu.')
const odakla = async (kimlik: string) => {
  await nextTick()
  document.getElementById(kimlik)?.focus()
}

async function yukle() {
  hata.value = ''
  yukleniyor.value = true
  try {
    liste.value = await tanimApi.liste(sekme.value)
  } catch (e) {
    liste.value = []
    hata.value = hataMetni(e)
  } finally {
    yukleniyor.value = false
  }
}

async function sekmeSec(k: string) {
  sekme.value = k as TanimTipi
  duzenlenen.value = null
  yeniAd.value = ''
  ekleHata.value = ''
  duzenHata.value = ''
  await yukle()
}

async function ekle() {
  const ad = yeniAd.value.trim()
  if (!ad || mesgul.value) return
  mesgul.value = true
  ekleHata.value = ''
  try {
    await tanimApi.ekle(sekme.value, ad)
    yeniAd.value = ''
    await yukle()
  } catch (e) {
    ekleHata.value = hataMetni(e)
  } finally {
    mesgul.value = false
  }
  void odakla('yeni-ad') // başarıda sıradaki kayıt, hatada düzeltilecek alan
}

function duzenle(t: TanimDto) {
  duzenlenen.value = t.id
  duzenAd.value = t.ad
  duzenHata.value = ''
  void odakla(`tanim-duzen-${t.id}`)
}

function vazgec(t: TanimDto) {
  duzenlenen.value = null
  duzenHata.value = ''
  void odakla(`tanim-yeniden-${t.id}`)
}

async function kaydet(t: TanimDto) {
  const ad = duzenAd.value.trim()
  if (!ad || mesgul.value) return
  mesgul.value = true
  duzenHata.value = ''
  try {
    await tanimApi.adlandir(t.id, ad)
    duzenlenen.value = null
    await yukle()
    void odakla(`tanim-yeniden-${t.id}`)
  } catch (e) {
    duzenHata.value = hataMetni(e)
    void odakla(`tanim-duzen-${t.id}`)
  } finally {
    mesgul.value = false
  }
}

onMounted(yukle)
</script>

<template>
  <section>
    <SayfaBasligi baslik="Tanımlar" />
    <Sekmeler
      etiket="Tanım türleri"
      :sekmeler="sekmeler"
      :model-value="sekme"
      @update:model-value="sekmeSec"
    >
      <template v-for="t in tipler" :key="t" #[panelAdi(t)]>
        <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Tanımlar yükleniyor…" />
        <VeriDurumu v-else-if="hata" durum="hata" :metin="hata">
          <template #eylem>
            <button type="button" class="btn" @click="yukle">Yeniden dene</button>
          </template>
        </VeriDurumu>
        <template v-else>
          <form v-if="yoneticiMi" class="arac-cubugu" @submit.prevent="ekle">
            <div class="alan alan-zorunlu">
              <label for="yeni-ad">Yeni ad</label>
              <input
                id="yeni-ad"
                v-model="yeniAd"
                maxlength="100"
                :aria-invalid="ekleHata !== ''"
                :aria-describedby="ekleHata ? 'yeni-ad-hata' : undefined"
              />
              <AlanHatasi id="yeni-ad-hata" :metin="ekleHata" />
            </div>
            <button type="submit" class="btn btn-birincil" :disabled="mesgul || !yeniAd.trim()">
              Ekle
            </button>
          </form>
          <div v-if="liste.length > 0" class="tablo-kap">
            <table class="tablo" :aria-label="TANIM_ETIKETLERI[sekme]">
              <thead>
                <tr>
                  <th>Ad</th>
                  <th v-if="yoneticiMi"><span class="gorunmez">İşlemler</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="x in liste" :key="x.id">
                  <td>
                    <template v-if="duzenlenen === x.id">
                      <input
                        :id="`tanim-duzen-${x.id}`"
                        v-model="duzenAd"
                        :aria-label="`${x.ad} yeni adı`"
                        :aria-invalid="duzenHata !== ''"
                        :aria-describedby="duzenHata ? `tanim-duzen-hata-${x.id}` : undefined"
                        maxlength="100"
                        @keydown.enter.prevent="kaydet(x)"
                      />
                      <AlanHatasi :id="`tanim-duzen-hata-${x.id}`" :metin="duzenHata" />
                    </template>
                    <template v-else>{{ x.ad }}</template>
                  </td>
                  <td v-if="yoneticiMi" class="islem-hucre">
                    <template v-if="duzenlenen === x.id">
                      <button
                        type="button"
                        class="btn btn-birincil btn-kucuk"
                        :disabled="mesgul"
                        @click="kaydet(x)"
                      >
                        Kaydet
                      </button>
                      <button type="button" class="btn btn-kucuk" @click="vazgec(x)">Vazgeç</button>
                    </template>
                    <button
                      v-else
                      :id="`tanim-yeniden-${x.id}`"
                      type="button"
                      class="btn btn-kucuk"
                      @click="duzenle(x)"
                    >
                      Yeniden adlandır
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <VeriDurumu v-else durum="bos" metin="Kayıt yok." />
        </template>
      </template>
    </Sekmeler>
    <GarSureleri v-if="sekme === 'GAR'" />
    <ResmiTatiller />
  </section>
</template>

<style scoped>
.islem-hucre {
  white-space: nowrap;
}
.islem-hucre .btn + .btn {
  margin-left: var(--aralik-3);
}
</style>
