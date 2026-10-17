<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { bugun, tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import { raporApi, type YedekRaporu } from './raporApi'

const ay = ref(bugun().slice(0, 7))
const rapor = ref<YedekRaporu | null>(null)
const hata = ref('')
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  if (!ay.value) {
    hata.value = 'Ay seçin.'
    return
  }
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await raporApi.yedek(ay.value)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor alınamadı.'
  } finally {
    mesgul.value = false
  }
}

// Boş sayfa yerine varsayılan ayın raporu hemen gelir.
onMounted(getir)
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>Yedek planı</h1></div>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan">
        <label for="yedek-ay">Ay</label>
        <input id="yedek-ay" v-model="ay" type="month" required />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="mesgul" class="soluk">Rapor hazırlanıyor…</p>

    <template v-if="rapor">
      <div class="kart ozet" data-test="ozet">
        <p>
          Eksik gün sayısı: <strong>{{ rapor.eksikGunSayisi }}</strong> · Toplam açık:
          <strong>{{ rapor.toplamAcik }}</strong> · Aktif makinist:
          <strong>{{ rapor.aktifMakinist }}</strong>
        </p>
        <p class="soluk" data-test="yer-tutucu">
          Yedek oranı ve asgari sayı kaynaksız yer tutucudur (oran {{ sayiBicimle(rapor.oran) }}, en
          az {{ rapor.min }}).
        </p>
        <p class="soluk">
          Üretim zamanı: {{ zamanBicimle(rapor.uretimZamani) }} · Üreten: {{ rapor.ureten }}
        </p>
      </div>
      <div class="tablo-kap">
        <table class="tablo" aria-label="Günlük yedek planı">
          <thead>
            <tr>
              <th>Tarih</th>
              <th class="sayisal">Planlı sefer</th>
              <th class="sayisal">İhtiyaç</th>
              <th class="sayisal">Mevcut</th>
              <th class="sayisal">İzinli</th>
              <th class="sayisal">Görevli</th>
              <th>Durum</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in rapor.gunler" :key="g.tarih" :class="{ eksik: g.acik > 0 }">
              <td>{{ tarihBicimle(g.tarih) }}</td>
              <td class="sayisal">{{ g.planliSefer }}</td>
              <td class="sayisal">{{ g.ihtiyac }}</td>
              <td class="sayisal">{{ g.mevcut }}</td>
              <td class="sayisal">{{ g.izinli }}</td>
              <td class="sayisal">{{ g.gorevli }}</td>
              <td>
                <strong v-if="g.acik > 0"
                  ><span aria-hidden="true">⚠</span> {{ g.acik }} eksik</strong
                >
                <span v-else>Yeterli</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.ozet {
  margin-bottom: 1rem;
}
.ozet p {
  margin: 0 0 0.25rem;
}
.eksik td {
  background: var(--renk-uyari-zemin);
}
</style>
