<script setup lang="ts">
import AlanHatasi from '@/bilesenler/AlanHatasi.vue'
import { nextTick, onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { bugun, tarihBicimle, zamanBicimle } from '@/lib/tarih'
import { sayiBicimle } from '@/lib/sayi'
import Rozet from '@/bilesenler/Rozet.vue'
import SayfaBasligi from '@/bilesenler/SayfaBasligi.vue'
import VeriDurumu from '@/bilesenler/VeriDurumu.vue'
import { raporApi, type YedekRaporu } from './raporApi'

const ay = ref(bugun().slice(0, 7))
const rapor = ref<YedekRaporu | null>(null)
const hata = ref('')
const ayHata = ref('')
const ayAlani = ref<HTMLInputElement | null>(null)
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  ayHata.value = ''
  if (!ay.value) {
    ayHata.value = 'Ay seçin.'
    await nextTick()
    ayAlani.value?.focus()
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
  <section class="rapor-sayfa">
    <SayfaBasligi baslik="Yedek planı" />

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan alan-zorunlu">
        <label for="yedek-ay">Ay</label>
        <input
          id="yedek-ay"
          ref="ayAlani"
          v-model="ay"
          type="month"
          required
          :aria-invalid="ayHata ? 'true' : undefined"
          :aria-describedby="ayHata ? 'yedek-ay-hata' : undefined"
        />
        <AlanHatasi id="yedek-ay-hata" :metin="ayHata" />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <VeriDurumu v-if="mesgul" durum="yukleniyor" metin="Rapor hazırlanıyor…" />
    <VeriDurumu v-else-if="hata" durum="hata" :metin="hata" />

    <template v-if="rapor">
      <div class="kart ozet" data-test="ozet">
        <p>
          Eksik gün sayısı: <strong class="sayisal">{{ rapor.eksikGunSayisi }}</strong> · Toplam
          açık: <strong class="sayisal">{{ rapor.toplamAcik }}</strong> · Aktif makinist:
          <strong class="sayisal">{{ rapor.aktifMakinist }}</strong>
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
                <Rozet v-if="g.acik > 0" ton="uyari" simge="uyari" :metin="`${g.acik} eksik`" />
                <Rozet v-else ton="basari" simge="basari" metin="Yeterli" />
              </td>
            </tr>
            <tr v-if="rapor.gunler.length === 0">
              <td colspan="7"><VeriDurumu durum="bos" metin="Bu ay için gün kaydı yok." /></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.rapor-sayfa {
  display: grid;
  gap: var(--aralik-4);
  align-content: start;
}
.rapor-sayfa :deep(.sayfa-baslik),
.rapor-sayfa .arac-cubugu,
.rapor-sayfa .alan {
  margin-bottom: 0;
}
.ozet p {
  margin: 0 0 var(--aralik-2);
}
.eksik td {
  background: var(--renk-uyari-zemin);
}
</style>
