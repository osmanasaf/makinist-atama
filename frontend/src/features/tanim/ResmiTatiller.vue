<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { yoneticiMi } from '@/composables/oturum'
import { bugun, tarihBicimle } from '@/lib/tarih'
import { tanimApi, type ResmiTatil } from './tanimApi'

/**
 * Resmi tatil listesi (F5, MK-178): yönetici elle girer; YALNIZ GÖSTERİM — aylık çizelge başlığında işaret. Kuralları, puantajı, adaleti ve çizelge toplamlarını etkilemez. Bayram tarihleri sistemde
 * hazır gelmez (doğruluğu bu ürünün iddiası değildir). Personel salt okur.
 */
const yil = ref(Number(bugun().slice(0, 4))) // İstanbul günü (yıl dönümünde yerel saat yanıltmasın)
const liste = ref<ResmiTatil[]>([])
const yukleniyor = ref(true)
const hata = ref('')
const bilgi = ref('')
const mesgul = ref(false)
const tarih = ref('')
const ad = ref('')

const gecerli = computed(() => tarih.value !== '' && ad.value.trim() !== '')
const hataYaz = (e: unknown, yedek: string) =>
  (hata.value = e instanceof ApiHata ? e.detail : yedek)

/** Yükleme sırası: yıl hızlı değişirse eski yılın yanıtı yeninin üstüne yazılmaz. */
let sira = 0
async function yukle() {
  const bu = ++sira
  try {
    const sonuc = await tanimApi.resmiTatiller(yil.value)
    if (bu === sira) liste.value = sonuc
  } catch (e) {
    if (bu !== sira) return
    liste.value = []
    hataYaz(e, 'Resmi tatiller yüklenemedi.')
  } finally {
    if (bu === sira) yukleniyor.value = false
  }
}

/** Odak kaybolmasın (WCAG 2.4.3): silinen satırın düğmesi artık yok; sıradaki tatil için forma dönülür. */
async function formaDon() {
  await nextTick()
  document.getElementById('tatil-tarih')?.focus()
}
onMounted(yukle)

async function yilDegisti() {
  if (!Number.isInteger(yil.value)) return
  hata.value = ''
  bilgi.value = ''
  await yukle()
}

async function ekle() {
  if (!gecerli.value || mesgul.value) return
  mesgul.value = true
  hata.value = ''
  bilgi.value = ''
  try {
    await tanimApi.tatilEkle({ tarih: tarih.value, ad: ad.value.trim() })
    const eklenenYil = Number(tarih.value.slice(0, 4))
    bilgi.value =
      eklenenYil === yil.value
        ? 'Resmi tatil eklendi.'
        : `Resmi tatil eklendi; ${eklenenYil} yılının listesi gösteriliyor.`
    yil.value = eklenenYil // eklenen tatil görünen listede olsun
    tarih.value = ''
    ad.value = ''
    await yukle()
    await formaDon() // sıradaki tatil
  } catch (e) {
    hataYaz(e, 'Resmi tatil kaydedilemedi.') // form değerleri korunur
  } finally {
    mesgul.value = false
  }
}

async function sil(t: ResmiTatil) {
  if (mesgul.value || !window.confirm(`${tarihBicimle(t.tarih)} ${t.ad} resmi tatili silinsin mi?`))
    return
  mesgul.value = true
  hata.value = ''
  bilgi.value = ''
  try {
    await tanimApi.tatilSil(t.tarih)
    bilgi.value = 'Resmi tatil silindi.'
    await yukle()
    await formaDon()
  } catch (e) {
    hataYaz(e, 'Resmi tatil silinemedi.')
  } finally {
    mesgul.value = false
  }
}
</script>

<template>
  <section class="resmi-tatiller" aria-labelledby="tatil-baslik" data-test="resmi-tatiller">
    <h2 id="tatil-baslik">Resmi tatiller</h2>
    <p class="soluk">
      Aylık çizelgenin sütun başlığında "Tatil" olarak işaretlenir; yalnız gösterim amaçlıdır,
      kuralları, puantajı ve adalet raporunu etkilemez. Tarihleri yönetici elle girer.
    </p>
    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="bilgi" class="bildirim bildirim-bilgi" role="status">{{ bilgi }}</p>
    <div class="arac-cubugu">
      <div class="alan">
        <label for="tatil-yil">Yıl</label>
        <input
          id="tatil-yil"
          v-model.number="yil"
          class="sayisal"
          type="number"
          min="2000"
          max="2100"
          @change="yilDegisti"
        />
      </div>
    </div>
    <form v-if="yoneticiMi" class="arac-cubugu" data-test="tatil-formu" @submit.prevent="ekle">
      <div class="alan alan-zorunlu">
        <label for="tatil-tarih">Tarih</label>
        <input id="tatil-tarih" v-model="tarih" type="date" required />
      </div>
      <div class="alan alan-zorunlu">
        <label for="tatil-ad">Ad</label>
        <input id="tatil-ad" v-model="ad" type="text" maxlength="80" required autocomplete="off" />
      </div>
      <button
        type="submit"
        class="btn btn-birincil"
        :disabled="!gecerli"
        :aria-disabled="mesgul ? 'true' : undefined"
      >
        Ekle
      </button>
    </form>
    <VeriDurumu v-if="yukleniyor" durum="yukleniyor" metin="Resmi tatiller yükleniyor…" />
    <div v-else-if="liste.length > 0" class="tablo-kap">
      <table class="tablo" aria-label="Resmi tatiller">
        <thead>
          <tr>
            <th>Tarih</th>
            <th>Ad</th>
            <th v-if="yoneticiMi"><span class="gorunmez">İşlemler</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in liste" :key="t.tarih" data-test="tatil-satiri">
            <td>{{ tarihBicimle(t.tarih) }}</td>
            <td>{{ t.ad }}</td>
            <td v-if="yoneticiMi">
              <button
                type="button"
                class="btn btn-kucuk btn-tehlike"
                :aria-label="`${tarihBicimle(t.tarih)} ${t.ad} resmi tatilini sil`"
                :aria-disabled="mesgul ? 'true' : undefined"
                @click="sil(t)"
              >
                Sil
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <VeriDurumu v-else-if="!hata" durum="bos" metin="Tanımlı resmi tatil yok." />
  </section>
</template>

<style scoped>
.resmi-tatiller {
  margin-top: var(--aralik-6);
}
</style>
