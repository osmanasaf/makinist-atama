<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiHata } from '@/api/istemci'
import { bugun, zamanBicimle } from '@/lib/tarih'
import { konaklamaApi, type KonaklamaRaporu } from './konaklamaApi'

/** Aylık konaklama gece raporu (F2b, MK-44): makinist x gar; ay = giriş günü; "kaydı eksik öneri" = konaklama gerektiren ama kaydı girilmemiş boşluklar. */
const ay = ref(bugun().slice(0, 7))
const rapor = ref<KonaklamaRaporu | null>(null)
/** Görünen raporun ayı: form sonradan değişse de Excel bununla çalışır. */
const uygulanan = ref('')
const hata = ref('')
const mesgul = ref(false)

async function getir() {
  if (mesgul.value) return
  hata.value = ''
  if (!ay.value) {
    hata.value = 'Ay seçin.'
    return
  }
  const secilen = ay.value
  mesgul.value = true
  rapor.value = null
  try {
    rapor.value = await konaklamaApi.rapor(secilen)
    uygulanan.value = secilen
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Rapor alınamadı.'
  } finally {
    mesgul.value = false
  }
}
onMounted(getir)

async function excelIndir() {
  hata.value = ''
  try {
    const { blob, ad } = await konaklamaApi.raporExcel(uygulanan.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = ad ?? `konaklama-raporu-${uygulanan.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    hata.value = e instanceof ApiHata ? e.detail : 'Excel indirilemedi.'
  }
}
</script>

<template>
  <section>
    <div class="sayfa-baslik"><h1>Konaklama raporu</h1></div>

    <form class="arac-cubugu" @submit.prevent="getir">
      <div class="alan">
        <label for="kr-ay">Ay</label>
        <input id="kr-ay" v-model="ay" type="month" required />
      </div>
      <button type="submit" class="btn btn-birincil" :disabled="mesgul">Raporu getir</button>
    </form>

    <p v-if="hata" class="bildirim bildirim-hata" role="alert">{{ hata }}</p>
    <p v-if="mesgul" class="soluk">Rapor hazırlanıyor…</p>

    <template v-if="rapor">
      <div class="kart ozet" data-test="ozet">
        <p>
          Toplam <strong>{{ rapor.toplamGece }}</strong> gece · kaydı eksik öneri
          <strong>{{ rapor.toplamEksikOneri }}</strong>
        </p>
        <p class="soluk">
          Üretim zamanı: {{ zamanBicimle(rapor.uretimZamani) }} · Üreten: {{ rapor.ureten }}. Gece =
          süre / 24 saat yukarı yuvarlanır (en az 1); konaklama giriş gününün ayına yazılır.
        </p>
        <div class="eylemler">
          <button type="button" class="btn" @click="excelIndir">Excel indir</button>
        </div>
      </div>

      <p v-if="rapor.makinistler.length === 0" class="soluk">Bu ay konaklama kaydı ve öneri yok.</p>
      <div v-else class="tablo-kap">
        <table class="tablo" aria-label="Aylık konaklama">
          <thead>
            <tr>
              <th>Personel</th>
              <th>Gar</th>
              <th class="sayisal">Konaklama</th>
              <th class="sayisal">Gece</th>
              <th class="sayisal">Kaydı eksik öneri</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="m in rapor.makinistler" :key="m.makinistId">
              <tr v-for="(g, i) in m.garlar" :key="`${m.makinistId}-${g.garId}`" data-test="satir">
                <td>
                  <template v-if="i === 0"
                    >{{ m.adSoyad }} <small class="soluk">{{ m.sicil }}</small></template
                  >
                </td>
                <td>{{ g.gar }}</td>
                <td class="sayisal">{{ g.konaklamaSayisi }}</td>
                <td class="sayisal">{{ g.gece }}</td>
                <td class="sayisal">
                  <template v-if="i === 0">{{ m.eksikOneri }}</template>
                </td>
              </tr>
              <tr v-if="m.garlar.length === 0" data-test="satir">
                <td>
                  {{ m.adSoyad }} <small class="soluk">{{ m.sicil }}</small>
                </td>
                <td class="soluk">—</td>
                <td class="sayisal">0</td>
                <td class="sayisal">0</td>
                <td class="sayisal">{{ m.eksikOneri }}</td>
              </tr>
              <tr v-if="m.garlar.length > 1" class="toplam" data-test="toplam">
                <td></td>
                <td><strong>Toplam</strong></td>
                <td class="sayisal">{{ m.konaklamaSayisi }}</td>
                <td class="sayisal">
                  <strong>{{ m.gece }}</strong>
                </td>
                <td></td>
              </tr>
            </template>
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
</style>
