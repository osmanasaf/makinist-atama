<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { NEDEN_ETIKETLERI } from '@/features/atama/atamaApi'
import { zamanBicimle } from '@/lib/tarih'
import { takasApi, type TakasListe, type TakasSatiri } from './takasApi'
import TakasOnayDialog from './TakasOnayDialog.vue'
import { TAKAS_DURUM_ETIKETLERI, TAKAS_TUR_ETIKETLERI } from './takasTipleri'

/** Atamacı: açık ve son 7 günde kapanan takas / bırakma talepleri. Onay bekleyenler üstte vurgulanır; ihlal ayrıntısı yalnız onay ekranındadır. */
const veri = ref<TakasListe | null>(null)
const yukleniyor = ref(true)
const hata = ref('')
const bilgi = ref('')
const acik = ref<number | null>(null)

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    veri.value = await takasApi.liste()
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Talepler yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}
onMounted(yukle)

/** Onay bekleyenler önce, sonra en yeni. */
const siraliTalepler = computed(() =>
  [...(veri.value?.talepler ?? [])].sort(
    (a, b) =>
      Number(b.durum === 'ATAMACI_ONAYI_BEKLIYOR') - Number(a.durum === 'ATAMACI_ONAYI_BEKLIYOR') ||
      b.id - a.id,
  ),
)

const taraf = (s: TakasSatiri) =>
  s.tur === 'TAKAS' ? `${s.kaynakSicil} ⇄ ${s.hedefSicil}` : `${s.kaynakSicil} bırakıyor`

function tamam(mesaj: string) {
  acik.value = null
  bilgi.value = mesaj
  void yukle()
}
</script>

<template>
  <section>
    <h1>Takas talepleri</h1>
    <p class="soluk">
      Karşılıklı takasta önce karşı personel kabul eder; kurallar onay anında iki yönlü yeniden
      denetlenir. Bırakma talebi onaylanırsa yerine biri atanır ya da görev boş kalır.
    </p>
    <div role="status">
      <p v-if="bilgi" class="bildirim bildirim-bilgi">{{ bilgi }}</p>
    </div>
    <p v-if="yukleniyor" role="status">Yükleniyor…</p>
    <div v-else-if="hata" class="bildirim bildirim-hata" role="alert">
      <p>{{ hata }}</p>
      <button type="button" class="btn" @click="yukle">Yeniden dene</button>
    </div>
    <template v-else-if="veri">
      <p data-test="takas-bekleyen">
        Onay bekleyen: <strong>{{ veri.bekleyen }}</strong>
      </p>
      <p v-if="veri.talepler.length === 0" class="soluk">Takas ya da bırakma talebi yok.</p>
      <div v-else class="tablo-kap">
        <table class="tablo" aria-label="Takas talepleri">
          <thead>
            <tr>
              <th>Tür</th>
              <th>Taraflar</th>
              <th>Görevler</th>
              <th>Neden</th>
              <th>Durum</th>
              <th>Açılış</th>
              <th><span class="gorunmez">İşlem</span></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="s in siraliTalepler"
              :key="s.id"
              :class="{ yaklasiyor: s.durum === 'ATAMACI_ONAYI_BEKLIYOR' }"
              :data-test="'talep-' + s.id"
            >
              <td>{{ TAKAS_TUR_ETIKETLERI[s.tur] }}</td>
              <td>{{ taraf(s) }}</td>
              <td>
                {{ s.kaynakGorev }}
                <template v-if="s.hedefGorev"><br />{{ s.hedefGorev }}</template>
              </td>
              <td>
                {{ NEDEN_ETIKETLERI[s.neden] }}
                <template v-if="s.nedenNotu"
                  ><br /><small>{{ s.nedenNotu }}</small></template
                >
              </td>
              <td>
                <span aria-hidden="true">{{ TAKAS_DURUM_ETIKETLERI[s.durum].simge }}</span>
                {{ TAKAS_DURUM_ETIKETLERI[s.durum].ad }}
              </td>
              <td>{{ zamanBicimle(s.olusturma) }}</td>
              <td>
                <button
                  type="button"
                  class="btn"
                  :class="{ 'btn-birincil': s.durum === 'ATAMACI_ONAYI_BEKLIYOR' }"
                  :aria-label="`${taraf(s)} talebini ${s.durum === 'ATAMACI_ONAYI_BEKLIYOR' ? 'incele' : 'göster'}`"
                  @click="acik = s.id"
                >
                  {{ s.durum === 'ATAMACI_ONAYI_BEKLIYOR' ? 'İncele' : 'Göster' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <TakasOnayDialog v-if="acik !== null" :talep-id="acik" @kapat="acik = null" @tamam="tamam" />
  </section>
</template>
