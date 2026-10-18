<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiHata } from '@/api/istemci'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { tarihBicimle } from '@/lib/tarih'
import {
  DURUM_ETIKETLERI,
  TUR_ETIKETLERI,
  makinistApi,
  type MakinistDurumu,
  type MakinistOzet,
  type PersonelTuru,
} from './makinistApi'

const arama = ref('')
const durum = ref<MakinistDurumu | ''>('')
const tur = ref<PersonelTuru | ''>('')
const liste = ref<MakinistOzet[]>([])
const hata = ref('')
const yukleniyor = ref(false)

async function yukle() {
  yukleniyor.value = true
  hata.value = ''
  try {
    liste.value = await makinistApi.liste(arama.value, durum.value, tur.value)
  } catch (e) {
    liste.value = []
    hata.value = e instanceof ApiHata ? e.detail : 'Liste yüklenemedi.'
  } finally {
    yukleniyor.value = false
  }
}

onMounted(yukle)
</script>

<template>
  <section>
    <SayfaBasligi baslik="Personel">
      <template #eylemler>
        <RouterLink class="btn btn-birincil" :to="{ name: 'makinist-yeni' }"
          >Yeni personel</RouterLink
        >
      </template>
    </SayfaBasligi>
    <form class="arac-cubugu" @submit.prevent="yukle">
      <div class="alan">
        <label for="arama">Sicil veya ad</label>
        <input id="arama" v-model="arama" type="search" />
      </div>
      <div class="alan">
        <label for="durum">Durum</label>
        <select id="durum" v-model="durum" @change="yukle">
          <option value="">Tümü</option>
          <option v-for="(etiket, k) in DURUM_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <div class="alan">
        <label for="tur">Tür</label>
        <select id="tur" v-model="tur" @change="yukle">
          <option value="">Tümü</option>
          <option v-for="(etiket, k) in TUR_ETIKETLERI" :key="k" :value="k">{{ etiket }}</option>
        </select>
      </div>
      <button type="submit" class="btn" :disabled="yukleniyor">Ara</button>
    </form>
    <VeriDurumu v-if="hata" durum="hata" :metin="hata">
      <template #eylem>
        <button type="button" class="btn" @click="yukle">Yeniden dene</button>
      </template>
    </VeriDurumu>
    <div class="tablo-kap">
      <table class="tablo" aria-label="Personel listesi">
        <thead>
          <tr>
            <th>Sicil</th>
            <th>Ad soyad</th>
            <th>Tür</th>
            <th>Bağlı gar</th>
            <th>Durum</th>
            <th>Ehliyet / belge bitişi</th>
            <th>Sağlık bitişi</th>
            <th>Psikoteknik bitişi</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in liste" :key="m.id">
            <td>
              <RouterLink :to="{ name: 'makinist-kart', params: { id: m.id } }">{{
                m.sicil
              }}</RouterLink>
            </td>
            <td>{{ m.adSoyad }}</td>
            <td>{{ TUR_ETIKETLERI[m.tur ?? 'MAKINIST'] }}</td>
            <td>{{ m.bagliGar }}</td>
            <td>{{ DURUM_ETIKETLERI[m.durum] }}</td>
            <td>{{ tarihBicimle(m.ehliyetBitis) }}</td>
            <td>{{ tarihBicimle(m.saglikBitis) }}</td>
            <td>{{ tarihBicimle(m.psikoteknikBitis) }}</td>
          </tr>
          <tr v-if="yukleniyor">
            <td colspan="8"><VeriDurumu durum="yukleniyor" metin="Yükleniyor…" /></td>
          </tr>
          <tr v-else-if="liste.length === 0 && !hata">
            <td colspan="8"><VeriDurumu durum="bos" metin="Kayıt bulunamadı." /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
